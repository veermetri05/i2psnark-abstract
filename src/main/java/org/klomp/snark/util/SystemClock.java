package org.klomp.snark.util;

import org.klomp.snark.spi.Clock;

/**
 *  Default {@link Clock} — wall clock time.
 */
public class SystemClock implements Clock {

    @Override
    public long now() {
        return System.currentTimeMillis();
    }
}
