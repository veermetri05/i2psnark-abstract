package org.klomp.snark.event;

/**
 *  One observable change in a torrent's state — the event-driven
 *  refresh source for host UIs (proposal WS-1.3).
 *
 *  Emitted by {@code Snark} at the existing listener call sites
 *  (CoordinatorListener / StorageListener / PeerListener paths).
 *  The app's TorrentInfoProvider can refresh event-first, with its
 *  existing polling interval as a consistency net.
 *
 *  @since 0.2.0
 */
public class TorrentEvent extends Event<TorrentListener> {

    public enum Type {
        /** paused/resumed/seeding/downloading/finished/error state changed */
        STATE_CHANGED,
        /** overall progress changed (pieces/bytes) */
        PROGRESS,
        /** transfer rates changed (throttled by the emitter) */
        SPEED,
        /** piece bitfield changed */
        PIECES_CHANGED,
        /** peer list changed (connected/disconnected/choked/...) */
        PEERS_CHANGED,
        /** tracker list/status changed */
        TRACKERS_CHANGED,
        /** file priorities or received-bytes changed */
        FILES_CHANGED,
        /** magnet metadata acquired (torrent is now fully initialized) */
        METADATA_ADDED,
        /** a storage recheck finished */
        RECHECK_DONE,
        /** a fatal or recoverable error occurred */
        ERROR
    }

    private final Type _type;
    private final String _message;
    /** the torrent's 20-byte info hash (opaque id for the host) */
    private final byte[] _infohash;

    public TorrentEvent(Type type, byte[] infohash, String message) {
        _type = type;
        _infohash = infohash;
        _message = message;
    }

    public Type getType() {
        return _type;
    }

    /** @return the torrent's info hash, or null if not yet known */
    public byte[] getInfoHash() {
        return _infohash;
    }

    /** @return human-readable detail, may be null */
    public String getMessage() {
        return _message;
    }

    @Override
    public Class<TorrentListener> listenerType() {
        return TorrentListener.class;
    }

    @Override
    public void dispatch(TorrentListener listener) {
        listener.onTorrentEvent(this);
    }

    @Override
    public String toString() {
        return "TorrentEvent[" + _type + (_message != null ? ": " + _message : "") + ']';
    }
}
