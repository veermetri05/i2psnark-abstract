package org.klomp.snark.spi;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 *  Abstract bidirectional byte stream — replaces
 *  {@code net.i2p.client.streaming.I2PSocket}.
 *
 *  @since 0.1.0
 */
public interface Stream {

    /** @return the input stream (null if the stream is closed) */
    InputStream getInputStream();

    /** @return the output stream (null if the stream is closed) */
    OutputStream getOutputStream();

    /**
     *  Set the read timeout in milliseconds.
     *  <p><b>SPI contract: 0 = infinite (block forever).</b> Implementations
     *  wrapping libraries with different conventions (e.g. net.i2p, where
     *  0 = non-blocking and -1 = infinite) MUST translate so the contract
     *  holds for callers, and {@link #getReadTimeout()} MUST return the
     *  value in SPI terms (0 = infinite).
     */
    void setReadTimeout(int timeoutMs);

    /** @return the current read timeout in milliseconds (0 = infinite, default) */
    default int getReadTimeout() {
        return 0;
    }

    /**
     *  @return the remote peer identity, or null if unknown
     *          (implementations wrapping I2PSocket return the peer
     *          destination; loopback transports may return null)
     */
    default PeerIdentity getPeer() {
        return null;
    }

    /**
     *  @return the local port of this stream (I2P port), or 0 if
     *          unknown — used by the full client to detect web seeds
     *          (port 80)
     */
    default int getLocalPort() {
        return 0;
    }

    /** Close the stream (idempotent) */
    void close();

    /** @return true if the stream is closed */
    boolean isClosed();
}
