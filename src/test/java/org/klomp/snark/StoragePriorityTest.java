package org.klomp.snark;

import org.junit.Test;
import static org.junit.Assert.*;

import org.klomp.snark.data.Hash;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 *  Priority + per-file received-byte tests for {@link Storage}
 *  (files-tab fix, A1/A2/A3).
 *
 *  Storage is built directly from a MetaInfo and a saved bitfield
 *  (files pre-written with old mtimes), so no hashing is needed and
 *  partial bitfield states can be simulated exactly.
 */
public class StoragePriorityTest {

    private static final int PIECE_LEN = 16 * 1024;

    /** No-op listener; the saved-bitfield path never fires storage events. */
    static final StorageListener LISTENER = new StorageListener() {
        @Override public void storageCreateFile(Storage storage, String name, long length) {}
        @Override public void storageAllocated(Storage storage, long length) {}
        @Override public void storageChecked(Storage storage, int num, boolean checked) {}
        @Override public void storageAllChecked(Storage storage) {}
        @Override public void storageCompleted(Storage storage) {}
        @Override public void setWantedPieces(Storage storage) {}
        @Override public void addMessage(String message) {}
    };

    // ── builders ─────────────────────────────────────────────────────

    static MetaInfo singleMeta(long length, int pieceLen) {
        int pieces = (int) ((length - 1) / pieceLen) + 1;
        return new MetaInfo(null, "single.bin", null, null, null,
                pieceLen, new byte[pieces * 20], length, false,
                null, null, null, null);
    }

    static MetaInfo multiMeta(long[] lengths, int pieceLen) {
        List<List<String>> files = new ArrayList<List<String>>();
        List<Long> ls = new ArrayList<Long>();
        long total = 0;
        for (int i = 0; i < lengths.length; i++) {
            List<String> path = new ArrayList<String>();
            path.add("f" + i + ".bin");
            files.add(path);
            ls.add(Long.valueOf(lengths[i]));
            total += lengths[i];
        }
        int pieces = (int) ((total - 1) / pieceLen) + 1;
        return new MetaInfo(null, "multi", null, files, ls,
                pieceLen, new byte[pieces * 20], total, false,
                null, null, null, null);
    }

    static BitField bits(int pieces, int... set) {
        BitField bf = new BitField(pieces);
        for (int p : set)
            bf.set(p);
        return bf;
    }

    static void setOldMtime(File f) {
        assertTrue("setLastModified", f.setLastModified(System.currentTimeMillis() - 60_000));
    }

    /** Single-file storage with a saved (partial) bitfield. */
    static Storage singleFileStorage(File work, long length, BitField saved) throws Exception {
        File base = new File(work, "single.bin");
        Files.write(base.toPath(), new byte[(int) length]);
        setOldMtime(base);
        MetaInfo meta = singleMeta(length, PIECE_LEN);
        Storage st = new Storage(null, base, meta, LISTENER, true);
        st.check(System.currentTimeMillis(), saved);
        return st;
    }

    /** Multi-file storage with a saved (partial) bitfield. */
    static Storage multiFileStorage(File work, long[] lengths, BitField saved) throws Exception {
        File base = new File(work, "multi" + System.nanoTime());
        assertTrue(base.mkdirs());
        for (int i = 0; i < lengths.length; i++) {
            File f = new File(base, "f" + i + ".bin");
            Files.write(f.toPath(), new byte[(int) lengths[i]]);
            setOldMtime(f);
        }
        MetaInfo meta = multiMeta(lengths, PIECE_LEN);
        Storage st = new Storage(null, base, meta, LISTENER, true);
        st.check(System.currentTimeMillis(), saved);
        return st;
    }

    static void deleteRecursive(File f) {
        File[] kids = f.listFiles();
        if (kids != null)
            for (File k : kids)
                deleteRecursive(k);
        f.delete();
    }

    // ── A1: single-file priority support ─────────────────────────────

    /** Single-file torrent: skip must stick and produce all-skip piece priorities. */
    @Test
    public void singleFileSkipPriority() throws Exception {
        File work = Files.createTempDirectory("pri-single").toFile();
        try {
            long length = 3L * PIECE_LEN;
            // piece 0 already downloaded
            Storage st = singleFileStorage(work, length, bits(3, 0));
            assertNotNull("single-file storage should be checked", st);

            // pre-fix: getPriority returns NORMAL no matter what
            st.setPriority(0, Storage.PRIORITY_SKIP);
            assertEquals(Storage.PRIORITY_SKIP, st.getPriority(0));
            assertArrayEquals(new int[]{Storage.PRIORITY_SKIP}, st.getFilePriorities());
            assertArrayEquals(new int[]{Storage.PRIORITY_SKIP, Storage.PRIORITY_SKIP, Storage.PRIORITY_SKIP},
                    st.getPiecePriorities());
            // pieces 1,2 are skipped and not downloaded
            assertEquals(2L * PIECE_LEN, st.getSkippedLength());
            // wanted length is file-granular: 0 when the only file is skipped
            assertEquals(0L, st.getWantedLength());

            // re-enable → everything wanted again
            st.setPriority(0, Storage.PRIORITY_NORMAL);
            assertEquals(Storage.PRIORITY_NORMAL, st.getPriority(0));
            assertArrayEquals(new int[]{Storage.PRIORITY_NORMAL, Storage.PRIORITY_NORMAL, Storage.PRIORITY_NORMAL},
                    st.getPiecePriorities());
            assertEquals(0L, st.getSkippedLength());
            assertEquals(length, st.getWantedLength());
        } finally {
            deleteRecursive(work);
        }
    }

    /** Multi-file torrent: skipping file B only affects B's pieces. */
    @Test
    public void multiFileSkipMiddleFile() throws Exception {
        File work = Files.createTempDirectory("pri-multi").toFile();
        try {
            long[] lengths = {2L * PIECE_LEN, PIECE_LEN};   // A: pieces 0-1, B: piece 2
            Storage st = multiFileStorage(work, lengths, bits(3, 0));
            st.setPriority(1, Storage.PRIORITY_SKIP);       // skip B
            assertEquals(Storage.PRIORITY_NORMAL, st.getPriority(0));
            assertEquals(Storage.PRIORITY_SKIP, st.getPriority(1));
            assertArrayEquals(new int[]{Storage.PRIORITY_NORMAL, Storage.PRIORITY_SKIP}, st.getFilePriorities());
            assertArrayEquals(new int[]{Storage.PRIORITY_NORMAL, Storage.PRIORITY_NORMAL, Storage.PRIORITY_SKIP},
                    st.getPiecePriorities());
            // piece 2 (B) is skipped and not downloaded
            assertEquals(PIECE_LEN, st.getSkippedLength());
            // wanted length is file-granular: only file A, whole length
            assertEquals(2L * PIECE_LEN, st.getWantedLength());

            // round-trip: set/get for both files
            st.setPriority(0, Storage.PRIORITY_SKIP);
            st.setPriority(1, Storage.PRIORITY_NORMAL);
            assertArrayEquals(new int[]{Storage.PRIORITY_SKIP, Storage.PRIORITY_NORMAL}, st.getFilePriorities());
        } finally {
            deleteRecursive(work);
        }
    }

    /** Spanning piece: the max-priority rule keeps a shared piece wanted. */
    @Test
    public void spanningPieceUsesMaxPriority() throws Exception {
        File work = Files.createTempDirectory("pri-span").toFile();
        try {
            // A: 2 pieces + 50 bytes; B: 16400 bytes → piece 2 spans both files,
            // piece 3 is B-only
            long[] lengths = {2L * PIECE_LEN + 50, 16400};
            Storage st = multiFileStorage(work, lengths, bits(4));
            st.setPriority(1, Storage.PRIORITY_SKIP);
            int[] pri = st.getPiecePriorities();
            assertEquals(4, pri.length);
            assertEquals(Storage.PRIORITY_NORMAL, pri[0]);
            assertEquals(Storage.PRIORITY_NORMAL, pri[1]);
            // piece 2 covers A's last 50 bytes + B's first 16334 → max(A,B) = wanted
            assertEquals(Storage.PRIORITY_NORMAL, pri[2]);
            // piece 3 covers only B's tail → skipped
            assertEquals(Storage.PRIORITY_SKIP, pri[3]);
            // skipped length: piece 3 (66 bytes), not piece 2 (still wanted)
            assertEquals(66L, st.getSkippedLength());
        } finally {
            deleteRecursive(work);
        }
    }

    // ── A2: per-file received bytes ──────────────────────────────────

    /** Single-file: boundary pieces + last-piece length. */
    @Test
    public void singleFileReceivedBytes() throws Exception {
        File work = Files.createTempDirectory("recv-single").toFile();
        try {
            long length = 2L * PIECE_LEN + 100;   // last piece is 100 bytes
            // pieces 0 and 2 done → one full piece + the 100-byte tail
            Storage st = singleFileStorage(work, length, bits(3, 0, 2));
            assertArrayEquals(new long[]{PIECE_LEN + 100}, st.getFileReceivedBytes());
            assertEquals(PIECE_LEN + 100, st.getWantedReceivedBytes());

            // only piece 1 → 16 KiB
            Storage st2 = singleFileStorage(work, length, bits(3, 1));
            assertArrayEquals(new long[]{PIECE_LEN}, st2.getFileReceivedBytes());
        } finally {
            deleteRecursive(work);
        }
    }

    /** Multi-file: cumulative offsets, boundary pieces, skipped-file exclusion. */
    @Test
    public void multiFileReceivedBytes() throws Exception {
        File work = Files.createTempDirectory("recv-multi").toFile();
        try {
            // A: 2 pieces + 50 bytes; B: 16400 bytes → piece 2 spans A's tail
            // (50) + B's head (16334); piece 3 = B's tail (66)
            long[] lengths = {2L * PIECE_LEN + 50, 16400};
            Storage st = multiFileStorage(work, lengths, bits(4, 0, 1, 3));
            long[] rv = st.getFileReceivedBytes();
            assertEquals(2, rv.length);
            // A: pieces 0,1 full; piece 2 (A's 50 bytes) NOT downloaded
            assertEquals(2L * PIECE_LEN, rv[0]);
            // B: piece 3 (66 bytes) downloaded
            assertEquals(66L, rv[1]);
            assertEquals(2L * PIECE_LEN + 66, st.getWantedReceivedBytes());

            // piece 2 downloaded too → A's tail counts, B's head (16334) counts
            Storage st2 = multiFileStorage(work, lengths, bits(4, 0, 1, 2));
            long[] rv2 = st2.getFileReceivedBytes();
            assertEquals(2L * PIECE_LEN + 50, rv2[0]);
            assertEquals(16334L, rv2[1]);

            // skip B → wantedReceived excludes B entirely (even though on disk)
            st2.setPriority(1, Storage.PRIORITY_SKIP);
            assertEquals(2L * PIECE_LEN + 50, st2.getWantedReceivedBytes());

            // complete bitfield → full lengths for both files
            Storage st3 = multiFileStorage(work, lengths, bits(4, 0, 1, 2, 3));
            assertArrayEquals(new long[]{2L * PIECE_LEN + 50, 16400}, st3.getFileReceivedBytes());
            assertEquals(2L * PIECE_LEN + 16450, st3.getWantedReceivedBytes());
        } finally {
            deleteRecursive(work);
        }
    }

    /** Complete torrent: all priorities read back as NORMAL (complete() gate). */
    @Test
    public void completeTorrentPriorities() throws Exception {
        File work = Files.createTempDirectory("pri-complete").toFile();
        try {
            long length = 2L * PIECE_LEN;
            Storage st = singleFileStorage(work, length, bits(2, 0, 1));
            assertTrue(st.complete());
            // complete() gates keep the write a no-op
            st.setPriority(0, Storage.PRIORITY_SKIP);
            assertEquals(Storage.PRIORITY_NORMAL, st.getPriority(0));
            assertNull(st.getFilePriorities());
            assertNull(st.getPiecePriorities());
            // ...but the file is fully received
            assertArrayEquals(new long[]{length}, st.getFileReceivedBytes());
            assertEquals(length, st.getWantedReceivedBytes());
        } finally {
            deleteRecursive(work);
        }
    }

    // ── End-to-end: single-file deselection stops downloads ──────────

    /**
     *  Loopback (reuses FullClientLoopbackTest's in-memory transport):
     *  a single-file torrent whose only file is deselected MID-DOWNLOAD
     *  must stop downloading; re-selecting must resume. Pre-A1 the
     *  skip was a no-op and everything downloaded despite deselection.
     */
    @Test
    public void singleFileDeselectStopsDownload() throws Exception {
        File work = Files.createTempDirectory("loop-single-skip").toFile();
        try {
            // 64 pieces × 16 KiB — big enough that the skip lands mid-download
            int n = 64;
            byte[][] pieces = new byte[n][];
            for (int i = 0; i < n; i++)
                pieces[i] = FullClientLoopbackTest.pieceData(i);
            byte[] torrentBytes = FullClientLoopbackTest.buildSingleFileTorrent(pieces);
            File torrentFile = new File(work, "single.torrent");
            Files.write(torrentFile.toPath(), torrentBytes);

            byte[] seederHash = new byte[32];
            for (int i = 0; i < 32; i++)
                seederHash[i] = (byte) (200 + i);
            FullClientLoopbackTest.LoopTransport transport = new FullClientLoopbackTest.LoopTransport(new byte[32]);
            FullClientLoopbackTest.LoopIdentity seeder = new FullClientLoopbackTest.LoopIdentity(seederHash);
            transport._known.put(Hash.create(seederHash), seeder);
            FullClientLoopbackTest.FakeSeeder seederServer =
                    new FullClientLoopbackTest.FakeSeeder(
                            FullClientLoopbackTest.singleFileInfohashOf(pieces), pieces);
            // throttle the seeder so the test can deselect mid-download
            seederServer._requestDelayMs = 30;
            transport._seederHandler = stream -> {
                try {
                    seederServer.handle(stream);
                } catch (Throwable t) {
                    // seeder errors surface as no requests served
                }
            };

            File dataDir = new File(work, "data");
            dataDir.mkdirs();
            ClientContext ctx = FullClientLoopbackTest.makeContext(transport, work);
            FullClientLoopbackTest.ListenerAdapter listener = new FullClientLoopbackTest.ListenerAdapter();
            Snark snark = new Snark(ctx, torrentFile.getAbsolutePath(), null, -1,
                    null, listener, listener, null, null, dataDir.getAbsolutePath());
            snark.startTorrent();

            PeerCoordinator coord = snark.getCoordinator();
            assertNotNull(coord);
            Storage st = snark.getStorage();

            // connect the seeder and let the download start
            PeerID seederPid = new PeerID(seederHash, ctx);
            boolean added = coord.addPeer(new Peer(seederPid, snark.getID(), snark.getInfoHash(), snark.getMetaInfo()));
            assertTrue("addPeer rejected", added);
            long deadline = System.currentTimeMillis() + 15_000;
            while (st.getBitField().count() == 0 && System.currentTimeMillis() < deadline)
                Thread.sleep(100);
            assertTrue("download did not start; requestsServed=" + seederServer._requestsServed.get(),
                    st.getBitField().count() > 0);
            int servedBeforeSkip = seederServer._requestsServed.get();

            // per-piece availability while the seeder is connected:
            // every piece has the seeder (1) plus us when downloaded (2)
            int[] avail = coord.getPiecesAvailability();
            assertEquals(64, avail.length);
            boolean weHold = false;
            for (int a : avail) {
                assertTrue("availability out of range: " + a, a >= 1 && a <= 2);
                if (a == 2)
                    weHold = true;
            }
            assertTrue("we should hold at least one piece", weHold);
            // Snark passthrough returns the same data
            assertArrayEquals(avail, snark.getPiecesAvailability());

            // deselect the only file mid-download
            st.setPriority(0, Storage.PRIORITY_SKIP);
            snark.updatePiecePriorities();
            coord.setWantedPieces();
            // let in-flight requests drain (loopback is fast)
            Thread.sleep(1000);
            int countAtSkip = st.getBitField().count();
            int servedAtSkip = seederServer._requestsServed.get();
            Thread.sleep(3000);
            assertEquals("deselected single-file torrent must not download further",
                    countAtSkip, st.getBitField().count());
            assertEquals("no new requests after deselection",
                    servedAtSkip, seederServer._requestsServed.get());

            // re-select → downloads to completion on the same connection
            st.setPriority(0, Storage.PRIORITY_NORMAL);
            snark.updatePiecePriorities();
            coord.setWantedPieces();
            boolean ok = listener.completed.await(60, TimeUnit.SECONDS);
            assertTrue("download did not complete after re-select; servedBeforeSkip=" + servedBeforeSkip
                    + " totalServed=" + seederServer._requestsServed.get(), ok);
            assertTrue(st.getBitField().complete());
            assertTrue(seederServer._requestsServed.get() > servedBeforeSkip);

            snark.stopTorrent();
        } finally {
            deleteRecursive(work);
        }
    }

    /** Loopback: multi-file deselection — skip B, only A/C download. */
    @Test
    public void multiFileDeselectSkipsRequests() throws Exception {
        File work = Files.createTempDirectory("loop-multi-skip").toFile();
        try {
            byte[][] pieces = {FullClientLoopbackTest.pieceData(0),
                    FullClientLoopbackTest.pieceData(1),
                    FullClientLoopbackTest.pieceData(2)};
            byte[] torrentBytes = FullClientLoopbackTest.buildMultiFileTorrent(pieces);
            File torrentFile = new File(work, "multi.torrent");
            Files.write(torrentFile.toPath(), torrentBytes);

            byte[] seederHash = new byte[32];
            for (int i = 0; i < 32; i++)
                seederHash[i] = (byte) (200 + i);
            FullClientLoopbackTest.LoopTransport transport = new FullClientLoopbackTest.LoopTransport(new byte[32]);
            FullClientLoopbackTest.LoopIdentity seeder = new FullClientLoopbackTest.LoopIdentity(seederHash);
            transport._known.put(Hash.create(seederHash), seeder);
            FullClientLoopbackTest.FakeSeeder seederServer =
                    new FullClientLoopbackTest.FakeSeeder(
                            FullClientLoopbackTest.infohashOf(pieces, torrentBytes), pieces);
            transport._seederHandler = stream -> {
                try {
                    seederServer.handle(stream);
                } catch (Throwable t) {
                    // seeder errors surface as no requests served
                }
            };

            File dataDir = new File(work, "data");
            dataDir.mkdirs();
            ClientContext ctx = FullClientLoopbackTest.makeContext(transport, work);
            FullClientLoopbackTest.ListenerAdapter listener = new FullClientLoopbackTest.ListenerAdapter();
            Snark snark = new Snark(ctx, torrentFile.getAbsolutePath(), null, -1,
                    null, listener, listener, null, null, dataDir.getAbsolutePath());
            snark.startTorrent();

            PeerCoordinator coord = snark.getCoordinator();
            assertNotNull(coord);

            // files: A = pieces 0-1, B = piece 2 — skip B before any peer
            Storage st = snark.getStorage();
            st.setPriority(1, Storage.PRIORITY_SKIP);
            snark.updatePiecePriorities();
            coord.setWantedPieces();

            PeerID seederPid = new PeerID(seederHash, ctx);
            boolean added = coord.addPeer(new Peer(seederPid, snark.getID(), snark.getInfoHash(), snark.getMetaInfo()));
            assertTrue("addPeer rejected", added);

            // A/C complete; B's piece must never be requested/downloaded
            boolean ok = listener.completed.await(60, TimeUnit.SECONDS);
            assertTrue("download did not complete for wanted files", ok);
            BitField bf = st.getBitField();
            assertTrue("piece 0 (A)", bf.get(0));
            assertTrue("piece 1 (A)", bf.get(1));
            assertFalse("piece 2 (B) must stay undownloaded", bf.get(2));
            // skipped length still counts B's undownloaded piece
            assertEquals(PIECE_LEN, st.getSkippedLength());
            // per-file bytes: A full, B empty
            long[] rv = st.getFileReceivedBytes();
            assertEquals(2L * PIECE_LEN, rv[0]);
            assertEquals(0L, rv[1]);

            snark.stopTorrent();
        } finally {
            deleteRecursive(work);
        }
    }
}
