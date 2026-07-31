package org.klomp.snark;

import org.junit.Test;
import static org.junit.Assert.*;

import org.klomp.snark.bencode.BEncoder;
import org.klomp.snark.bencode.BEValue;
import org.klomp.snark.data.DataHelper;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 *  Tests for the wire/coordinator batch: Storage (full piece I/O),
 *  TrackerInfo parsing, ExtensionHandler handshake.
 */
public class FullClientWireTest {

    // ── Storage ──────────────────────────────────────────────────────

    /** Deterministic piece data for piece n. */
    private static byte[] pieceData(int n, int pieceLen) {
        byte[] data = new byte[pieceLen];
        for (int i = 0; i < data.length; i++)
            data[i] = (byte) ((n * 31 + i) % 251);
        return data;
    }

    /** Build a single-file MetaInfo whose piece hashes are the real SHA-1s of pieceData(n). */
    private static MetaInfo buildMeta(int pieces, int pieceLen) throws Exception {
        byte[] pieceHashes = new byte[pieces * 20];
        for (int i = 0; i < pieces; i++)
            System.arraycopy(org.klomp.snark.data.SHA1.hash(pieceData(i, pieceLen)), 0, pieceHashes, i * 20, 20);
        Map<String, Object> info = new HashMap<String, Object>();
        info.put("name", "test.bin");
        info.put("piece length", Integer.valueOf(pieceLen));
        info.put("length", Long.valueOf((long) pieces * pieceLen));
        info.put("pieces", pieceHashes);
        Map<String, Object> top = new HashMap<String, Object>();
        top.put("info", info);
        return new MetaInfo(new ByteArrayInputStream(BEncoder.bencode(top)));
    }

    private static Storage makeStorage(MetaInfo meta, File dir) throws Exception {
        File base = new File(dir, "data");
        return new Storage(null, base, meta, null, false);
    }

    /** Write one piece into a Storage via a PartialPiece (as the wire layer does). */
    private static void putPiece(Storage storage, MetaInfo meta, int pieceNum, int pieceLen, byte[] data, File dir)
            throws Exception {
        PartialPiece pp = new PartialPiece(new Piece(pieceNum), pieceLen, dir);
        pp.read(new java.io.DataInputStream(new ByteArrayInputStream(data)), 0, pieceLen, noopBwl());
        assertTrue("piece " + pieceNum + " must hash correctly", meta.checkPiece(pp));
        storage.putPiece(pp);
    }

    private static BandwidthListener noopBwl() {
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

    @Test
    public void storageWriteAndRecheck() throws Exception {
        File dir = Files.createTempDirectory("snark-storage").toFile();
        try {
            MetaInfo meta = buildMeta(4, 16 * 1024);
            Storage storage = makeStorage(meta, dir);
            // check: all pieces missing
            storage.check();
            assertEquals(0, storage.getBitField().count());
            // write piece 1
            byte[] piece = pieceData(1, 16 * 1024);
            putPiece(storage, meta, 1, piece.length, piece, dir);
            assertEquals(1, storage.getBitField().count());
            assertTrue(storage.getBitField().get(1));
            // reopen + recheck: piece 1 survives
            storage.close();
            storage = makeStorage(meta, dir);
            storage.check();
            assertEquals(1, storage.getBitField().count());
            assertTrue(storage.getBitField().get(1));
        } finally {
            deleteRecursive(dir);
        }
    }

    @Test
    public void storageReadPiece() throws Exception {
        File dir = Files.createTempDirectory("snark-storage2").toFile();
        try {
            MetaInfo meta = buildMeta(2, 16 * 1024);
            Storage storage = makeStorage(meta, dir);
            storage.check();
            byte[] piece = pieceData(0, 16 * 1024);
            putPiece(storage, meta, 0, piece.length, piece, dir);
            org.klomp.snark.data.ByteArray out = storage.getPiece(0, 0, 1024);
            assertNotNull(out);
            assertEquals(1024, out.getData().length);
            for (int i = 0; i < 1024; i++)
                assertEquals(piece[i], out.getData()[i]);
        } finally {
            deleteRecursive(dir);
        }
    }

    @Test
    public void storageBadPieceRejected() throws Exception {
        File dir = Files.createTempDirectory("snark-storage3").toFile();
        try {
            MetaInfo meta = buildMeta(2, 16 * 1024);
            Storage storage = makeStorage(meta, dir);
            storage.check();
            byte[] garbage = new byte[16 * 1024]; // all zeros, wrong hash
            boolean ok = storage.putPiece(badPartialPiece(meta, garbage, dir));
            assertFalse(ok);
            assertEquals(0, storage.getBitField().count());
        } finally {
            deleteRecursive(dir);
        }
    }

    @Test
    public void storageFilterName() {
        // Windows-illegal chars filtered
        String filtered = Storage.filterName("a/b\\c:d*e?f\"g<h>i|j");
        assertFalse(filtered.contains("/"));
        assertFalse(filtered.contains("\\"));
        assertFalse(filtered.contains(":"));
    }

    /** PartialPiece that hashes wrong (all zeros). */
    private static PartialPiece badPartialPiece(MetaInfo meta, byte[] data, File dir) throws Exception {
        PartialPiece pp = new PartialPiece(new Piece(0), data.length, dir);
        pp.read(new java.io.DataInputStream(new ByteArrayInputStream(data)), 0, data.length, noopBwl());
        assertFalse(meta.checkPiece(pp));
        return pp;
    }

    private static void deleteRecursive(File f) {
        File[] kids = f.listFiles();
        if (kids != null)
            for (File k : kids)
                deleteRecursive(k);
        f.delete();
    }

    // ── TrackerInfo ──────────────────────────────────────────────────

    @Test
    public void trackerInfoCompactPeers() throws Exception {
        // compact format 2: one string of concatenated 32-byte hashes
        byte[] peer1 = new byte[32];
        for (int i = 0; i < 32; i++)
            peer1[i] = (byte) i;
        byte[] peer2 = new byte[32];
        for (int i = 0; i < 32; i++)
            peer2[i] = (byte) (100 + i);

        Map<String, Object> resp = new HashMap<String, Object>();
        resp.put("interval", Integer.valueOf(1800));
        byte[] peers = new byte[64];
        System.arraycopy(peer1, 0, peers, 0, 32);
        System.arraycopy(peer2, 0, peers, 32, 32);
        resp.put("peers", peers);
        resp.put("complete", Integer.valueOf(10));
        resp.put("incomplete", Integer.valueOf(5));

        byte[] bencoded = BEncoder.bencode(resp);
        TrackerInfo info = new TrackerInfo(new ByteArrayInputStream(bencoded),
                new byte[20], new byte[20], null, null);
        assertEquals(2, info.getPeers().size());
        assertEquals(1800, info.getInterval());
        assertEquals(10, info.getSeedCount());
        assertNull(info.getFailureReason());
    }

    @Test
    public void trackerInfoFailure() throws Exception {
        Map<String, Object> resp = new HashMap<String, Object>();
        resp.put("failure reason", "torrent not registered");
        byte[] bencoded = BEncoder.bencode(resp);
        TrackerInfo info = new TrackerInfo(new ByteArrayInputStream(bencoded),
                new byte[20], new byte[20], null, null);
        assertEquals("torrent not registered", info.getFailureReason());
        assertEquals(-1, info.getInterval());
    }

    // ── ExtensionHandler ─────────────────────────────────────────────

    @Test
    public void extensionHandshakeBencode() throws Exception {
        byte[] hs = ExtensionHandler.getHandshake(12345, true, true, false);
        // parse back with the bencode decoder
        org.klomp.snark.bencode.BDecoder dec =
                new org.klomp.snark.bencode.BDecoder(new ByteArrayInputStream(hs));
        Map<String, BEValue> map = dec.bdecodeMap().getMap();
        assertNotNull(map.get("m"));
        assertEquals(12345, map.get("metadata_size").getInt());
        Map<String, BEValue> m = map.get("m").getMap();
        assertNotNull(m.get(ExtensionHandler.TYPE_METADATA));
        assertNotNull(m.get(ExtensionHandler.TYPE_PEX));
        assertNotNull(m.get(ExtensionHandler.TYPE_DHT));
        // comments dropped in the abstract port
        assertNull(m.get("ut_comment"));
    }

    // ── PeerCoordinatorSet ───────────────────────────────────────────

    @Test
    public void coordinatorSetRoundTrip() {
        PeerCoordinatorSet set = new PeerCoordinatorSet();
        byte[] ih = new byte[20];
        for (int i = 0; i < 20; i++)
            ih[i] = (byte) i;
        // can't construct a real PeerCoordinator without a full engine,
        // so test the map keying through a dummy via add/get with null-free path:
        // (the set is only exercised with real coordinators in the loopback test)
        assertEquals(0, count(set));
    }

    private static int count(Iterable<PeerCoordinator> it) {
        int n = 0;
        for (PeerCoordinator pc : it)
            n++;
        return n;
    }
}
