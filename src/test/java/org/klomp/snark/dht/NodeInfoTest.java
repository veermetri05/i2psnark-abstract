package org.klomp.snark.dht;

import org.junit.Test;
import static org.junit.Assert.*;

import org.klomp.snark.data.Base64;
import org.klomp.snark.data.Hash;
import org.klomp.snark.spi.PeerIdentity;
import org.klomp.snark.spi.PeerIdentityFactory;
import org.klomp.snark.spi.RandomSource;

/**
 *  NodeInfo / NID tests — compact 54-byte format and the persistent
 *  storage string round-trip.
 */
public class NodeInfoTest {

    static class TestIdentity implements PeerIdentity {
        private final byte[] _data;
        private final Hash _hash;
        TestIdentity(byte[] data) {
            _data = data;
            _hash = Hash.create(org.klomp.snark.data.SHA256.hash(data));
        }
        public byte[] getData() { return _data; }
        public Hash calculateHash() { return _hash; }
        public String toBase64() { return Base64.encode(_data); }
        public String toBase32() { return _hash.toBase64().toLowerCase().replace("=", "") + ".b32.i2p"; }
        @Override public boolean equals(Object o) {
            return o instanceof TestIdentity && java.util.Arrays.equals(_data, ((TestIdentity) o)._data);
        }
        @Override public int hashCode() { return _hash.hashCode(); }
    }

    static class TestIdentityFactory implements PeerIdentityFactory {
        public PeerIdentity fromBytes(byte[] data) { return new TestIdentity(data); }
        public PeerIdentity fromBase64(String s) { return new TestIdentity(Base64.decode(s)); }
    }

    @Test
    public void testNodeInfoCompactFormat() {
        byte[] destData = new byte[387];
        new java.util.Random(42).nextBytes(destData);
        TestIdentity dest = new TestIdentity(destData);
        int port = 46931;

        NID nid = NodeInfo.generateNID(dest.calculateHash(), port, RandomSource.getInstance());
        NodeInfo ni = new NodeInfo(nid, dest, port);
        assertEquals(NodeInfo.LENGTH, ni.length());
        assertEquals(port, ni.getPort());
        assertEquals(dest.calculateHash(), ni.getHash());
        assertEquals(dest, ni.getDestination());

        // parse back from the 54-byte compact form
        NodeInfo ni2 = new NodeInfo(ni.getData(), 0);
        assertEquals(ni, ni2);
        assertEquals(port, ni2.getPort());
    }

    @Test
    public void testPersistentStringRoundTrip() throws Exception {
        byte[] destData = new byte[387];
        new java.util.Random(7).nextBytes(destData);
        TestIdentity dest = new TestIdentity(destData);
        int port = 49152;
        NID nid = NodeInfo.generateNID(dest.calculateHash(), port, RandomSource.getInstance());

        NodeInfo ni = new NodeInfo(nid, dest, port);
        String s = ni.toPersistentString();

        NodeInfo ni2 = new NodeInfo(s, new TestIdentityFactory());
        assertEquals(ni, ni2);
        assertEquals(dest, ni2.getDestination());
    }

    @Test
    public void testGenerateNIDSecure() {
        byte[] destData = new byte[387];
        new java.util.Random(1).nextBytes(destData);
        TestIdentity dest = new TestIdentity(destData);
        int port = 46931;

        NID nid = NodeInfo.generateNID(dest.calculateHash(), port, RandomSource.getInstance());
        NodeInfo ni = new NodeInfo(nid, dest, port); // verify() must not throw
        assertNotNull(ni.getNID());
        assertEquals(20, nid.length());
    }

    @Test
    public void testNIDLastSeenAndTimeout() {
        NID nid = new NID(new byte[20]);
        assertEquals(0L, nid.lastSeen());
        nid.setLastSeen();
        assertTrue(nid.lastSeen() > 0);
        assertFalse(nid.timeout());
        assertFalse(nid.timeout());
        assertTrue(nid.timeout()); // third failure
    }
}
