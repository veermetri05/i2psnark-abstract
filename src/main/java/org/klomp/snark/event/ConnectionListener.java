package org.klomp.snark.event;

/**
 *  Listener for {@link ConnectionEvent}s.
 *
 *  @since 0.1.0
 */
public interface ConnectionListener {

    /** A connection state change occurred. */
    void onConnectionEvent(ConnectionEvent event);
}
