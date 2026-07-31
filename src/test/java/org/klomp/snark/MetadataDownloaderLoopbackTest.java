package org.klomp.snark;

import org.junit.Test;
import static org.junit.Assert.*;

import org.klomp.snark.bencode.BDecoder;
import org.klomp.snark.bencode.BEncoder;
import org.klomp.snark.bencode.BEValue;
import org.klomp.snark.data.Base64;
import org.klomp.snark.data.Hash;
import org.klomp.snark.data.SHA256;
import org.klomp.snark.event.MetadataEvent;
import org.klomp.snark.event.MetadataListener;
import org.klomp.snark.spi.PeerIdentity;
import org.klomp.snark.spi.PeerIdentityFactory;
import org.klomp.snark.spi.Stream;
import org.klomp.snark.spi.StreamConnector;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 *  End-to-end loopback test: MetadataDownloader (client) against a
 *  fake in-memory peer implementing BEP 3 + BEP 10 + BEP 9 — with
 *  ZERO I2P code. Verifies the full protocol logic and the event
 *  stream (CONNECTING → ... → COMPLETE).
 */
public class MetadataDownloaderLoopbackTest {

    private static final int CHUNK = 16 * 1024;

    // ── Test identities ──────────────────────────────────────────────

    static class TestIdentity implements PeerIdentity {
        private final byte[] _data;
        private final Hash _hash;
        TestIdentity(byte[] data) {
            _data = data;
            _hash = Hash.create(SHA256.hash(data));
        }
        public byte[] getData() { return _data; }
        public Hash calculateHash() { return _hash; }
        public String toBase64() { return Base64.encode(_data); }
        public String toBase32() { return _hash.toBase64().toLowerCase().replace("=", "") + ".b32.i2p"; }
        @Override public boolean equals(Object o) {
            return o instanceof TestIdentity && java.util.Arrays.equals(_data, ((TestIdentity) o)._data);
        }
        @Override public int hashCode() { return _hash.hashCode(); }
        @Override public String toString() { return "TestIdentity[" + _hash.toBase64().substring(0, 8) + "...]"; }
    }

    static class TestIdentityFactory implements PeerIdentityFactory {
        public PeerIdentity fromBytes(byte[] data) { return new TestIdentity(data); }
        public PeerIdentity fromBase64(String s) { return new TestIdentity(Base64.decode(s)); }
    }

    // ── In-memory streams ────────────────────────────────────────────

    static class FakeStream implements Stream {
        private final InputStream _in;
        private final OutputStream _out;
        private volatile boolean _closed;

        FakeStream(InputStream in, OutputStream out) {
            _in = in;
            _out = out;
        }
        public InputStream getInputStream() { return _in; }
        public OutputStream getOutputStream() { return _out; }
        public void setReadTimeout(int timeoutMs) { /* no-op */ }
        public synchronized void close() {
            if (_closed) return;
            _closed = true;
            try { _in.close(); } catch (IOException ignored) {}
            try { _out.close(); } catch (IOException ignored) {}
        }
        public boolean isClosed() { return _closed; }
    }

    /** Pair of connected streams (client side + peer side). */
    static class StreamPair {
        final Stream client;
        final Stream peer;
        StreamPair() throws IOException {
            PipedInputStream clientIn = new PipedInputStream(64 * 1024);
            PipedOutputStream peerOut = new PipedOutputStream(clientIn);
            PipedInputStream peerIn = new PipedInputStream(64 * 1024);
            PipedOutputStream clientOut = new PipedOutputStream(peerIn);
            client = new FakeStream(clientIn, clientOut);
            peer = new FakeStream(peerIn, peerOut);
        }
    }

    // ── Fake BEP 9/10 peer ───────────────────────────────────────────

    /**
     *  Server side of the metadata exchange. Serves the given info dict.
     */
    static class FakePeer implements Runnable {
        private final PeerIdentity _identity;
        protected final byte[] _infohash;
        private final byte[] _infoDict;
        private volatile String _error;
        protected Stream _stream;

        FakePeer(PeerIdentity identity, byte[] infohash, byte[] infoDict) {
            _identity = identity;
            _infohash = infohash;
            _infoDict = infoDict;
        }

        public void run() {
            try {
                serve();
            } catch (Exception e) {
                _error = e.toString();
            }
        }

        protected void serve() throws Exception {
            // server streams are passed via connector; use thread-local
            // handshake: read 68 bytes, reply
            DataInputStream din = new DataInputStream(_stream.getInputStream());
            DataOutputStream dout = new DataOutputStream(_stream.getOutputStream());

            // read client handshake
            int p = din.readUnsignedByte();
            byte[] proto = new byte[19];
            din.readFully(proto);
            byte[] reserved = new byte[8];
            din.readFully(reserved);
            byte[] ih = new byte[20];
            din.readFully(ih);
            byte[] peerId = new byte[20];
            din.readFully(peerId);
            if (p != 19 || !java.util.Arrays.equals(proto, "BitTorrent protocol".getBytes("US-ASCII")))
                throw new IOException("bad handshake");
            if (!java.util.Arrays.equals(ih, _infohash))
                throw new IOException("infohash mismatch");

            // reply handshake with BEP 10 bit set
            byte[] reservedOut = new byte[8];
            reservedOut[5] = 0x10;
            dout.writeByte(19);
            dout.write("BitTorrent protocol".getBytes("US-ASCII"));
            dout.write(reservedOut);
            dout.write(_infohash);
            dout.write(new byte[20]); // our peer id
            dout.flush();

            // wait for extension handshake, reply with ut_metadata=1 and metadata_size
            int peerExtId = -1;
            boolean gotHandshake = false;
            while (!gotHandshake) {
                int len = din.readInt();
                if (len == 0) continue;
                int msgId = din.readUnsignedByte();
                if (msgId != 20) { din.skipBytes(len - 1); continue; }
                int extId = din.readUnsignedByte();
                byte[] payload = new byte[len - 2];
                din.readFully(payload);
                if (extId == 0) {
                    gotHandshake = true;
                }
            }
            peerExtId = 1; // our ut_metadata id
            Map<String, Object> m = new HashMap<String, Object>();
            m.put("ut_metadata", Integer.valueOf(1));
            Map<String, Object> hs = new HashMap<String, Object>();
            hs.put("m", m);
            hs.put("metadata_size", Integer.valueOf(_infoDict.length));
            sendExt(dout, 0, BEncoder.bencode(hs));

            // serve metadata chunks
            int totalChunks = (_infoDict.length + CHUNK - 1) / CHUNK;
            boolean[] sent = new boolean[totalChunks];
            int sentCount = 0;
            while (sentCount < totalChunks) {
                int len;
                try {
                    len = din.readInt();
                } catch (EOFException eof) {
                    break; // client hung up
                }
                if (len == 0) continue;
                int msgId = din.readUnsignedByte();
                if (msgId != 20) { din.skipBytes(len - 1); continue; }
                int extId = din.readUnsignedByte();
                byte[] payload = new byte[len - 2];
                din.readFully(payload);
                if (extId != peerExtId) continue;
                // BEP 9 request
                BEValue bev = new BDecoder(new ByteArrayInputStream(payload)).bdecodeMap();
                Map<String, BEValue> req = bev.getMap();
                int msgType = req.get("msg_type").getInt();
                int piece = req.get("piece").getInt();
                if (msgType != 0) continue;
                if (sent[piece]) continue;
                sent[piece] = true;
                sentCount++;

                int off = piece * CHUNK;
                int size = Math.min(CHUNK, _infoDict.length - off);
                byte[] chunk = new byte[size];
                System.arraycopy(_infoDict, off, chunk, 0, size);

                Map<String, Object> data = new HashMap<String, Object>();
                data.put("msg_type", Integer.valueOf(1));
                data.put("piece", Integer.valueOf(piece));
                byte[] dict = BEncoder.bencode(data);
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                baos.write(dict);
                baos.write(chunk);
                sendExt(dout, peerExtId, baos.toByteArray());
            }
            // keep connection open briefly so client can finish reading
            Thread.sleep(200);
            _stream.close();
        }

        private void sendExt(DataOutputStream dout, int extId, byte[] payload) throws IOException {
            dout.writeInt(1 + 1 + payload.length);
            dout.writeByte(20);
            dout.writeByte(extId);
            dout.write(payload);
            dout.flush();
        }

        void setStream(Stream s) { _stream = s; }
        String getError() { return _error; }
    }

    // ── Fake connector ───────────────────────────────────────────────

    static class FakeConnector implements StreamConnector {
        private final Map<PeerIdentity, FakePeer> _peers = new HashMap<PeerIdentity, FakePeer>();
        private final List<PeerIdentity> _order = new ArrayList<PeerIdentity>();
        private final PeerIdentity _local;
        private int _connectCount;
        /** identities for which connect() should fail */
        private final List<PeerIdentity> _blackhole = new ArrayList<PeerIdentity>();

        FakeConnector(PeerIdentity local) {
            _local = local;
        }

        void addPeer(PeerIdentity id, FakePeer peer) {
            _peers.put(id, peer);
            _order.add(id);
        }

        void failConnect(PeerIdentity id) {
            _blackhole.add(id);
        }

        public PeerIdentity getLocalIdentity() { return _local; }

        public PeerIdentity lookup(byte[] sha256Hash, long timeoutMs) {
            for (PeerIdentity id : _order) {
                if (java.util.Arrays.equals(id.calculateHash().getData(), sha256Hash))
                    return id;
            }
            return null;
        }

        public boolean isClosed() { return false; }

        public synchronized Stream connect(PeerIdentity dest) throws IOException {
            _connectCount++;
            if (_blackhole.contains(dest))
                throw new IOException("connect refused (blackholed)");
            FakePeer peer = _peers.get(dest);
            if (peer == null)
                throw new IOException("unknown peer");
            StreamPair pair = new StreamPair();
            peer.setStream(pair.peer);
            Thread t = new Thread(peer, "fake-peer-" + _connectCount);
            t.setDaemon(true);
            t.start();
            return pair.client;
        }

        int getConnectCount() { return _connectCount; }
    }

    // ── Test helpers ─────────────────────────────────────────────────

    private static byte[] buildBigInfoDict() throws Exception {
        Map<String, Object> info = new HashMap<String, Object>();
        info.put("name", "loopback-test.bin");
        info.put("piece length", Integer.valueOf(16384));
        info.put("length", Long.valueOf(50000));
        byte[] pieces = new byte[80]; // 4 pieces x 20
        for (int i = 0; i < pieces.length; i++)
            pieces[i] = (byte) i;
        info.put("pieces", pieces);
        // pad to ~40KB so we get multiple metadata chunks
        StringBuilder sb = new StringBuilder(40000);
        for (int i = 0; i < 40000; i++)
            sb.append((char) ('a' + (i % 26)));
        info.put("comment", sb.toString());
        return BEncoder.bencode(info);
    }

    /** Event recorder implementing MetadataListener. */
    static class Recorder implements MetadataListener {
        final CopyOnWriteArrayList<MetadataEvent> events = new CopyOnWriteArrayList<MetadataEvent>();
        public void onMetadataEvent(MetadataEvent event) {
            events.add(event);
        }
        List<MetadataEvent.Phase> phases() {
            List<MetadataEvent.Phase> rv = new ArrayList<MetadataEvent.Phase>();
            for (MetadataEvent e : events)
                rv.add(e.getPhase());
            return rv;
        }
    }

    private static byte[] sha1(byte[] data) throws Exception {
        return java.security.MessageDigest.getInstance("SHA-1").digest(data);
    }

    // ── Tests ────────────────────────────────────────────────────────

    @Test
    public void testDownloadFromSinglePeer() throws Exception {
        byte[] infoDict = buildBigInfoDict();
        byte[] infohash = sha1(infoDict);

        TestIdentity local = new TestIdentity(new byte[387]); // dest-sized
        TestIdentity peer = new TestIdentity(new byte[387]);
        peer.getData()[0] = 1;

        FakeConnector connector = new FakeConnector(local);
        FakePeer fakePeer = new FakePeer(peer, infohash, infoDict);
        connector.addPeer(peer, fakePeer);
        Recorder recorder = new Recorder();

        MetaInfo meta = MetadataDownloader.download(connector, peer, infohash, 5000, 15000, recorder);

        assertNotNull(meta);
        assertEquals("loopback-test.bin", meta.getName());
        assertEquals(50000L, meta.getTotalLength());
        assertArrayEquals(infohash, meta.getInfoHash());
        assertNull("peer should not have errored: " + fakePeer.getError(), fakePeer.getError());

        // event stream sanity
        List<MetadataEvent.Phase> phases = recorder.phases();
        assertTrue("expected CONNECTING, got " + phases, phases.contains(MetadataEvent.Phase.CONNECTING));
        assertTrue(phases.contains(MetadataEvent.Phase.CONNECTED));
        assertTrue(phases.contains(MetadataEvent.Phase.HANDSHAKE));
        assertTrue(phases.contains(MetadataEvent.Phase.EXTENSION_HANDSHAKE));
        assertTrue(phases.contains(MetadataEvent.Phase.DOWNLOADING));
        assertTrue(phases.contains(MetadataEvent.Phase.VERIFYING));
        assertEquals(MetadataEvent.Phase.COMPLETE, phases.get(phases.size() - 1));

        // progress reached 100%
        MetadataEvent last = recorder.events.get(recorder.events.size() - 1);
        assertEquals(100, last.getPercent());
        assertNotNull(last.getMetaInfo());
        assertEquals(infoDict.length, last.getMetadataSize());
        assertTrue("expected multiple chunks, got " + last.getChunksTotal(), last.getChunksTotal() > 2);
    }

    @Test
    public void testDownloadFromPeersSkipsBadPeer() throws Exception {
        byte[] infoDict = buildBigInfoDict();
        byte[] infohash = sha1(infoDict);

        TestIdentity local = new TestIdentity(new byte[387]);
        TestIdentity badPeer = new TestIdentity(new byte[387]);
        badPeer.getData()[0] = 1;
        TestIdentity goodPeer = new TestIdentity(new byte[387]);
        goodPeer.getData()[0] = 2;

        FakeConnector connector = new FakeConnector(local);
        connector.addPeer(badPeer, new FakePeer(badPeer, infohash, infoDict));
        connector.addPeer(goodPeer, new FakePeer(goodPeer, infohash, infoDict));
        connector.failConnect(badPeer);
        Recorder recorder = new Recorder();

        List<PeerIdentity> peers = new ArrayList<PeerIdentity>();
        peers.add(badPeer);
        peers.add(goodPeer);

        MetaInfo meta = MetadataDownloader.downloadFromPeers(connector, peers, infohash, 5000, 15000, recorder);

        assertNotNull(meta);
        assertEquals(50000L, meta.getTotalLength());
        assertArrayEquals(infohash, meta.getInfoHash());

        List<MetadataEvent.Phase> phases = recorder.phases();
        // one failed attempt, then success
        int firstFail = phases.indexOf(MetadataEvent.Phase.PEER_FAILED);
        assertTrue("expected a PEER_FAILED, got " + phases, firstFail >= 0);
        int complete = phases.indexOf(MetadataEvent.Phase.COMPLETE);
        assertTrue(complete > firstFail);
    }

    @Test
    public void testAllPeersFail() throws Exception {
        byte[] infoDict = buildBigInfoDict();
        byte[] infohash = sha1(infoDict);

        TestIdentity local = new TestIdentity(new byte[387]);
        TestIdentity badPeer = new TestIdentity(new byte[387]);
        FakeConnector connector = new FakeConnector(local);
        connector.addPeer(badPeer, new FakePeer(badPeer, infohash, infoDict));
        connector.failConnect(badPeer);
        Recorder recorder = new Recorder();

        List<PeerIdentity> peers = new ArrayList<PeerIdentity>();
        peers.add(badPeer);

        try {
            MetadataDownloader.downloadFromPeers(connector, peers, infohash, 5000, 15000, recorder);
            fail("expected IOException");
        } catch (IOException expected) {
            // good
        }
        List<MetadataEvent.Phase> phases = recorder.phases();
        assertTrue(phases.contains(MetadataEvent.Phase.PEER_FAILED));
        assertEquals(MetadataEvent.Phase.FAILED, phases.get(phases.size() - 1));
    }

    @Test
    public void testBadPeerHandshakeFailsFast() throws Exception {
        byte[] infoDict = buildBigInfoDict();
        byte[] infohash = sha1(infoDict);

        TestIdentity local = new TestIdentity(new byte[387]);
        TestIdentity peer = new TestIdentity(new byte[387]);
        // a peer that does NOT support BEP 10
        FakePeer bad = new FakePeer(peer, infohash, infoDict) {
            @Override
            protected void serve() throws Exception {
                DataInputStream din = new DataInputStream(_stream.getInputStream());
                DataOutputStream dout = new DataOutputStream(_stream.getOutputStream());
                // consume client handshake
                din.readUnsignedByte();
                din.skipBytes(19 + 8 + 20 + 20);
                // reply WITHOUT the extension bit
                dout.writeByte(19);
                dout.write("BitTorrent protocol".getBytes("US-ASCII"));
                dout.write(new byte[8]); // no BEP 10
                dout.write(_infohash);
                dout.write(new byte[20]);
                dout.flush();
                Thread.sleep(500);
                _stream.close();
            }
        };
        FakeConnector connector = new FakeConnector(local);
        connector.addPeer(peer, bad);

        try {
            MetadataDownloader.download(connector, peer, infohash, 5000, 15000, null);
            fail("expected IOException for non-BEP10 peer");
        } catch (IOException e) {
            assertTrue("unexpected message: " + e.getMessage(),
                    e.getMessage().contains("extension protocol") || e.getMessage().contains("Handshake"));
        }
    }
}
