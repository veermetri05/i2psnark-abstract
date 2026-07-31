package org.klomp.snark;

import org.junit.Test;
import static org.junit.Assert.*;

import org.klomp.snark.bencode.BEncoder;
import org.klomp.snark.data.Base64;
import org.klomp.snark.data.DataHelper;
import org.klomp.snark.data.Hash;
import org.klomp.snark.data.SHA1;
import org.klomp.snark.event.SimpleEventBus;
import org.klomp.snark.spi.Environment;
import org.klomp.snark.spi.PeerIdentity;
import org.klomp.snark.spi.PeerIdentityFactory;
import org.klomp.snark.spi.Stream;
import org.klomp.snark.spi.StreamConnector;
import org.klomp.snark.spi.StreamServer;
import org.klomp.snark.util.FileStorage;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 *  Loopback swarming test (WS-1.5 / milestone M0): a real engine
 *  (ClientContext + Snark + PeerCoordinator + Storage) downloads from
 *  and uploads to fake peers over in-memory streams. No I2P needed.
 */
public class FullClientLoopbackTest {

    private static final int PIECE_LEN = 16 * 1024;

    // ── In-memory SPI transport ──────────────────────────────────────

    /** Identity: a 32-byte hash (like an I2P destination hash). */
    static class LoopIdentity implements PeerIdentity {
        final byte[] _hash;
        LoopIdentity(byte[] hash) { _hash = hash; }
        @Override public byte[] getData() { return _hash; }
        @Override public Hash calculateHash() { return Hash.create(_hash); }
        @Override public String toBase64() { return Base64.encode(_hash); }
        @Override public String toBase32() { return org.klomp.snark.data.Base32.encode(_hash) + ".b32.i2p"; }
        @Override public boolean equals(Object o) {
            return o instanceof LoopIdentity && DataHelper.eq(_hash, ((LoopIdentity) o)._hash);
        }
        @Override public int hashCode() { return DataHelper.hashCode(_hash); }
    }

    /** Bidirectional stream over two pipe pairs. */
    static class LoopStream implements Stream {
        final PipedInputStream _in;
        final PipedOutputStream _out;
        volatile boolean _closed;
        volatile PeerIdentity _peer;
        LoopStream(PipedInputStream in, PipedOutputStream out) { _in = in; _out = out; }
        void setPeer(PeerIdentity peer) { _peer = peer; }
        @Override public PeerIdentity getPeer() { return _peer; }
        @Override public InputStream getInputStream() { return _in; }
        @Override public OutputStream getOutputStream() { return _out; }
        @Override public void setReadTimeout(int timeoutMs) { /* pipes block; no-op */ }
        @Override public int getReadTimeout() { return 0; }
        @Override public void close() {
            _closed = true;
            try { _out.close(); } catch (IOException ignored) {}
            try { _in.close(); } catch (IOException ignored) {}
        }
        @Override public boolean isClosed() { return _closed; }
    }

    /** Create a connected pair of streams: A's output feeds B's input and vice versa. */
    static Stream[] pair() throws IOException {
        PipedOutputStream aOut = new PipedOutputStream(); // A writes
        PipedInputStream bIn = new PipedInputStream(64 * 1024); // B reads
        aOut.connect(bIn);
        PipedOutputStream bOut = new PipedOutputStream(); // B writes
        PipedInputStream aIn = new PipedInputStream(64 * 1024); // A reads
        bOut.connect(aIn);
        return new Stream[]{new LoopStream(aIn, aOut), new LoopStream(bIn, bOut)};
    }

    /**
     *  The loopback transport: one local identity, a map of known
     *  remote identities (seeders), and a server queue for inbound
     *  connections to the local engine.
     */
    static class LoopTransport implements StreamConnector, StreamServer, PeerIdentityFactory {
        final LoopIdentity _local;
        final Map<Hash, LoopIdentity> _known = new ConcurrentHashMap<Hash, LoopIdentity>();
        final LinkedBlockingQueue<Stream> _inbound = new LinkedBlockingQueue<Stream>();
        volatile java.util.function.Consumer<Stream> _seederHandler;
        volatile boolean _closed;
        /** identity of the local caller for self-connections (fake leecher) */
        volatile PeerIdentity _callerIdentity;


        LoopTransport(byte[] localHash) {
            _local = new LoopIdentity(localHash);
        }

        // StreamConnector
        @Override public PeerIdentity getLocalIdentity() { return _local; }
        @Override public PeerIdentity lookup(byte[] sha256Hash, long timeoutMs) {
            if (DataHelper.eq(sha256Hash, _local._hash))
                return _local;
            return _known.get(Hash.create(sha256Hash));
        }
        @Override public boolean isClosed() { return _closed; }

        @Override
        public Stream connect(PeerIdentity dest) throws IOException {
            if (dest.equals(_local)) {
                // connection to ourselves → route into the server queue
                Stream[] pair = pair();
                PeerIdentity caller = _callerIdentity != null ? _callerIdentity : _local;
                ((LoopStream) pair[0]).setPeer(_local);   // caller (leecher) sees the engine
                ((LoopStream) pair[1]).setPeer(caller);   // engine's acceptor sees the caller
                _inbound.offer(pair[1]);
                return pair[0];
            }
            LoopIdentity id = _known.get(dest.calculateHash());
            if (id == null)
                throw new IOException("unknown destination " + dest);
            Stream[] pair = pair();
            ((LoopStream) pair[0]).setPeer(id);           // engine sees the seeder
            final Stream peerEnd = pair[1];
            Thread t = new Thread(() -> {
                try {
                    _seederHandler.accept(peerEnd);
                } catch (Exception e) {
                    peerEnd.close();
                }
            }, "loop-seeder");
            t.setDaemon(true);
            t.start();
            return pair[0];
        }

        // StreamServer
        @Override
        public Stream accept() throws IOException {
            while (!_closed) {
                try {
                    Stream s = _inbound.poll(500, TimeUnit.MILLISECONDS);
                    if (s != null)
                        return s;
                } catch (InterruptedException ie) {
                    throw new IOException("interrupted");
                }
            }
            throw new IOException("server closed");
        }
        @Override public void close() { _closed = true; }

        // PeerIdentityFactory
        @Override public PeerIdentity fromBytes(byte[] data) {
            if (data.length != 32)
                throw new IllegalArgumentException("bad length");
            return new LoopIdentity(data);
        }
        @Override public PeerIdentity fromBase64(String base64) {
            byte[] b = Base64.decode(base64);
            if (b == null || b.length != 32)
                throw new IllegalArgumentException("bad base64");
            return new LoopIdentity(b);
        }
    }

    // ── Fake seeder: a full wire-protocol server ─────────────────────

    static class FakeSeeder {
        final byte[] _infohash;
        final byte[] _peerId;
        final byte[][] _pieces;   // piece data by index
        final AtomicInteger _requestsServed = new AtomicInteger();

        volatile byte[] _receivedInfohash;

        FakeSeeder(byte[] infohash, byte[][] pieces) {
            _infohash = infohash;
            _peerId = new byte[20];
            for (int i = 0; i < 20; i++)
                _peerId[i] = (byte) ('S' + i);
            _pieces = pieces;
        }

        void handle(Stream s) {
            try {
                DataInputStream din = new DataInputStream(s.getInputStream());
                DataOutputStream dout = new DataOutputStream(s.getOutputStream());
                // handshake in
                if (din.readUnsignedByte() != 19)
                    throw new IOException("bad pstrlen");
                byte[] pstr = new byte[19];
                din.readFully(pstr);
                byte[] reserved = new byte[8];
                din.readFully(reserved);
                byte[] ih = new byte[20];
                din.readFully(ih);
                byte[] pid = new byte[20];
                din.readFully(pid);
                _receivedInfohash = ih;
                if (!DataHelper.eq(ih, _infohash)) {
                    s.close();
                    return;
                }
                // handshake out
                dout.writeByte(19);
                dout.write(DataHelper.getASCII("BitTorrent protocol"));
                dout.write(new byte[8]);
                dout.write(ih);
                dout.write(_peerId);
                // bitfield (all set)
                int bfLen = (_pieces.length + 7) / 8;
                byte[] bf = new byte[bfLen];
                for (int i = 0; i < _pieces.length; i++)
                    bf[i / 8] |= (byte) (0x80 >> (i % 8));
                dout.writeInt(1 + bfLen);
                dout.writeByte(Message.BITFIELD);
                dout.write(bf);
                // unchoke
                dout.writeInt(1);
                dout.writeByte(Message.UNCHOKE);
                dout.flush();

                // message loop
                byte[] lenBuf = new byte[4];
                while (true) {
                    din.readFully(lenBuf);
                    int len = (int) DataHelper.fromLong(lenBuf, 0, 4);
                    if (len == 0)
                        continue; // keep-alive
                    byte[] msg = new byte[len];
                    din.readFully(msg);
                    switch (msg[0]) {
                        case Message.REQUEST: {
                            int piece = (int) DataHelper.fromLong(msg, 1, 4);
                            int begin = (int) DataHelper.fromLong(msg, 5, 4);
                            int length = (int) DataHelper.fromLong(msg, 9, 4);
                            byte[] data = _pieces[piece];
                            final int off = begin;
                            Message reply = new Message(piece, begin, length, (p, b, l) -> {
                                _requestsServed.incrementAndGet();
                                return new org.klomp.snark.data.ByteArray(
                                        java.util.Arrays.copyOfRange(data, off, off + l));
                            });
                            reply.sendMessage(dout);
                            dout.flush();
                            break;
                        }
                        case Message.CANCEL:
                        case Message.INTERESTED:
                        case Message.UNINTERESTED:
                        case Message.BITFIELD:
                        case Message.EXTENSION:
                        case Message.HAVE:
                        case Message.KEEP_ALIVE:
                        default:
                            // ignore
                            break;
                    }
                }
            } catch (EOFException eof) {
                // peer disconnected — normal
            } catch (Exception e) {
                // peer error — normal for a test server
            } finally {
                s.close();
            }
        }
    }

    // ── Listener adapter ─────────────────────────────────────────────

    static class ListenerAdapter implements CompleteListener, CoordinatorListener, StorageListener {
        final CountDownLatch completed = new CountDownLatch(1);
        final CountDownLatch failed = new CountDownLatch(1);
        volatile String failure;
        final AtomicInteger pieces = new AtomicInteger();

        // CompleteListener
        @Override public void torrentComplete(Snark snark) { completed.countDown(); }
        @Override public void updateStatus(Snark snark) {}
        @Override public String gotMetaInfo(Snark snark) { return null; }
        @Override public void fatal(Snark snark, String error) { failure = error; failed.countDown(); }
        @Override public void addMessage(Snark snark, String message) {}
        @Override public void addMessage(String message) {}
        @Override public void gotPiece(Snark snark) { pieces.incrementAndGet(); }
        @Override public long getSavedTorrentTime(Snark snark) { return 0; }
        @Override public BitField getSavedTorrentBitField(Snark snark) { return null; }
        @Override public boolean getSavedPreserveNamesSetting(Snark snark) { return false; }
        @Override public long getSavedUploaded(Snark snark) { return 0; }
        @Override public boolean shouldAutoStart() { return false; }
        @Override public BandwidthListener getBandwidthListener() { return noopBwl(); }

        // CoordinatorListener
        @Override public void peerChange(PeerCoordinator coordinator, Peer peer) {}
        @Override public void gotMetaInfo(PeerCoordinator coordinator, MetaInfo metainfo) {}
        @Override public boolean overUploadLimit(int uploaders) { return false; }

        // StorageListener
        @Override public void storageCreateFile(Storage storage, String name, long length) {}
        @Override public void storageAllocated(Storage storage, long length) {}
        @Override public void storageChecked(Storage storage, int num, boolean checked) {}
        @Override public void storageAllChecked(Storage storage) {}
        @Override public void storageCompleted(Storage storage) {}
        @Override public void setWantedPieces(Storage storage) {}
    }

    static BandwidthListener noopBwl() {
        return new BandwidthListener() {
            @Override public long getUploadRate() { return 0; }
            @Override public long getDownloadRate() { return 0; }
            @Override public void uploaded(int size) {}
            @Override public void downloaded(int size) {}
            @Override public boolean shouldSend(int size) { return true; }
            @Override public boolean shouldRequest(Peer peer, int size) { return true; }
            @Override public long getUpBWLimit() { return 0; }
            @Override public long getDownBWLimit() { return 0; }
            @Override public boolean overUpBWLimit() { return false; }
            @Override public boolean overDownBWLimit() { return false; }
        };
    }

    // ── helpers ──────────────────────────────────────────────────────

    /** Deterministic piece data. */
    static byte[] pieceData(int n) {
        byte[] data = new byte[PIECE_LEN];
        for (int i = 0; i < data.length; i++)
            data[i] = (byte) ((n * 31 + i) % 251);
        return data;
    }

    /** Multi-file torrent: dir/a.bin (2 pieces) + dir/b.bin (1 piece). */
    static byte[] buildMultiFileTorrent(byte[][] pieces) throws Exception {
        List<Object> files = new ArrayList<Object>();
        files.add(filesEntry("dir/a.bin", 2L * PIECE_LEN));
        files.add(filesEntry("dir/b.bin", (long) PIECE_LEN));
        Map<String, Object> info = new HashMap<String, Object>();
        info.put("name", "multi");
        info.put("piece length", Integer.valueOf(PIECE_LEN));
        info.put("files", files);
        byte[] hashes = new byte[pieces.length * 20];
        for (int i = 0; i < pieces.length; i++)
            System.arraycopy(SHA1.hash(pieces[i]), 0, hashes, i * 20, 20);
        info.put("pieces", hashes);
        Map<String, Object> top = new HashMap<String, Object>();
        // an announce URL keeps TrackerClient alive: with no trackers AND no
        // DHT, upstream stops the torrent ("No valid trackers..."). The host
        // must be a b32 name (TrackerClient validates).
        byte[] trackerKey = new byte[32];
        for (int i = 0; i < 32; i++)
            trackerKey[i] = (byte) (150 + i);
        top.put("announce", "http://" + org.klomp.snark.data.Base32.encode(trackerKey) + ".b32.i2p/announce");
        top.put("info", info);
        return BEncoder.bencode(top);
    }

    /** The torrent infohash = SHA-1 of the bencoded info dict alone. */
    static byte[] infohashOf(byte[][] pieces, byte[] torrentBytes) throws Exception {
        // rebuild the info map exactly as buildMultiFileTorrent does, then hash
        List<Object> files = new ArrayList<Object>();
        files.add(filesEntry("dir/a.bin", 2L * PIECE_LEN));
        files.add(filesEntry("dir/b.bin", (long) PIECE_LEN));
        Map<String, Object> info = new HashMap<String, Object>();
        info.put("name", "multi");
        info.put("piece length", Integer.valueOf(PIECE_LEN));
        info.put("files", files);
        byte[] hashes = new byte[pieces.length * 20];
        for (int i = 0; i < pieces.length; i++)
            System.arraycopy(SHA1.hash(pieces[i]), 0, hashes, i * 20, 20);
        info.put("pieces", hashes);
        return SHA1.hash(BEncoder.bencode(info));
    }

    static Map<String, Object> filesEntry(String path, long length) {
        Map<String, Object> e = new HashMap<String, Object>();
        e.put("length", Long.valueOf(length));
        List<Object> pathParts = new ArrayList<Object>();
        for (String p : path.split("/"))
            pathParts.add(p);
        e.put("path", pathParts);
        return e;
    }

    /** Write the expected multi-file content under rootDir. */
    static void writeExpectedFiles(File rootDir, byte[][] pieces) throws Exception {
        File dir = new File(rootDir, "multi/dir");
        assertTrue(dir.mkdirs());
        java.nio.file.Files.write(new File(dir, "a.bin").toPath(), concat(pieces[0], pieces[1]));
        java.nio.file.Files.write(new File(dir, "b.bin").toPath(), pieces[2]);
    }

    static byte[] concat(byte[] a, byte[] b) {
        byte[] rv = new byte[a.length + b.length];
        System.arraycopy(a, 0, rv, 0, a.length);
        System.arraycopy(b, 0, rv, a.length, b.length);
        return rv;
    }

    static ClientContext makeContext(LoopTransport transport, File workDir) {
        Environment env = Environment.basic(new FileStorage(new File(workDir, "config")));
        File tempDir = new File(workDir, "tmp");
        assertTrue(tempDir.mkdirs());
        return new ClientContext(env, transport, transport, transport,
                new SimpleEventBus("loopback"), tempDir);
    }

    static void deleteRecursive(File f) {
        File[] kids = f.listFiles();
        if (kids != null)
            for (File k : kids)
                deleteRecursive(k);
        f.delete();
    }

    // ── Tests ────────────────────────────────────────────────────────

    /** The M0 test: full BEP 3 download of a multi-file torrent. */
    @Test
    public void downloadMultiFileFromFakeSeeder() throws Exception {
        File work = Files.createTempDirectory("loop-dl").toFile();
        try {
            byte[][] pieces = {pieceData(0), pieceData(1), pieceData(2)};
            byte[] torrentBytes = buildMultiFileTorrent(pieces);
            File torrentFile = new File(work, "multi.torrent");
            Files.write(torrentFile.toPath(), torrentBytes);
            byte[] infohash = infohashOf(pieces, torrentBytes);

            // seeder identity + transport
            byte[] seederHash = new byte[32];
            for (int i = 0; i < 32; i++)
                seederHash[i] = (byte) (200 + i);
            LoopTransport transport = new LoopTransport(new byte[32]);
            LoopIdentity seeder = new LoopIdentity(seederHash);
            transport._known.put(Hash.create(seederHash), seeder);
            FakeSeeder seederServer = new FakeSeeder(infohash, pieces);
            final List<Throwable> seederErrors = new ArrayList<Throwable>();
            transport._seederHandler = stream -> {
                try {
                    seederServer.handle(stream);
                } catch (Throwable t) {
                    seederErrors.add(t);
                }
            };

            File dataDir = new File(work, "data");
            dataDir.mkdirs();
            ClientContext ctx = makeContext(transport, work);
            ListenerAdapter listener = new ListenerAdapter();
            Snark snark = new Snark(ctx, torrentFile.getAbsolutePath(), null, -1,
                    null, listener, listener, null, null, dataDir.getAbsolutePath());
            snark.startTorrent();

            // inject the seeder as a known peer
            PeerCoordinator coord = snark.getCoordinator();
            assertNotNull("coordinator should exist after start", coord);
            PeerID seederPid = new PeerID(seederHash, ctx);
            boolean added = coord.addPeer(new Peer(seederPid, snark.getID(), snark.getInfoHash(), snark.getMetaInfo()));
            if (!added) {
                fail("addPeer rejected: halted=" + coord.halted()
                        + " peers=" + coord.peerList().size()
                       );
            }

            boolean ok = listener.completed.await(60, TimeUnit.SECONDS);
            if (!ok) {
                StringBuilder sb = new StringBuilder("download did not complete");
                if (listener.failure != null)
                    sb.append("; failure=").append(listener.failure);
                if (!seederErrors.isEmpty())
                    sb.append("; seederErrors=").append(seederErrors);
                sb.append("; requestsServed=").append(seederServer._requestsServed.get());
                sb.append("; engineIh=").append(org.klomp.snark.data.Base32.encode(snark.getInfoHash()));
                sb.append("; seederIh=").append(org.klomp.snark.data.Base32.encode(infohash));
                sb.append("; seederGotIh=").append(seederServer._receivedInfohash != null
                        ? org.klomp.snark.data.Base32.encode(seederServer._receivedInfohash) : "null");
                fail(sb.toString());
            }
            assertTrue("storage should be complete", snark.getStorage().getBitField().complete());
            assertTrue("seeder should have served requests", seederServer._requestsServed.get() > 0);

            // verify the actual files on disk
            File dir = new File(work, "data/multi/dir");
            byte[] a = Files.readAllBytes(new File(dir, "a.bin").toPath());
            byte[] b = Files.readAllBytes(new File(dir, "b.bin").toPath());
            assertArrayEquals(concat(pieces[0], pieces[1]), a);
            assertArrayEquals(pieces[2], b);

            snark.stopTorrent();
        } finally {
            deleteRecursive(work);
        }
    }

    /** Recheck on restart: a new Snark over the same data dir finds all pieces. */
    @Test
    public void recheckAfterRestart() throws Exception {
        File work = Files.createTempDirectory("loop-recheck").toFile();
        try {
            byte[][] pieces = {pieceData(0), pieceData(1), pieceData(2)};
            byte[] torrentBytes = buildMultiFileTorrent(pieces);
            File torrentFile = new File(work, "multi.torrent");
            Files.write(torrentFile.toPath(), torrentBytes);

            // pre-seed the data directory with correct content
            writeExpectedFiles(new File(work, "data"), pieces);

            LoopTransport transport = new LoopTransport(new byte[32]);
            File dataDir = new File(work, "data");
            dataDir.mkdirs();
            ClientContext ctx = makeContext(transport, work);
            ListenerAdapter listener = new ListenerAdapter();
            Snark snark = new Snark(ctx, torrentFile.getAbsolutePath(), null, -1,
                    null, listener, listener, null, null, dataDir.getAbsolutePath());
            snark.startTorrent();

            // no peers at all — the storage check must find everything
            assertTrue(snark.getStorage().getBitField().complete());
            assertTrue(listener.completed.await(30, TimeUnit.SECONDS));
            snark.stopTorrent();
        } finally {
            deleteRecursive(work);
        }
    }

    /** Upload: the engine (complete) serves a fake leecher over the inbound path. */
    @Test
    public void uploadToFakeLeecher() throws Exception {
        File work = Files.createTempDirectory("loop-up").toFile();
        try {
            byte[][] pieces = {pieceData(0), pieceData(1), pieceData(2)};
            byte[] torrentBytes = buildMultiFileTorrent(pieces);
            File torrentFile = new File(work, "multi.torrent");
            Files.write(torrentFile.toPath(), torrentBytes);
            writeExpectedFiles(new File(work, "data"), pieces);

            LoopTransport transport = new LoopTransport(new byte[32]);
            File dataDir = new File(work, "data");
            dataDir.mkdirs();
            ClientContext ctx = makeContext(transport, work);
            ListenerAdapter listener = new ListenerAdapter();
            Snark snark = new Snark(ctx, torrentFile.getAbsolutePath(), null, -1,
                    null, listener, listener, null, null, dataDir.getAbsolutePath());
            snark.startTorrent();
            assertTrue(snark.getStorage().getBitField().complete());

            // leecher connects inbound (to our own identity)
            byte[] infohash = infohashOf(pieces, torrentBytes);
            byte[] leecherHash = new byte[32];
            for (int i = 0; i < 32; i++)
                leecherHash[i] = (byte) (100 + i);
            transport._callerIdentity = new LoopIdentity(leecherHash);
            Stream s = transport.connect(transport._local);
            FakeLeecher leecher = new FakeLeecher(s, infohash, pieces);
            assertTrue("upload should complete" + (leecher._error != null ? ": " + leecher._error : ""),
                    leecher.await(60, TimeUnit.SECONDS));
            assertArrayEquals("piece 0 data", pieces[0], leecher._received[0]);
            assertArrayEquals("piece 1 data", pieces[1], leecher._received[1]);

            snark.stopTorrent();
        } finally {
            deleteRecursive(work);
        }
    }

    /** Fake leecher: handshake, interested, wait unchoke, request everything. */
    static class FakeLeecher {
        final byte[][] _received;
        final CountDownLatch _done = new CountDownLatch(1);
        volatile String _error;

        FakeLeecher(Stream s, byte[] infohash, byte[][] pieces) {
            _received = new byte[pieces.length][];
            Thread t = new Thread(() -> run(s, infohash, pieces), "loop-leecher");
            t.setDaemon(true);
            t.start();
        }

        boolean await(long time, TimeUnit unit) throws InterruptedException {
            boolean ok = _done.await(time, unit);
            if (!ok)
                _error = "timeout";
            return ok;
        }

        void run(Stream s, byte[] infohash, byte[][] pieces) {
            try {
                DataInputStream din = new DataInputStream(s.getInputStream());
                DataOutputStream dout = new DataOutputStream(s.getOutputStream());
                // handshake
                dout.writeByte(19);
                dout.write(DataHelper.getASCII("BitTorrent protocol"));
                dout.write(new byte[8]);
                dout.write(infohash);
                byte[] myId = new byte[20];
                for (int i = 0; i < 20; i++)
                    myId[i] = (byte) ('L' + i);
                dout.write(myId);
                dout.flush();
                // read engine handshake
                if (din.readUnsignedByte() != 19)
                    throw new IOException("bad pstrlen");
                byte[] pstr = new byte[19];
                din.readFully(pstr);
                byte[] reserved = new byte[8];
                din.readFully(reserved);
                byte[] ih = new byte[20];
                din.readFully(ih);
                byte[] pid = new byte[20];
                din.readFully(pid);
                assertEquals(infohash.length, ih.length);
                // interested
                dout.writeInt(1);
                dout.writeByte(Message.INTERESTED);
                dout.flush();

                // wait for unchoke, skipping other messages
                boolean unchoked = false;
                long deadline = System.currentTimeMillis() + 60_000;
                while (!unchoked && System.currentTimeMillis() < deadline) {
                    byte[] lenBuf = new byte[4];
                    din.readFully(lenBuf);
                    int len = (int) DataHelper.fromLong(lenBuf, 0, 4);
                    if (len == 0)
                        continue;
                    byte[] msg = new byte[len];
                    din.readFully(msg);
                    switch (msg[0]) {
                        case Message.UNCHOKE:
                            unchoked = true;
                            break;
                        case Message.BITFIELD:
                        case Message.HAVE:
                        case Message.INTERESTED:
                        case Message.UNINTERESTED:
                        case Message.CHOKE:
                        case Message.EXTENSION:
                        default:
                            break;
                    }
                }
                if (!unchoked)
                    throw new IOException("never unchoked");

                // request all pieces (one 16K request each)
                for (int p = 0; p < pieces.length; p++) {
                    dout.writeInt(13);
                    dout.writeByte(Message.REQUEST);
                    dout.writeInt(p);
                    dout.writeInt(0);
                    dout.writeInt(pieces[p].length);
                }
                dout.flush();

                // read PIECE messages until every piece arrived
                byte[] lenBuf = new byte[4];
                while (_done.getCount() > 0) {
                    din.readFully(lenBuf);
                    int len = (int) DataHelper.fromLong(lenBuf, 0, 4);
                    if (len == 0)
                        continue; // keep-alive
                    byte[] msg = new byte[len];
                    din.readFully(msg);
                    if (msg[0] == Message.PIECE) {
                        int piece = (int) DataHelper.fromLong(msg, 1, 4);
                        int begin = (int) DataHelper.fromLong(msg, 5, 4);
                        byte[] data = java.util.Arrays.copyOfRange(msg, 9, len);
                        byte[] cur = _received[piece];
                        if (cur == null)
                            cur = new byte[pieces[piece].length];
                        System.arraycopy(data, 0, cur, begin, data.length);
                        _received[piece] = cur;
                        boolean allDone = true;
                        for (byte[] r : _received)
                            if (r == null) {
                                allDone = false;
                                break;
                            }
                        if (allDone) {
                            _done.countDown();
                            return;
                        }
                    }
                }
            } catch (Exception e) {
                _error = e.toString();
                _done.countDown();
            }
        }
    }
}
