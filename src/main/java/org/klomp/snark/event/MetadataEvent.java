package org.klomp.snark.event;

import org.klomp.snark.MetaInfo;
import org.klomp.snark.spi.PeerIdentity;

/**
 *  A metadata download event — emitted by
 *  {@code org.klomp.snark.MetadataDownloader} for every phase change,
 *  chunk progress, peer failure and final result.
 *
 *  A single download attempt covers one peer; the downloader may
 *  walk through several peers ({@link Phase#PEER_FAILED} between
 *  them). {@link #getAttempt()} is the 1-based peer attempt number.
 *
 *  @since 0.1.0
 */
public class MetadataEvent extends Event<MetadataListener> {

    /** Phases of a single metadata download (per peer attempt). */
    public enum Phase {
        /** resolving peer identity / connecting */
        CONNECTING,
        /** stream connected, starting BT handshake */
        CONNECTED,
        /** BT handshake (BEP 3) completed */
        HANDSHAKE,
        /** BEP 10 extension handshake exchanged, metadata_size known */
        EXTENSION_HANDSHAKE,
        /** downloading metadata chunks (BEP 9) — see progress fields */
        DOWNLOADING,
        /** all chunks received, verifying info hash */
        VERIFYING,
        /** download succeeded — {@link #getMetaInfo()} is set */
        COMPLETE,
        /** this peer attempt failed; the downloader will try the next peer */
        PEER_FAILED,
        /** all peer attempts failed — the overall download failed */
        FAILED
    }

    private final Phase _phase;
    private final byte[] _infohash;
    private final PeerIdentity _peer;
    private final int _attempt;
    private final int _percent;
    private final int _chunksReceived;
    private final int _chunksTotal;
    private final long _bytesReceived;
    private final long _metadataSize;
    private final String _message;
    private final String _error;
    private final long _durationMs;
    private final MetaInfo _metaInfo;

    public MetadataEvent(Phase phase, byte[] infohash, PeerIdentity peer, int attempt,
                         int percent, int chunksReceived, int chunksTotal,
                         long bytesReceived, long metadataSize,
                         String message, String error, long durationMs, MetaInfo metaInfo) {
        _phase = phase;
        _infohash = infohash;
        _peer = peer;
        _attempt = attempt;
        _percent = percent;
        _chunksReceived = chunksReceived;
        _chunksTotal = chunksTotal;
        _bytesReceived = bytesReceived;
        _metadataSize = metadataSize;
        _message = message;
        _error = error;
        _durationMs = durationMs;
        _metaInfo = metaInfo;
    }

    /** @return the download phase */
    public Phase getPhase() {
        return _phase;
    }

    /** @return the 20-byte info hash being downloaded */
    public byte[] getInfohash() {
        return _infohash;
    }

    /** @return the current peer (null if unknown / not yet resolved) */
    public PeerIdentity getPeer() {
        return _peer;
    }

    /** @return the current peer's base64 identity (may be null) */
    public String getPeerBase64() {
        return _peer != null ? _peer.toBase64() : null;
    }

    /** @return 1-based peer attempt number */
    public int getAttempt() {
        return _attempt;
    }

    /** @return overall progress 0-100 for the current peer attempt */
    public int getPercent() {
        return _percent;
    }

    /** @return chunks received so far */
    public int getChunksReceived() {
        return _chunksReceived;
    }

    /** @return total chunks (0 if metadata size unknown) */
    public int getChunksTotal() {
        return _chunksTotal;
    }

    /** @return bytes of metadata received so far */
    public long getBytesReceived() {
        return _bytesReceived;
    }

    /** @return the metadata size in bytes, or -1 if unknown */
    public long getMetadataSize() {
        return _metadataSize;
    }

    /** @return a human-readable status message suitable for a status line */
    public String getMessage() {
        return _message;
    }

    /** @return the error detail for PEER_FAILED / FAILED (else null) */
    public String getError() {
        return _error;
    }

    /** @return elapsed time since this peer attempt started (ms) */
    public long getDurationMs() {
        return _durationMs;
    }

    /** @return the parsed torrent metadata on COMPLETE, else null */
    public MetaInfo getMetaInfo() {
        return _metaInfo;
    }

    /** @return true if this event carries failure information */
    public boolean isFailure() {
        return _phase == Phase.PEER_FAILED || _phase == Phase.FAILED;
    }

    @Override
    public Class<MetadataListener> listenerType() {
        return MetadataListener.class;
    }

    @Override
    public void dispatch(MetadataListener listener) {
        listener.onMetadataEvent(this);
    }
}
