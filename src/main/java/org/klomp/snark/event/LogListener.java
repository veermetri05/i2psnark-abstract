package org.klomp.snark.event;

/**
 *  Listener for {@link LogEvent}s — feeds UI log screens.
 *
 *  @since 0.1.0
 */
public interface LogListener {

    /** A log record was produced. */
    void onLogEvent(LogEvent event);
}
