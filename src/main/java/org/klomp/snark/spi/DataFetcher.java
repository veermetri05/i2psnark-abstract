package org.klomp.snark.spi;

import java.io.IOException;

/**
 *  HTTP GET over the transport — the abstract replacement for
 *  {@code net.i2p.client.streaming.I2PSocketEepGet} /
 *  {@code net.i2p.util.EepGet} (proposal WS-1.1).
 *
 *  Used by TrackerClient (HTTP trackers over I2P) and WebPeer
 *  (web seeds / HTTP serving). Implementations open a stream to the
 *  host, send a GET request, follow redirects, and enforce the size
 *  and timeout limits. No other SPI depends on it.
 *
 *  @since 0.2.0
 */
public interface DataFetcher {

    /**
     *  Fetch a URL over the transport.
     *
     *  @param url the full http://host.i2p/... URL (already rewritten,
     *             see {@code ClientContext.rewriteAnnounce(String)})
     *  @param maxBytes maximum response size in bytes, or 0 for the
     *                  implementation default, or -1 for no limit
     *  @param timeoutMs connect timeout; implementations should apply
     *                   a short timeout when the caller passes a
     *                   non-positive value and a long one otherwise
     *  @param retries number of additional attempts after a failed one
     *  @return the response body, or null on failure
     *  @throws IOException on connection-level failure
     */
    byte[] fetch(String url, int maxBytes, long timeoutMs, int retries) throws IOException;
}
