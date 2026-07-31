package org.klomp.snark.spi;

import org.klomp.snark.util.SystemClock;

/**
 *  Abstract time source — replaces {@code net.i2p.util.Clock}.
 *
 *  I2PSnark code calls {@code Clock.getInstance().now()}; the global
 *  instance can be replaced for tests or to use a monotonic source.
 *
 *  @since 0.1.0
 */
public interface Clock {

    /** @return the current time in milliseconds since epoch */
    long now();

    /** @return the global clock (defaults to {@link SystemClock}) */
    static Clock getInstance() {
        return Holder.INSTANCE;
    }

    /** Replace the global clock. Null restores the default. */
    static void setInstance(Clock clock) {
        Holder.INSTANCE = clock != null ? clock : new SystemClock();
    }

    /** lazy holder so the default can be swapped at runtime */
    final class Holder {
        static volatile Clock INSTANCE = new SystemClock();
        private Holder() {}
    }
}
