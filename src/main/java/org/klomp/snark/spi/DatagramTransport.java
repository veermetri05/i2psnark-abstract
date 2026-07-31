package org.klomp.snark.spi;

/**
 *  Abstract datagram transport — replaces the I2P datagram parts of
 *  {@code net.i2p.client.I2PSession} (PROTO_DATAGRAM /
 *  PROTO_DATAGRAM_RAW) plus the I2PDatagramMaker/Dissector framing.
 *
 *  The implementation owns:
 *  <ul>
 *    <li>the two local ports (query port for signed datagrams,
 *        response port = query port + 1 for raw datagrams)</li>
 *    <li>datagram signing/verification (I2P: I2PDatagramMaker /
 *        I2PDatagramDissector)</li>
 *    <li>transport options (expiration, crypto tags, gzip)</li>
 *  </ul>
 *
 *  The DHT (KRPC) only sees bencoded payloads.
 *
 *  @since 0.1.0
 */
public interface DatagramTransport extends Session {

    /**
     *  Register the datagram receiver. Pass null to stop receiving.
     *  The implementation delivers messages destined for either of
     *  its local ports to this listener.
     */
    void setDatagramListener(DatagramListener listener);

    /**
     *  Send a datagram.
     *
     *  @param dest the destination identity
     *  @param toPort the remote port (query port for queries,
     *                response port for replies/announces)
     *  @param payload the payload bytes (bencoded KRPC message)
     *  @param signed true to send a repliable (signed) datagram on
     *                our query port, false for a raw datagram
     *  @return true on success
     */
    boolean send(PeerIdentity dest, int toPort, byte[] payload, boolean signed);

    /** Close the transport and stop receiving (idempotent) */
    void close();
}
