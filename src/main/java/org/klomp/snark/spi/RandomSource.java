package org.klomp.snark.spi;

import java.util.Random;

import org.klomp.snark.util.SecureRandomSource;

/**
 *  Abstract random source — replaces {@code net.i2p.util.RandomSource}.
 *
 *  Extends {@link java.util.Random} so ported I2PSnark code that passes
 *  it to {@code Collections.shuffle(...)} or {@code BigInteger(...)}
 *  keeps working unchanged. Adds the I2P-style
 *  {@link #nextBytes(byte[], int, int)} for filling a sub-range.
 *
 *  @since 0.1.0
 */
public abstract class RandomSource extends Random {

    private static final long serialVersionUID = 1L;

    protected RandomSource() {
        super();
    }

    /**
     *  Fill len bytes of the buffer starting at off.
     *  @param buf the buffer to fill
     *  @param off the offset to start at
     *  @param len the number of bytes to fill
     */
    public abstract void nextBytes(byte[] buf, int off, int len);

    @Override
    public void nextBytes(byte[] buf) {
        nextBytes(buf, 0, buf.length);
    }

    /** @return the global random source (defaults to {@link SecureRandomSource}) */
    public static RandomSource getInstance() {
        return Holder.INSTANCE;
    }

    /** Replace the global random source. Null restores the default. */
    public static void setInstance(RandomSource random) {
        Holder.INSTANCE = random != null ? random : new SecureRandomSource();
    }

    /** lazy holder so the default can be swapped at runtime */
    private static final class Holder {
        static volatile RandomSource INSTANCE = new SecureRandomSource();
        private Holder() {}
    }
}
