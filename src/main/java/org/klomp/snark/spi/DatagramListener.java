package org.klomp.snark.spi;

/**
 *  Receives datagrams from a {@link DatagramTransport}.
 *
 *  Implementations MUST be thread-safe and non-blocking: callbacks
 *  arrive on the transport's receiver thread and delaying them
 *  delays all DHT message processing.
 *
 *  @since 0.1.0
 */
public interface DatagramListener {

    /**
     *  A datagram was received.
     *
     *  @param from the sender identity, or null for unsigned
     *              (raw) datagrams where the sender is unknown
     *  @param fromPort the sender's source port (0 if unknown)
     *  @param payload the datagram payload (already unwrapped from
     *                 any transport-level signature framing)
     *  @param signed true if the datagram was repliable (signed)
     */
    void onDatagram(PeerIdentity from, int fromPort, byte[] payload, boolean signed);

    /**
     *  The underlying session closed. The DHT should stop.
     *  Default: no-op.
     */
    default void onTransportClosed() {}

    /**
     *  The underlying session reported an error.
     *  Default: no-op.
     */
    default void onTransportError(String message, Throwable error) {}
}
