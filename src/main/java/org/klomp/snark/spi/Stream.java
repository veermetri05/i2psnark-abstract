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

    /** Set the read timeout in milliseconds (0 = infinite) */
    void setReadTimeout(int timeoutMs);

    /** Close the stream (idempotent) */
    void close();

    /** @return true if the stream is closed */
    boolean isClosed();
}
