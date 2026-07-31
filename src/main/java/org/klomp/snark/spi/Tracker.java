package org.klomp.snark.spi;

import java.util.List;

/**
 *  Abstract BitTorrent tracker — replaces
 *  {@code org.klomp.snark.TrackerClient}.
 *
 *  NOT yet used by the metadata downloader (BEP 9/10 needs no
 *  tracker); provided so a future full-client port has its SPI in
 *  place. Trackers over I2P are HTTP(S) reachable via the stream
 *  transport.
 *
 *  @since 0.1.0
 */
public interface Tracker {

    /**
     *  Announce to the tracker.
     *
     *  @param infohash the 20-byte info hash
     *  @param isSeed true if we are seeding
     *  @param maxPeers maximum peers to request
     *  @param timeoutMs maximum time to wait
     *  @return the announced peers (possibly empty, never null)
     *  @throws Exception on tracker failure
     */
    List<PeerIdentity> announce(byte[] infohash, boolean isSeed, int maxPeers, long timeoutMs) throws Exception;

    /**
     *  Scrape torrent statistics.
     *  @param infohash the 20-byte info hash
     *  @param timeoutMs maximum time to wait
     *  @return seeders, leechers, downloads or null if unsupported
     */
    long[] scrape(byte[] infohash, long timeoutMs) throws Exception;
}
