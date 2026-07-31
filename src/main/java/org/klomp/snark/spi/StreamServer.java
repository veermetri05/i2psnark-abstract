package org.klomp.snark.spi;

import java.io.IOException;

/**
 *  Abstract inbound connection acceptor — replaces
 *  {@code net.i2p.client.streaming.I2PServerSocket}.
 *
 *  Not used by the metadata downloader; provided so a future
 *  full-client port (serving torrents) has its SPI in place.
 *
 *  @since 0.1.0
 */
public interface StreamServer extends Session {

    /**
     *  Accept the next inbound connection (blocking).
     *  @throws IOException on accept failure or if closed
     */
    Stream accept() throws IOException;

    /** Close the server (idempotent) */
    void close();
}
