package org.klomp.snark.event;

/**
 *  Listener for {@link DhtEvent}s — DHT health, routing table size,
 *  lookups, bootstrap and infohash discovery, pushed without polling.
 *
 *  Callbacks arrive on the event bus dispatch thread; keep them fast.
 *
 *  @since 0.1.0
 */
public interface DhtListener {

    /** A DHT event occurred. */
    void onDhtEvent(DhtEvent event);
}
