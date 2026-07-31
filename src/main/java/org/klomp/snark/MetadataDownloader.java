package org.klomp.snark;

/**
 * MetadataDownloader - Downloads .torrent metadata from a peer via
 *                      BEP 9 (ut_metadata) and BEP 10 (Extension Protocol).
 *
 * Given a peer identity and a 20-byte infohash, it:
 *   1. Connects via the abstract stream transport
 *   2. Performs the BitTorrent handshake (BEP 3)
 *   3. Exchanges the extension handshake (BEP 10), learns metadata_size
 *   4. Requests and assembles all metadata chunks (BEP 9)
 *   5. Returns the assembled MetaInfo
 *
 * This is the I2PSnark protocol logic with ZERO transport dependencies:
 * the caller supplies a {@link org.klomp.snark.spi.StreamConnector}
 * (I2P, SAM, loopback, tests...).
 *
 * Progress is delivered through {@link MetadataListener} events — no
 * polling. UIs may also register a global listener on an
 * {@link org.klomp.snark.spi.EventBus} via {@link #toBus}.
 *
 * Usage:
 *   MetaInfo meta = MetadataDownloader.download(connector, dest, infohash, listener);
 */
import org.klomp.snark.bencode.BDecoder;
import org.klomp.snark.bencode.BEncoder;
import org.klomp.snark.bencode.BEValue;
import org.klomp.snark.data.DataHelper;
import org.klomp.snark.event.MetadataEvent;
import org.klomp.snark.event.MetadataListener;
import org.klomp.snark.spi.EventBus;
import org.klomp.snark.spi.Log;
import org.klomp.snark.spi.Logs;
import org.klomp.snark.spi.PeerIdentity;
import org.klomp.snark.spi.RandomSource;
import org.klomp.snark.spi.Stream;
import org.klomp.snark.spi.StreamConnector;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class MetadataDownloader {

    private static final Log _log = Logs.getLog(MetadataDownloader.class);

    // BT protocol constants (matching I2PSnark's Message.java)
    private static final byte ID_EXTENSION = 20;
    // BEP 10 extension IDs
    private static final int EXT_HANDSHAKE = 0;
    private static final int EXT_METADATA = 1;
    // BEP 9 metadata message types
    private static final int META_REQUEST = 0;
    private static final int META_DATA = 1;
    private static final int META_REJECT = 2;
    // BEP 9 chunk size (must match MagnetState.CHUNK_SIZE)
    private static final int CHUNK_SIZE = 16 * 1024;
    // Timeouts
    public static final int DEFAULT_HANDSHAKE_TIMEOUT = 30_000;
    public static final int DEFAULT_READ_TIMEOUT = 120_000;
    // Sanity limit for metadata size (4MB)
    public static final int MAX_METADATA_SIZE = 4 * 1024 * 1024;
    // BitTorrent listen port we advertise in the extension handshake
    // (informational only — metadata exchange does not use it)
    public static final int PORT = 6881;
    // Reserved bytes: set bit 20 (byte[5] bit 4 = 0x10) for BEP 10
    private static final byte[] RESERVED = new byte[8];
    static {
        RESERVED[5] = 0x10;
    }

    // ---- Public API ----

    /**
     * Download metadata from a peer.
     *
     * @param connector the stream transport (I2P socket manager or equivalent)
     * @param dest      the peer identity (from DHT lookup)
     * @param infohash  20-byte info hash to download metadata for
     * @return the parsed MetaInfo
     * @throws IOException on any connection, protocol, or timeout error
     */
    public static MetaInfo download(StreamConnector connector, PeerIdentity dest,
                                    byte[] infohash) throws IOException {
        return download(connector, dest, infohash, null);
    }

    /**
     * Download metadata from a peer with event reporting.
     *
     * @param connector the stream transport
     * @param dest      the peer identity
     * @param infohash  20-byte info hash
     * @param listener  optional event listener (may be null)
     * @return the parsed MetaInfo
     * @throws IOException on any connection, protocol, or timeout error
     */
    public static MetaInfo download(StreamConnector connector, PeerIdentity dest,
                                    byte[] infohash, MetadataListener listener) throws IOException {
        return download(connector, dest, infohash, DEFAULT_HANDSHAKE_TIMEOUT, DEFAULT_READ_TIMEOUT, listener);
    }

    /**
     * Download metadata from a peer with event reporting and custom timeouts.
     */
    public static MetaInfo download(StreamConnector connector, PeerIdentity dest,
                                    byte[] infohash, int handshakeTimeoutMs, int readTimeoutMs,
                                    MetadataListener listener) throws IOException {
        if (infohash == null || infohash.length != 20)
            throw new IllegalArgumentException("infohash must be 20 bytes");
        if (dest == null)
            throw new IllegalArgumentException("dest must not be null");
        if (connector == null)
            throw new IllegalArgumentException("connector must not be null");

        long start = System.currentTimeMillis();
        fire(listener, new MetadataEvent(MetadataEvent.Phase.CONNECTING, infohash, dest, 1,
                0, 0, 0, 0, -1, "Connecting to peer...", null, 0, null));

        // 1. Connect to peer via the stream transport
        Stream socket;
        try {
            socket = connector.connect(dest);
        } catch (IOException ie) {
            String msg = "Failed to connect to peer: " + ie.getMessage();
            fire(listener, new MetadataEvent(MetadataEvent.Phase.PEER_FAILED, infohash, dest, 1,
                    0, 0, 0, 0, -1, msg, msg, elapsed(start), null));
            throw new IOException(msg, ie);
        }
        if (socket == null || socket.isClosed()) {
            String msg = "Failed to connect to peer: null/closed stream";
            fire(listener, new MetadataEvent(MetadataEvent.Phase.PEER_FAILED, infohash, dest, 1,
                    0, 0, 0, 0, -1, msg, msg, elapsed(start), null));
            throw new IOException(msg);
        }

        fire(listener, new MetadataEvent(MetadataEvent.Phase.CONNECTED, infohash, dest, 1,
                0, 0, 0, 0, -1, "Connected", null, elapsed(start), null));

        try {
            return doDownload(socket, infohash, handshakeTimeoutMs, readTimeoutMs, listener, start);
        } catch (IOException e) {
            fire(listener, new MetadataEvent(MetadataEvent.Phase.PEER_FAILED, infohash, dest, 1,
                    0, 0, 0, 0, -1, e.getMessage(), e.getMessage(), elapsed(start), null));
            throw e;
        } catch (Exception e) {
            IOException ioe = new IOException("Metadata download failed: " + e.getMessage(), e);
            fire(listener, new MetadataEvent(MetadataEvent.Phase.PEER_FAILED, infohash, dest, 1,
                    0, 0, 0, 0, -1, ioe.getMessage(), ioe.getMessage(), elapsed(start), null));
            throw ioe;
        } finally {
            try {
                socket.close();
            } catch (RuntimeException e) {
                _log.warn("Error closing stream", e);
            }
        }
    }

    /**
     * Try a list of peers in order until one yields the metadata.
     * Emits {@link MetadataEvent.Phase#PEER_FAILED} between attempts and
     * {@link MetadataEvent.Phase#FAILED} if all peers fail.
     *
     * @return the parsed MetaInfo
     * @throws IOException if ALL peers failed (last error)
     */
    public static MetaInfo downloadFromPeers(StreamConnector connector, List<PeerIdentity> peers,
                                             byte[] infohash, MetadataListener listener) throws IOException {
        return downloadFromPeers(connector, peers, infohash,
                DEFAULT_HANDSHAKE_TIMEOUT, DEFAULT_READ_TIMEOUT, listener);
    }

    /**
     * Try a list of peers in order with custom timeouts.
     */
    public static MetaInfo downloadFromPeers(StreamConnector connector, List<PeerIdentity> peers,
                                             byte[] infohash, int handshakeTimeoutMs, int readTimeoutMs,
                                             MetadataListener listener) throws IOException {
        if (peers == null || peers.isEmpty())
            throw new IOException("No peers to try");
        IOException lastError = null;
        int attempt = 0;
        for (PeerIdentity peer : peers) {
            attempt++;
            try {
                return download(connector, peer, infohash, handshakeTimeoutMs, readTimeoutMs, listener);
            } catch (IOException e) {
                lastError = e;
                // PEER_FAILED was already emitted by download()
            }
        }
        String msg = "All " + peers.size() + " peers failed" +
                     (lastError != null ? ": " + lastError.getMessage() : "");
        fire(listener, new MetadataEvent(MetadataEvent.Phase.FAILED, infohash, null, attempt,
                0, 0, 0, 0, -1, msg, msg, 0, null));
        throw lastError != null ? lastError : new IOException(msg);
    }

    /**
     * Adapter: forward MetadataEvents to an {@link EventBus} for
     * global (UI-wide) listeners.
     */
    public static MetadataListener toBus(final EventBus bus) {
        if (bus == null)
            return null;
        return new MetadataListener() {
            @Override
            public void onMetadataEvent(MetadataEvent event) {
                bus.post(event);
            }
        };
    }

    private static void fire(MetadataListener listener, MetadataEvent event) {
        if (listener != null) {
            try {
                listener.onMetadataEvent(event);
            } catch (RuntimeException e) {
                _log.warn("Metadata listener error", e);
            }
        }
    }

    private static long elapsed(long start) {
        return System.currentTimeMillis() - start;
    }

    // ---- Core download logic ----

    private static MetaInfo doDownload(Stream socket, byte[] infohash,
                                       int handshakeTimeoutMs, int readTimeoutMs,
                                       MetadataListener listener, long start) throws Exception {
        DataInputStream din = new DataInputStream(socket.getInputStream());
        DataOutputStream dout = new DataOutputStream(socket.getOutputStream());

        // 2. BT handshake (BEP 3) — format matches Peer.handshake() in I2PSnark
        doHandshake(din, dout, infohash, handshakeTimeoutMs);
        fire(listener, new MetadataEvent(MetadataEvent.Phase.HANDSHAKE, infohash, null, 1,
                0, 0, 0, 0, -1, "BT handshake OK", null, elapsed(start), null));

        // 3. Send BEP 10 extension handshake
        sendExtensionHandshake(dout, -1);

        // 4. Receive peer's extension handshake → learn metadata_size + peer's ut_metadata msg ID
        HandshakeResult hs = recvExtensionHandshake(din, handshakeTimeoutMs);
        int peerMetaMsgId = hs.metaMsgId;
        int metadataSize = hs.metadataSize;
        fire(listener, new MetadataEvent(MetadataEvent.Phase.EXTENSION_HANDSHAKE, infohash, null, 1,
                0, 0, 0, 0, metadataSize,
                "Peer supports ut_metadata, metadata_size=" + metadataSize, null, elapsed(start), null));

        if (metadataSize <= 0 || metadataSize > MAX_METADATA_SIZE)
            throw new IOException("Invalid or missing metadata_size: " + metadataSize);

        // 5. Initialize download state
        MetadataState state = new MetadataState(infohash, metadataSize);

        // 6. Send initial batch of requests (up to 3 parallel, matching I2PSnark)
        int maxOutstanding = Math.min(3, state.chunksRemaining());
        for (int i = 0; i < maxOutstanding; i++) {
            int chunk = state.getNextRequest();
            sendRequest(dout, peerMetaMsgId, chunk);
        }

        // 7. Read messages in a loop until metadata is complete
        long deadline = System.currentTimeMillis() + Math.max(readTimeoutMs, metadataSize / 1024 * 1000);
        int extMsgCount = 0;

        while (!state.isComplete() && System.currentTimeMillis() < deadline) {
            int length;
            try {
                length = din.readInt();
            } catch (EOFException eof) {
                throw new IOException("Peer disconnected during metadata download");
            }

            if (length == 0) {
                // Keep-alive — ignore
                continue;
            }
            if (length < 0 || length > MAX_METADATA_SIZE + 100)
                throw new IOException("Invalid message length: " + length);

            int msgId = din.readUnsignedByte();
            int payloadLen = length - 1;

            switch (msgId) {
                case 0: // CHOKE
                case 1: // UNCHOKE
                    if (payloadLen > 0) din.skipBytes(payloadLen);
                    break;

                case ID_EXTENSION: {
                    if (payloadLen < 1)
                        throw new IOException("Truncated extension message");
                    int extId = din.readUnsignedByte();
                    payloadLen--;
                    byte[] extPayload = new byte[payloadLen];
                    if (payloadLen > 0)
                        din.readFully(extPayload);

                    if (extId == EXT_HANDSHAKE) {
                        // duplicate extension handshake, ignore
                        continue;
                    }
                    if (extId != EXT_METADATA) {
                        // unknown extension id, ignore
                        continue;
                    }

                    // BEP 9 metadata message
                    extMsgCount++;
                    handleMetadataData(state, extPayload, peerMetaMsgId, dout, listener, start);

                    // If completed, break
                    if (state.isComplete()) {
                        break;
                    }

                    // Request next chunk if we have room
                    if (state.chunksRemaining() > 0) {
                        int chunk = state.getNextRequest();
                        sendRequest(dout, peerMetaMsgId, chunk);
                    }
                    break;
                }

                default:
                    // Unknown message type — skip payload
                    if (payloadLen > 0)
                        din.skipBytes(payloadLen);
                    break;
            }
        }

        if (!state.isComplete()) {
            throw new IOException("Metadata download incomplete after timeout " +
                                 "(" + state.chunksRemaining() + " chunks remaining of " +
                                 state.totalChunks + ")");
        }

        // 8. Verify assembled info dict
        fire(listener, new MetadataEvent(MetadataEvent.Phase.VERIFYING, infohash, null, 1,
                100, state.totalChunks, state.totalChunks, metadataSize, metadataSize,
                "Verifying info hash...", null, elapsed(start), null));
        MetaInfo meta = state.getMetaInfo();
        String msg = "Downloaded: " + meta.getName() + " (" + meta.getInfoBytesLength() + " bytes)";
        fire(listener, new MetadataEvent(MetadataEvent.Phase.COMPLETE, infohash, null, 1,
                100, state.totalChunks, state.totalChunks, metadataSize, metadataSize,
                msg, null, elapsed(start), meta));
        return meta;
    }

    // ========== BT Handshake (BEP 3) ==========

    private static void doHandshake(DataInputStream din, DataOutputStream dout,
                                    byte[] infohash, int handshakeTimeoutMs) throws IOException {
        byte[] peerId = generatePeerId();

        // Write handshake (matches Peer.handshake() in I2PSnark)
        dout.writeByte(19);
        dout.write(DataHelper.getASCII("BitTorrent protocol"));
        dout.write(RESERVED);
        dout.write(infohash);
        dout.write(peerId);
        dout.flush();

        // Read handshake
        byte b = din.readByte();
        if (b != 19)
            throw new IOException("Handshake failed: expected 19, got " + (b & 0xff));

        byte[] protocol = new byte[19];
        din.readFully(protocol);
        if (!DataHelper.eq(protocol, DataHelper.getASCII("BitTorrent protocol")))
            throw new IOException("Handshake failed: bad protocol string");

        byte[] reserved = new byte[8];
        din.readFully(reserved);

        byte[] theirHash = new byte[20];
        din.readFully(theirHash);
        if (!DataHelper.eq(theirHash, infohash))
            throw new IOException("Handshake failed: infohash mismatch");

        byte[] theirPeerId = new byte[20];
        din.readFully(theirPeerId);

        // Verify extension support (bit 20 in reserved bytes)
        if ((reserved[5] & 0x10) == 0)
            throw new IOException("Peer does not support extension protocol (BEP 10)");
    }

    // ========== BEP 10 Extension Protocol ==========

    /**
     * Send the BEP 10 extension handshake.
     * Matches ExtensionHandler.getHandshake() in I2PSnark.
     */
    private static void sendExtensionHandshake(DataOutputStream dout, int metadataSize) throws IOException {
        Map<String, Object> m = new HashMap<String, Object>();
        m.put("ut_metadata", Integer.valueOf(EXT_METADATA));

        Map<String, Object> handshake = new HashMap<String, Object>();
        handshake.put("m", m);
        if (metadataSize >= 0)
            handshake.put("metadata_size", Integer.valueOf(metadataSize));
        handshake.put("p", Integer.valueOf(PORT));
        handshake.put("v", "I2PSnark");
        handshake.put("reqq", Integer.valueOf(8));

        byte[] payload = BEncoder.bencode(handshake);
        sendExtensionMsg(dout, EXT_HANDSHAKE, payload);
    }

    /** Result of parsing the peer's extension handshake. */
    private static class HandshakeResult {
        final int metaMsgId;
        final int metadataSize;
        HandshakeResult(int metaMsgId, int metadataSize) {
            this.metaMsgId = metaMsgId;
            this.metadataSize = metadataSize;
        }
    }

    /**
     * Receive and parse the peer's extension handshake.
     * Reads messages until we get the handshake or timeout.
     */
    private static HandshakeResult recvExtensionHandshake(DataInputStream din, int handshakeTimeoutMs) throws IOException {
        long deadline = System.currentTimeMillis() + handshakeTimeoutMs;

        while (System.currentTimeMillis() < deadline) {
            int length = din.readInt();
            if (length == 0)
                continue; // keep-alive
            if (length < 0)
                throw new IOException("Invalid message length");

            int msgId = din.readUnsignedByte();
            int payloadLen = length - 1;

            if (msgId == ID_EXTENSION && payloadLen >= 1) {
                int extId = din.readUnsignedByte();
                payloadLen--;
                byte[] payload = new byte[payloadLen];
                if (payloadLen > 0)
                    din.readFully(payload);

                if (extId == EXT_HANDSHAKE) {
                    ByteArrayInputStream bais = new ByteArrayInputStream(payload);
                    BDecoder dec = new BDecoder(bais);
                    BEValue bev = dec.bdecodeMap();
                    Map<String, BEValue> map = bev.getMap();

                    // Get the 'm' map to find ut_metadata msg ID
                    BEValue mVal = map.get("m");
                    if (mVal == null)
                        throw new IOException("Peer handshake missing 'm' map");
                    Map<String, BEValue> extMap = mVal.getMap();
                    BEValue metaIdVal = extMap.get("ut_metadata");
                    if (metaIdVal == null)
                        throw new IOException("Peer does not support ut_metadata");
                    int msgIdForMeta = metaIdVal.getInt();

                    // Get metadata_size
                    BEValue sizeVal = map.get("metadata_size");
                    int metaSize = sizeVal != null ? sizeVal.getInt() : -1;

                    return new HandshakeResult(msgIdForMeta, metaSize);
                }
            } else {
                // Non-extension or non-handshake — skip
                if (payloadLen > 0)
                    din.skipBytes(payloadLen);
            }
        }
        throw new IOException("Timeout waiting for extension handshake from peer");
    }

    // ========== BEP 9 Metadata Exchange ==========

    /**
     * Handle an incoming BEP 9 metadata DATA or REJECT message.
     */
    private static void handleMetadataData(MetadataState state, byte[] msgBytes,
                                            int peerMetaMsgId, DataOutputStream dout,
                                            MetadataListener listener, long start) throws Exception {
        // The payload is a bencoded dict, possibly followed by raw chunk data
        ByteArrayInputStream bais = new ByteArrayInputStream(msgBytes);
        BDecoder dec = new BDecoder(bais);
        BEValue bev = dec.bdecodeMap();
        Map<String, BEValue> map = bev.getMap();

        int msgType = map.get("msg_type").getInt();
        int piece = map.get("piece").getInt();

        if (msgType == META_REJECT) {
            throw new IOException("Peer rejected metadata chunk " + piece);
        }

        if (msgType != META_DATA) {
            if (_log.shouldLog(Log.WARN))
                _log.warn("Unknown metadata msg_type=" + msgType + " from peer");
            return;
        }

        // The remaining bytes after the dict are the raw chunk data
        int dictLen = msgBytes.length - bais.available();
        int dataLen = msgBytes.length - dictLen;
        if (dataLen <= 0) {
            throw new IOException("Empty metadata data chunk");
        }

        byte[] chunkData = new byte[dataLen];
        System.arraycopy(msgBytes, dictLen, chunkData, 0, dataLen);

        boolean done = state.saveChunk(piece, chunkData);
        int received = state.chunksReceived();
        int pct = received * 100 / state.totalChunks;
        String msg = "Downloaded chunk " + received + "/" + state.totalChunks +
                     " (" + state.bytesReceived() + " bytes)";
        fire(listener, new MetadataEvent(MetadataEvent.Phase.DOWNLOADING, state.infohash(), null, 1,
                pct, received, state.totalChunks, state.bytesReceived(), state.totalSize,
                msg, null, elapsed(start), null));
        if (done && _log.shouldInfo())
            _log.info("Got chunk " + piece + " (" + dataLen + " bytes) -- COMPLETE!");
    }

    /**
     * Send a BEP 9 metadata request for a specific chunk.
     */
    private static void sendRequest(DataOutputStream dout, int peerMetaMsgId, int chunk) throws IOException {
        Map<String, Object> req = new HashMap<String, Object>();
        req.put("msg_type", Integer.valueOf(META_REQUEST));
        req.put("piece", Integer.valueOf(chunk));
        byte[] payload = BEncoder.bencode(req);
        sendExtensionMsg(dout, peerMetaMsgId, payload);
    }

    // ========== Wire Format Helpers ==========

    /**
     * Send a BT EXTENSION message (ID=20).
     * Wire format (matching Message.sendMessage for EXTENSION):
     *   4-byte length (1 + 1 + payload.length)
     *   1-byte message ID = 20
     *   1-byte extension sub-ID
     *   payload bytes
     */
    private static void sendExtensionMsg(DataOutputStream dout, int extMsgId, byte[] payload) throws IOException {
        dout.writeInt(1 + 1 + payload.length); // msgId(1) + extId(1) + payload
        dout.writeByte(ID_EXTENSION & 0xFF);
        dout.writeByte(extMsgId & 0xFF);
        dout.write(payload);
        dout.flush();
    }

    // ========== Peer ID Generation ==========

    /**
     * Generate a 20-byte peer ID.
     * Matches I2PSnark's format (from Snark.java): 9 zeros, 3 bytes of '3', 8 random bytes.
     */
    private static byte[] generatePeerId() {
        byte[] rv = new byte[20];
        rv[9] = 3;
        rv[10] = 3;
        rv[11] = 3;
        try {
            RandomSource.getInstance().nextBytes(rv, 12, 8);
        } catch (RuntimeException rse) {
            new Random().nextBytes(rv);
        }
        return rv;
    }

    // ========== Metadata Download State ==========

    /**
     * Tracks the state of a metadata download — replaces I2PSnark's
     * package-private MagnetState class.
     */
    private static class MetadataState {
        private final byte[] infohash;
        private final int totalSize;
        private final int totalChunks;
        private final boolean[] have;
        private final boolean[] requested;
        private final byte[] assembled;
        private boolean complete;
        private MetaInfo metainfo;

        MetadataState(byte[] infohash, int totalSize) {
            this.infohash = infohash;
            this.totalSize = totalSize;
            this.totalChunks = (totalSize + CHUNK_SIZE - 1) / CHUNK_SIZE;
            this.have = new boolean[totalChunks];
            this.requested = new boolean[totalChunks];
            this.assembled = new byte[totalSize];
        }

        byte[] infohash() {
            return infohash;
        }

        int chunksRemaining() {
            int remaining = 0;
            for (boolean b : have) { if (!b) remaining++; }
            return remaining;
        }

        int chunksReceived() {
            int received = 0;
            for (boolean b : have) { if (b) received++; }
            return received;
        }

        long bytesReceived() {
            return chunksReceived() * (long) CHUNK_SIZE;
        }

        boolean isComplete() {
            return complete;
        }

        /**
         * Get the next chunk to request (randomized start to avoid thundering herd).
         */
        synchronized int getNextRequest() {
            if (complete)
                throw new IllegalStateException("already complete");
            Random rnd = new Random();
            int start = rnd.nextInt(totalChunks);
            for (int i = 0; i < totalChunks; i++) {
                int chk = (start + i) % totalChunks;
                if (!have[chk] && !requested[chk]) {
                    requested[chk] = true;
                    return chk;
                }
            }
            // End game: all requested, request any missing
            for (int i = 0; i < totalChunks; i++) {
                if (!have[i])
                    return i;
            }
            throw new IllegalStateException("complete");
        }

        /**
         * Save a received chunk.
         * @return true if this was the last chunk
         */
        synchronized boolean saveChunk(int chunk, byte[] data) throws Exception {
            if (complete)
                return true;
            if (chunk < 0 || chunk >= totalChunks)
                throw new IllegalArgumentException("bad chunk " + chunk);
            if (have[chunk])
                return false;

            int offset = chunk * CHUNK_SIZE;
            int size = Math.min(CHUNK_SIZE, totalSize - offset);
            if (data.length != size)
                throw new IllegalArgumentException("chunk " + chunk + " bad length " + data.length + " != " + size);

            System.arraycopy(data, 0, assembled, offset, size);
            have[chunk] = true;

            // Check if all chunks received
            boolean done = true;
            for (boolean b : have) { if (!b) { done = false; break; } }

            if (done) {
                metainfo = buildMetaInfo();
                complete = true;
            }
            return done;
        }

        /**
         * Build the MetaInfo from assembled info dict bytes.
         * Verifies the infohash matches.
         */
        private MetaInfo buildMetaInfo() throws Exception {
            Map<String, BEValue> top = new HashMap<String, BEValue>();
            ByteArrayInputStream bais = new ByteArrayInputStream(assembled);
            BDecoder dec = new BDecoder(bais);
            BEValue infoValue = dec.bdecodeMap();
            top.put("info", infoValue);

            MetaInfo meta = new MetaInfo(top);
            if (!DataHelper.eq(meta.getInfoHash(), infohash)) {
                // Hash mismatch — reset and retry
                Arrays.fill(have, false);
                Arrays.fill(requested, false);
                throw new IOException("info hash mismatch after metadata assembly");
            }
            return meta;
        }

        MetaInfo getMetaInfo() {
            if (!complete)
                throw new IllegalStateException("not complete");
            return metainfo;
        }
    }
}
