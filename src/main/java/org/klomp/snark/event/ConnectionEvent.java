package org.klomp.snark.event;

/**
 *  Connection lifecycle event — posted by the host application's
 *  transport layer (I2P session connect/disconnect/reconnect).
 *
 *  The core library has no connection lifecycle of its own; this
 *  event exists so UIs can render a connection status indicator
 *  (like the Android app's "I2CP connection status" bar) purely from
 *  events.
 *
 *  @since 0.1.0
 */
public class ConnectionEvent extends Event<ConnectionListener> {

    public enum State {
        /** attempting to establish a session */
        CONNECTING,
        /** session established */
        CONNECTED,
        /** session lost */
        DISCONNECTED,
        /** session lost, retrying with backoff */
        RECONNECTING
    }

    private final State _state;
    private final String _message;
    private final String _localIdentityBase64;
    private final int _reconnectAttempt;

    public ConnectionEvent(State state, String message, String localIdentityBase64, int reconnectAttempt) {
        _state = state;
        _message = message;
        _localIdentityBase64 = localIdentityBase64;
        _reconnectAttempt = reconnectAttempt;
    }

    public State getState() {
        return _state;
    }

    /** @return human-readable detail (e.g. failure reason) */
    public String getMessage() {
        return _message;
    }

    /** @return our own identity base64 on CONNECTED, else null */
    public String getLocalIdentityBase64() {
        return _localIdentityBase64;
    }

    /** @return reconnect attempt number for RECONNECTING, else 0 */
    public int getReconnectAttempt() {
        return _reconnectAttempt;
    }

    @Override
    public Class<ConnectionListener> listenerType() {
        return ConnectionListener.class;
    }

    @Override
    public void dispatch(ConnectionListener listener) {
        listener.onConnectionEvent(this);
    }
}
