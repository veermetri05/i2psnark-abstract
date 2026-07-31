package org.klomp.snark.event;

/**
 *  Receives {@link TorrentEvent}s from the engine.
 *
 *  Implementations MUST be thread-safe and fast: dispatch happens on
 *  the bus's dispatch thread.
 *
 *  @since 0.2.0
 */
public interface TorrentListener {

    void onTorrentEvent(TorrentEvent event);
}
