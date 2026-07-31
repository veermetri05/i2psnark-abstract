package org.klomp.snark;

import org.junit.Test;
import static org.junit.Assert.*;

import org.klomp.snark.data.Base32;
import org.klomp.snark.data.DataHelper;
import org.klomp.snark.spi.PeerIdentity;
import org.klomp.snark.bencode.InvalidBEncodingException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.DataInputStream;
import java.util.Arrays;

/**
 *  Tests for the full-client leaf files ported from I2PSnark:
 *  Message wire serialization, PeerID, MagnetURI.
 */
public class FullClientLeafTest {

    // ── Message wire format (BEP 3 + BEP 10 + BEP 6/5) ──────────────

    private static byte[] send(Message m) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        m.sendMessage(new DataOutputStream(bos));
        return bos.toByteArray();
    }

    @Test
    public void messageKeepAlive() throws Exception {
        byte[] b = send(new Message(Message.KEEP_ALIVE));
        assertEquals(4, b.length);
        assertEquals(0, DataHelper.fromLong(b, 0, 4));
    }

    @Test
    public void messageChoke() throws Exception {
        byte[] b = send(new Message(Message.CHOKE));
        assertEquals(5, b.length);
        assertEquals(1, DataHelper.fromLong(b, 0, 4)); // length prefix
        assertEquals(Message.CHOKE, b[4]);
    }

    @Test
    public void messageHave() throws Exception {
        byte[] b = send(new Message(Message.HAVE, 42));
        assertEquals(9, b.length);
        assertEquals(5, DataHelper.fromLong(b, 0, 4)); // length prefix
        assertEquals(Message.HAVE, b[4]);
        assertEquals(42, DataHelper.fromLong(b, 5, 4));
    }

    @Test
    public void messageRequest() throws Exception {
        byte[] b = send(new Message(Message.REQUEST, 7, 16384, 16384));
        assertEquals(17, b.length);
        assertEquals(13, DataHelper.fromLong(b, 0, 4));
        assertEquals(Message.REQUEST, b[4]);
        assertEquals(7, DataHelper.fromLong(b, 5, 4));
        assertEquals(16384, DataHelper.fromLong(b, 9, 4));
        assertEquals(16384, DataHelper.fromLong(b, 13, 4));
    }

    @Test
    public void messagePieceDeferredLoad() throws Exception {
        final byte[] payload = new byte[8192];
        for (int i = 0; i < payload.length; i++)
            payload[i] = (byte) (i % 251);
        Message m = new Message(3, 0, payload.length, (piece, begin, length) -> {
            assertEquals(3, piece);
            assertEquals(0, begin);
            assertEquals(payload.length, length);
            return new org.klomp.snark.data.ByteArray(payload);
        });
        byte[] b = send(m);
        assertEquals(13 + payload.length, b.length);
        assertEquals(1 + 4 + 4 + payload.length, DataHelper.fromLong(b, 0, 4));
        assertEquals(Message.PIECE, b[4]);
        assertArrayEquals(payload, Arrays.copyOfRange(b, 13, b.length));
    }

    @Test
    public void messageBitfield() throws Exception {
        byte[] bits = {0x55, (byte) 0xAA};
        byte[] b = send(new Message(bits));
        assertEquals(4 + 1 + 2, b.length);
        assertEquals(3, DataHelper.fromLong(b, 0, 4));
        assertEquals(Message.BITFIELD, b[4]);
        assertArrayEquals(bits, Arrays.copyOfRange(b, 5, 7));
    }

    @Test
    public void messageExtension() throws Exception {
        byte[] payload = {(byte) 0xde, (byte) 0xad};
        byte[] b = send(new Message(2, payload)); // id 2 = ut_metadata
        assertEquals(4 + 1 + 1 + 2, b.length);
        assertEquals(4, DataHelper.fromLong(b, 0, 4));
        assertEquals(Message.EXTENSION, b[4]);
        assertEquals(2, b[5]);
        assertArrayEquals(payload, Arrays.copyOfRange(b, 6, 8));
    }

    @Test
    public void messagePort() throws Exception {
        byte[] b = send(new Message(Message.PORT, 6881));
        assertEquals(4 + 1 + 2, b.length);
        assertEquals(3, DataHelper.fromLong(b, 0, 4));
        assertEquals(Message.PORT, b[4]);
        assertEquals(6881, DataHelper.fromLong(b, 5, 2));
    }

    // ── PeerID ───────────────────────────────────────────────────────

    private static final byte[] HASH32 = new byte[32];

    static {
        for (int i = 0; i < 32; i++)
            HASH32[i] = (byte) i;
    }

    @Test
    public void peerIDEqualityByDestHash() throws Exception {
        PeerID a = new PeerID(HASH32, (ClientContext) null);
        PeerID b = new PeerID(Arrays.copyOf(HASH32, 32), (ClientContext) null);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertEquals(ClientContext.PORT, a.getPort());
        assertArrayEquals(HASH32, a.getDestHash());
    }

    @Test
    public void peerIDBadHashLength() {
        try {
            new PeerID(new byte[20], (ClientContext) null);
            fail("expected InvalidBEncodingException");
        } catch (InvalidBEncodingException e) {
            // expected
        }
    }

    @Test
    public void peerIDLookupNullContext() throws Exception {
        // no context → deferred lookup yields null, no crash
        PeerID pid = new PeerID(HASH32, (ClientContext) null);
        assertNull(pid.getAddress());
        assertNull(pid.getID());
    }

    @Test
    public void peerIDWebSeedToString() throws Exception {
        // id = "WebSeedBEP19" → WebSeed@b32
        PeerID pid = new PeerID(DataHelper.getASCII("WebSeedBEP19"),
                                (PeerIdentity) new TestIdentity(HASH32));
        String s = pid.toString();
        assertTrue(s.startsWith("WebSeed@"));
        assertTrue(s.endsWith(".b32.i2p"));
        assertEquals(Base32.encode(HASH32) + ".b32.i2p",
                     s.substring("WebSeed@".length()));
    }

    @Test
    public void peerIDUnknownToString() throws Exception {
        PeerID pid = new PeerID(HASH32, (ClientContext) null);
        String s = pid.toString();
        assertEquals("unkn@" + org.klomp.snark.data.Base64.encode(HASH32).substring(0, 6), s);
    }

    // ── MagnetURI ────────────────────────────────────────────────────

    @Test
    public void magnetHexInfoHash() {
        String ih = "0123456789abcdef0123456789abcdef01234567";
        MagnetURI m = new MagnetURI("magnet:?xt=urn:btih:" + ih + "&dn=test.torrent");
        assertNotNull(m.getInfoHash());
        assertEquals(20, m.getInfoHash().length);
        assertEquals(0x01, m.getInfoHash()[0] & 0xff);
        assertTrue(m.getName().contains("test.torrent"));
    }

    @Test
    public void magnetBase32InfoHash() {
        byte[] ih = new byte[20];
        for (int i = 0; i < 20; i++)
            ih[i] = (byte) (i + 1);
        String b32 = Base32.encode(ih).toUpperCase();
        MagnetURI m = new MagnetURI("magnet:?xt=urn:btih:" + b32);
        assertArrayEquals(ih, m.getInfoHash());
    }

    @Test
    public void magnetTrackerFiltering() {
        String url = "magnet:?xt=urn:btih:0123456789abcdef0123456789abcdef01234567"
                + "&tr=http://tracker2.postman.i2p/announce.php"
                + "&tr=udp://tracker.openbittorrent.com:80"
                + "&tr=http://clearnet.example.com/announce";
        MagnetURI m = new MagnetURI(url);
        assertNotNull(m.getTrackerURL());
        assertTrue(m.getTrackerURL().startsWith("http://tracker2.postman.i2p"));
        // only the .i2p tracker survives
        assertEquals(1, m.getTrackerURLs().size());
    }

    @Test
    public void magnetMaggot() {
        MagnetURI m = new MagnetURI("maggot://0123456789abcdef0123456789abcdef01234567");
        assertEquals(20, m.getInfoHash().length);
    }

    @Test
    public void magnetRejectsClearnet() {
        try {
            new MagnetURI("http://example.com/x.torrent");
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }
        try {
            new MagnetURI("magnet:?xt=urn:btmh:1220deadbeef");
            fail("expected IllegalArgumentException (v2 not supported)");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    @Test
    public void magnetBep53PrioritiesIgnoredGracefully() {
        // BEP 53: &so=... — must not crash; the xt is still parsed
        MagnetURI m = new MagnetURI("magnet:?xt=urn:btih:0123456789abcdef0123456789abcdef01234567&so=0,2-4");
        assertNotNull(m.getInfoHash());
    }

    // ── BitField ─────────────────────────────────────────────────────

    @Test
    public void bitfieldBasics() {
        BitField bf = new BitField(100);
        assertEquals(0, bf.count());
        assertFalse(bf.complete());
        bf.set(0);
        bf.set(99);
        assertTrue(bf.get(0));
        assertTrue(bf.get(99));
        assertEquals(2, bf.count());
        bf.clear(0);
        assertFalse(bf.get(0));
        bf.setAll();
        assertTrue(bf.complete());
        assertEquals(100, bf.count());
    }

    @Test
    public void bitfieldFromBytes() {
        byte[] bytes = {(byte) 0x80, 0x00};
        BitField bf = new BitField(bytes, 9);
        assertTrue(bf.get(0));
        assertFalse(bf.get(8));
        assertEquals(1, bf.count());
    }

    // ── helper ───────────────────────────────────────────────────────

    /** Minimal identity for tests (no I2P types). */
    static class TestIdentity implements PeerIdentity {
        private final byte[] _hash;
        TestIdentity(byte[] hash) { _hash = hash; }
        @Override public byte[] getData() { return _hash; }
        @Override public org.klomp.snark.data.Hash calculateHash() {
            return org.klomp.snark.data.Hash.create(_hash);
        }
        @Override public String toBase64() {
            return org.klomp.snark.data.Base64.encode(_hash);
        }
        @Override public String toBase32() {
            return Base32.encode(_hash) + ".b32.i2p";
        }
        @Override public boolean equals(Object o) {
            return o instanceof TestIdentity && Arrays.equals(_hash, ((TestIdentity) o)._hash);
        }
        @Override public int hashCode() { return Arrays.hashCode(_hash); }
    }
}
