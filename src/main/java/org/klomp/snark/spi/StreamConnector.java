package org.klomp.snark.spi;

import java.io.IOException;

/**
 *  Abstract outbound connection factory — replaces
 *  {@code net.i2p.client.streaming.I2PSocketManager}.
 *
 *  Host applications obtain a connector from their transport
 *  implementation and hand it to {@code MetadataDownloader}.
 *
 *  @since 0.1.0
 */
public interface StreamConnector extends Session {

    /**
     *  Open a streaming connection to the given peer.
     *
     *  @param dest the peer identity (usually obtained from the DHT
     *              and resolved via {@link #lookup(byte[], long)})
     *  @return the connected stream
     *  @throws IOException on connection failure or timeout
     */
    Stream connect(PeerIdentity dest) throws IOException;
}
