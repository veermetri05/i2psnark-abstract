package org.klomp.snark.util;

import org.junit.Test;
import static org.junit.Assert.*;

import org.klomp.snark.data.Base32;
import org.klomp.snark.data.ByteArray;
import org.klomp.snark.data.DataHelper;
import org.klomp.snark.spi.Clock;
import org.klomp.snark.spi.Environment;
import org.klomp.snark.spi.Storage;

import java.io.File;
import java.nio.charset.StandardCharsets;

/**
 *  Tests for the ported util layer: Base32 decode, ByteCache,
 *  SyntheticREDQueue.
 */
public class FullClientUtilTest {

    @Test
    public void base32RoundTrip() {
        byte[] data = "The quick brown fox jumps over the lazy dog".getBytes(StandardCharsets.UTF_8);
        assertEquals(Base32.encode(data), Base32.encode(Base32.decode(Base32.encode(data))));
    }

    @Test
    public void base32KnownVector() {
        // RFC 4648 test vectors (lowercase, unpadded as I2P encodes)
        assertEquals("my", Base32.encode("f".getBytes(StandardCharsets.UTF_8)));
        assertEquals("mzxq", Base32.encode("fo".getBytes(StandardCharsets.UTF_8)));
        assertEquals("mzxw6", Base32.encode("foo".getBytes(StandardCharsets.UTF_8)));
        assertEquals("mzxw6yq", Base32.encode("foob".getBytes(StandardCharsets.UTF_8)));
        assertEquals("mzxw6ytb", Base32.encode("fooba".getBytes(StandardCharsets.UTF_8)));
        assertEquals("mzxw6ytboi", Base32.encode("foobar".getBytes(StandardCharsets.UTF_8)));
        // and decode back
        assertArrayEquals("foobar".getBytes(StandardCharsets.UTF_8),
                          Base32.decode("mzxw6ytboi"));
    }

    @Test
    public void base32RejectsInvalid() {
        assertNull(Base32.decode("1a"));          // '1' is below '2' → bad char
        assertNull(Base32.decode("AAAAAAA!"));    // '!' is below '2' → bad char
    }

    @Test
    public void base32B32HostnameRoundTrip() {
        byte[] hash = new byte[32];
        for (int i = 0; i < 32; i++)
            hash[i] = (byte) i;
        String b32 = Base32.encode(hash);
        assertEquals(52, b32.length());
        assertArrayEquals(hash, Base32.decode(b32));
    }

    @Test
    public void byteCacheReusesBuffers() {
        ByteCache cache = ByteCache.getInstance(4, 1024);
        ByteArray a = cache.acquire();
        assertEquals(1024, a.getData().length);
        a.getData()[0] = 0x55;
        cache.release(a, false);
        ByteArray b = cache.acquire();
        assertSame("released buffer should be reused", a, b);
        // release with zeroing clears the data
        b.getData()[0] = 0x55;
        cache.release(b, true);
        ByteArray c = cache.acquire();
        assertEquals(0, c.getData()[0]);
    }

    @Test
    public void byteCacheRejectsWrongSize() {
        ByteCache cache = ByteCache.getInstance(2, 512);
        ByteArray big = new ByteArray(new byte[1024]);
        cache.release(big, false); // must not go into the 512 cache
        ByteArray a = cache.acquire();
        assertEquals(512, a.getData().length);
    }

    @Test
    public void syntheticREDQueueDropsOverLimit() {
        // RED is time-based: the queue builds only after WESTWOOD_RTT_MIN
        // (500 ms) windows, so drive a fake clock.
        FakeClock clock = new FakeClock();
        Clock.setInstance(clock);
        try {
            SyntheticREDQueue q = new SyntheticREDQueue(Environment.basic(dummyStorage()), 100_000);
            // 16 KB per 200 ms = 80 KB/s < 100 KB/s limit → all admitted
            for (int i = 0; i < 20; i++) {
                clock.advance(200);
                assertTrue("under limit, sample " + i, q.offer(16 * 1024, 1.0f));
            }
            // 16 KB per 10 ms = 1.6 MB/s → queue exceeds maxth → drops
            boolean dropped = false;
            for (int i = 0; i < 200 && !dropped; i++) {
                clock.advance(10);
                if (!q.offer(16 * 1024, 1.0f))
                    dropped = true;
            }
            assertTrue("should drop under sustained overload", dropped);
        } finally {
            Clock.setInstance(null);
        }
    }

    /** test clock with manually advanced time */
    static class FakeClock implements Clock {
        long t;
        @Override public long now() { return t; }
        void advance(long ms) { t += ms; }
    }

    @Test
    public void syntheticREDQueueUnconditionalSamples() {
        SyntheticREDQueue q = new SyntheticREDQueue(Environment.basic(dummyStorage()), 10_000);
        // addSample never drops
        for (int i = 0; i < 100; i++)
            q.addSample(1024);
        assertTrue(q.getBandwidthEstimate() >= 0);
    }

    private static Storage dummyStorage() {
        return new Storage() {
            @Override public String getConfigDir() { return ""; }
            @Override public boolean exists(String name) { return false; }
            @Override public java.io.InputStream open(String name) { return null; }
            @Override public java.io.OutputStream create(String name) { return null; }
            @Override public boolean delete(String name) { return false; }
        };
    }

    @Test
    public void dataHelperHashCode() {
        byte[] a = DataHelper.getASCII("abc");
        byte[] b = DataHelper.getASCII("abc");
        byte[] c = DataHelper.getASCII("abd");
        assertEquals(DataHelper.hashCode(a), DataHelper.hashCode(b));
        assertFalse(DataHelper.hashCode(a) == DataHelper.hashCode(c));
        // deterministic known value
        assertEquals(96354, DataHelper.hashCode(a));
    }
}
