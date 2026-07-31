package org.klomp.snark.event;

import org.klomp.snark.dht.NodeInfo;

/**
 *  A DHT event — emitted by {@code org.klomp.snark.dht.KRPC} so UIs
 *  can show DHT health, routing-table growth, lookups, bootstrap and
 *  infohash discovery without polling.
 *
 *  @since 0.1.0
 */
public class DhtEvent extends Event<DhtListener> {

    public enum Type {
        /** engine started */
        STARTED,
        /** engine stopped */
        STOPPED,
        /** routing table size changed */
        NODE_COUNT,
        /** a previously unknown node was added to the routing table */
        NEW_NODE,
        /** a get_peers / find_nodes lookup started */
        LOOKUP_START,
        /** a lookup finished */
        LOOKUP_COMPLETE,
        /** bootstrap fetch started */
        BOOTSTRAP_START,
        /** bootstrap fetch finished */
        BOOTSTRAP_COMPLETE,
        /** an announce_peer query was received (infohash discovery) */
        ANNOUNCE_PEER,
        /** a get_peers query was received */
        GET_PEERS,
        /** a find_node query was received */
        FIND_NODE,
        /** announce round finished */
        ANNOUNCE_COMPLETE
    }

    private final Type _type;
    private final String _message;
    private final NodeInfo _node;
    private final int _durationMs;
    private final int _nodeCount;
    private final byte[] _infohash;
    private final byte[] _peerHash;
    private final boolean _isSeed;

    /** General-purpose constructor. */
    public DhtEvent(Type type, String message, NodeInfo node, int durationMs, int nodeCount) {
        this(type, message, node, durationMs, nodeCount, null, null, false);
    }

    /** Constructor for infohash-discovery events (ANNOUNCE_PEER, GET_PEERS, FIND_NODE). */
    public DhtEvent(Type type, String message, NodeInfo node, int durationMs, int nodeCount,
                    byte[] infohash, byte[] peerHash, boolean isSeed) {
        _type = type;
        _message = message;
        _node = node;
        _durationMs = durationMs;
        _nodeCount = nodeCount;
        _infohash = infohash;
        _peerHash = peerHash;
        _isSeed = isSeed;
    }

    public Type getType() {
        return _type;
    }

    /** @return a human-readable description */
    public String getMessage() {
        return _message;
    }

    /** @return the related node (NEW_NODE, queries), may be null */
    public NodeInfo getNode() {
        return _node;
    }

    /** @return elapsed ms for LOOKUP_* / BOOTSTRAP_* events, else -1 */
    public int getDurationMs() {
        return _durationMs;
    }

    /** @return routing table size at event time */
    public int getNodeCount() {
        return _nodeCount;
    }

    /** @return 20-byte info hash for ANNOUNCE_PEER / GET_PEERS, else null */
    public byte[] getInfohash() {
        return _infohash;
    }

    /** @return 32-byte peer hash for ANNOUNCE_PEER, else null */
    public byte[] getPeerHash() {
        return _peerHash;
    }

    /** @return seed flag for ANNOUNCE_PEER */
    public boolean isSeed() {
        return _isSeed;
    }

    @Override
    public Class<DhtListener> listenerType() {
        return DhtListener.class;
    }

    @Override
    public void dispatch(DhtListener listener) {
        listener.onDhtEvent(this);
    }
}
