package org.klomp.snark.dht;

/**
 * DHTEventListener - Callback interface for I2P DHT events.
 *
 * Registered with a KRPC instance to receive notifications of
 * incoming DHT queries and routing table changes. This is the
 * foundation for passive infohash discovery.
 *
 * All callbacks are invoked from the I2P message receiver thread.
 * Implementations MUST be thread-safe and non-blocking (or at
 * least minimal-blocking) to avoid delaying DHT message processing.
 */
import org.klomp.snark.data.Hash;

public interface DHTEventListener {

    /**
     * Called when an announce_peer query is received.
     * This means another node IS sharing the given infohash —
     * the highest-confidence signal of an active torrent.
     *
     * @param infohashBytes  the 20-byte info hash (raw bytes) being announced
     * @param peerHash       the 32-byte destination hash of the announcing peer (may be null)
     * @param isSeed         true if announcing as a seed
     * @param source         the NodeInfo of the announcing node (may be null for unsigned)
     */
    void onAnnouncePeer(byte[] infohashBytes, Hash peerHash, boolean isSeed, NodeInfo source);

    /**
     * Called when a get_peers query is received.
     * Another node is searching for this infohash.
     *
     * @param infohashBytes  the 20-byte info hash (raw bytes) being looked up
     * @param source         the NodeInfo of the querying node (may be null)
     */
    void onGetPeers(byte[] infohashBytes, NodeInfo source);

    /**
     * Called when a find_node query is received.
     * The target might be an infohash if someone is searching
     * for peers for a torrent.
     *
     * @param targetBytes  the 20-byte target NID (raw bytes) being searched for
     * @param source       the NodeInfo of the querying node (may be null)
     */
    void onFindNode(byte[] targetBytes, NodeInfo source);

    /**
     * Called when a previously unknown node is added to the
     * routing table. Used for building a persistent node pool.
     *
     * @param node  the newly discovered NodeInfo
     */
    void onNewNode(NodeInfo node);
}
