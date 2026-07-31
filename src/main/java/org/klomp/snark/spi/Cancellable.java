package org.klomp.snark.spi;

/**
 *  Handle for a scheduled task — replaces
 *  {@code net.i2p.util.SimpleTimer2.TimedEvent}.
 *
 *  @since 0.1.0
 */
public interface Cancellable {

    /** Cancel the task. No-op if it already ran or was cancelled. */
    void cancel();
}
