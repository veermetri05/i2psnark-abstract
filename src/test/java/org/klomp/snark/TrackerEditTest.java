package org.klomp.snark;

import org.junit.Test;

import static org.junit.Assert.*;

import org.klomp.snark.bencode.BEncoder;
import org.klomp.snark.data.Hash;
import org.klomp.snark.event.SimpleEventBus;
import org.klomp.snark.spi.Environment;
import org.klomp.snark.util.FileStorage;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 *  Runtime tracker editing (GUI add/replace/delete): the app calls
 *  {@code Snark.addTrackerURL / removeTrackerURL / replaceTrackerURLs}
 *  while a torrent is running (or before its first start), and the
 *  TrackerClient must accept the changes live, validate them like
 *  setup(), and not lose them on the first start / restart.
 */
public class TrackerEditTest {

    private static final String TRACKER_A =
            "http://" + org.klomp.snark.data.Base32.encode(trackerKey(10)) + ".b32.i2p/announce";
    private static final String TRACKER_B =
            "http://" + org.klomp.snark.data.Base32.encode(trackerKey(20)) + ".b32.i2p/announce";
    private static final String TRACKER_C =
            "http://tracker2.postman.i2p/announce.php";   // named host
    private static final String TRACKER_BAD = "ftp://not.i2p/x";  // rejected by getTrackerKey

    private static byte[] trackerKey(int seed) {
        byte[] key = new byte[32];
        for (int i = 0; i < 32; i++)
            key[i] = (byte) (seed + i);
        return key;
    }

    /** A torrent with one announce URL (like buildMultiFileTorrent). */
    private static byte[] buildTorrentWithAnnounce(String announce) throws Exception {
        byte[][] pieces = {FullClientLoopbackTest.pieceData(0), FullClientLoopbackTest.pieceData(1)};
        List<Object> files = new ArrayList<Object>();
        files.add(FullClientLoopbackTest.filesEntry("dir/a.bin", 16384L));
        java.util.Map<String, Object> info = new java.util.HashMap<String, Object>();
        info.put("name", "tracker-edit");
        info.put("piece length", Integer.valueOf(16384));
        info.put("files", files);
        byte[] hashes = new byte[pieces.length * 20];
        for (int i = 0; i < pieces.length; i++)
            System.arraycopy(org.klomp.snark.data.SHA1.hash(pieces[i]), 0, hashes, i * 20, 20);
        info.put("pieces", hashes);
        java.util.Map<String, Object> top = new java.util.HashMap<String, Object>();
        top.put("announce", announce);
        top.put("info", info);
        return BEncoder.bencode(top);
    }

    private static Snark makeSnark(File work, byte[] torrentBytes) throws Exception {
        File torrentFile = new File(work, "edit.torrent");
        Files.write(torrentFile.toPath(), torrentBytes);
        File dataDir = new File(work, "data");
        dataDir.mkdirs();
        FullClientLoopbackTest.LoopTransport transport =
                new FullClientLoopbackTest.LoopTransport(new byte[32]);
        ClientContext ctx = FullClientLoopbackTest.makeContext(transport, work);
        return new Snark(ctx, torrentFile.getAbsolutePath(), null, -1,
                null, new FullClientLoopbackTest.ListenerAdapter(),
                new FullClientLoopbackTest.ListenerAdapter(), null, null,
                dataDir.getAbsolutePath());
    }

    /** Add while running: new tracker appears in the announce list. */
    @Test
    public void addTrackerWhileRunning() throws Exception {
        File work = Files.createTempDirectory("tracker-edit-add").toFile();
        try {
            Snark snark = makeSnark(work, buildTorrentWithAnnounce(TRACKER_A));
            snark.startTorrent();
            TrackerClient tc = snark.getTrackerClient();
            assertNotNull("TrackerClient is created on startTorrent", tc);
            // wait for the first setup() pass
            long deadline = System.currentTimeMillis() + 5000;
            while (tc.trackerURLs().isEmpty() && System.currentTimeMillis() < deadline)
                Thread.sleep(20);

            assertTrue("setup() should pick up the metainfo tracker",
                    tc.trackerURLs().contains(TRACKER_A));

            snark.addTrackerURL(TRACKER_B);
            List<String> urls = tc.trackerURLs();
            assertTrue("added tracker should be in the live list", urls.contains(TRACKER_B));
            assertTrue("metainfo tracker must stay", urls.contains(TRACKER_A));

            // duplicates and invalid URLs are rejected like setup()
            snark.addTrackerURL(TRACKER_B);
            snark.addTrackerURL(TRACKER_BAD);
            assertEquals("dup + invalid must not grow the list", 2, tc.trackerURLs().size());

            snark.stopTorrent();
        } finally {
            FullClientLoopbackTest.deleteRecursive(work);
        }
    }

    /** Remove: works for both user-added and metainfo trackers. */
    @Test
    public void removeTracker() throws Exception {
        File work = Files.createTempDirectory("tracker-edit-rm").toFile();
        try {
            Snark snark = makeSnark(work, buildTorrentWithAnnounce(TRACKER_A));
            snark.startTorrent();
            TrackerClient tc = snark.getTrackerClient();
            long deadline = System.currentTimeMillis() + 5000;
            while (tc.trackerURLs().isEmpty() && System.currentTimeMillis() < deadline)
                Thread.sleep(20);
            snark.addTrackerURL(TRACKER_B);
            assertEquals(2, tc.trackerURLs().size());

            // remove the metainfo tracker (matched by host hash)
            snark.removeTrackerURL(TRACKER_A);
            List<String> urls = tc.trackerURLs();
            assertFalse("metainfo tracker must be removable", urls.contains(TRACKER_A));
            assertTrue("other tracker must survive", urls.contains(TRACKER_B));

            // removing a non-present URL is a no-op
            snark.removeTrackerURL(TRACKER_A);
            assertEquals(1, tc.trackerURLs().size());

            snark.stopTorrent();
        } finally {
            FullClientLoopbackTest.deleteRecursive(work);
        }
    }

    /** Replace: the given URLs become the whole list (GUI "replace"). */
    @Test
    public void replaceTrackers() throws Exception {
        File work = Files.createTempDirectory("tracker-edit-replace").toFile();
        try {
            Snark snark = makeSnark(work, buildTorrentWithAnnounce(TRACKER_A));
            snark.startTorrent();
            TrackerClient tc = snark.getTrackerClient();
            long deadline = System.currentTimeMillis() + 5000;
            while (tc.trackerURLs().isEmpty() && System.currentTimeMillis() < deadline)
                Thread.sleep(20);

            snark.replaceTrackerURLs(Arrays.asList(TRACKER_C, TRACKER_B));
            List<String> urls = tc.trackerURLs();
            assertEquals("replace must drop the metainfo tracker", 2, urls.size());
            assertTrue(urls.contains(TRACKER_C));
            assertTrue(urls.contains(TRACKER_B));

            // replace with an empty list = no trackers (DHT only)
            snark.replaceTrackerURLs(new ArrayList<String>());
            assertTrue("empty replace must clear the list", tc.trackerURLs().isEmpty());

            snark.stopTorrent();
        } finally {
            FullClientLoopbackTest.deleteRecursive(work);
        }
    }

    /**
     *  Edit before the first start (torrent added paused): the edited
     *  MetaInfo (via replaceMetaInfo) plus the live list must survive
     *  the first setup() without duplicates.
     */
    @Test
    public void editBeforeFirstStart() throws Exception {
        File work = Files.createTempDirectory("tracker-edit-paused").toFile();
        try {
            Snark snark = makeSnark(work, buildTorrentWithAnnounce(TRACKER_A));
            // torrent never started: no TrackerClient yet (lazy creation)
            assertNull(snark.getTrackerClient());

            // the app swaps the meta (infohash preserved); the edited
            // meta is what the TrackerClient sees when it is created.
            // The app always writes BOTH announce and announce-list.
            List<List<String>> tiers = new ArrayList<List<String>>();
            tiers.add(Arrays.asList(TRACKER_B, TRACKER_C));
            MetaInfo edited = new MetaInfo(snark.getMetaInfo(),
                    TRACKER_B, tiers, null, null, null);
            snark.replaceMetaInfo(edited);
            snark.replaceTrackerURLs(Arrays.asList(TRACKER_B, TRACKER_C));

            snark.startTorrent();
            TrackerClient tc = snark.getTrackerClient();
            long deadline = System.currentTimeMillis() + 5000;
            while (tc.trackerURLs().size() < 2 && System.currentTimeMillis() < deadline)
                Thread.sleep(20);

            List<String> urls = tc.trackerURLs();
            assertEquals("setup() must not duplicate or resurrect the old tracker", 2, urls.size());
            assertTrue(urls.contains(TRACKER_B));
            assertTrue(urls.contains(TRACKER_C));
            assertFalse(urls.contains(TRACKER_A));
            // the meta was swapped too (UI reads it)
            assertEquals(TRACKER_B, snark.getMetaInfo().getAnnounce());

            snark.stopTorrent();
        } finally {
            FullClientLoopbackTest.deleteRecursive(work);
        }
    }
}
