package org.klomp.snark;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

import org.klomp.snark.data.Base32;
import org.klomp.snark.dht.DHT;
import org.klomp.snark.spi.DataFetcher;
import org.klomp.snark.spi.DatagramTransportFactory;
import org.klomp.snark.spi.Stream;
import org.klomp.snark.spi.Environment;
import org.klomp.snark.spi.EventBus;
import org.klomp.snark.spi.Log;
import org.klomp.snark.spi.Logs;
import org.klomp.snark.spi.PeerIdentity;
import org.klomp.snark.spi.PeerIdentityFactory;
import org.klomp.snark.spi.RandomSource;
import org.klomp.snark.spi.StreamConnector;
import org.klomp.snark.spi.StreamServer;

/**
 *  The engine's central hub — the abstract replacement for
 *  {@code I2PSnarkUtil} (proposal WS-1.1 / Appendix B.4).
 *
 *  Owns everything the full client needs that is not per-torrent:
 *  <ul>
 *    <li>the {@link Environment} (clock, randomness, timers, storage, logs)</li>
 *    <li>the {@link StreamConnector} / {@link StreamServer} the host
 *        application's transport provides (already connected)</li>
 *    <li>the {@link PeerIdentityFactory} (deserializing identities from
 *        the DHT persistence file / HTTP bootstrap)</li>
 *    <li>the {@link EventBus} (TorrentEvents, DhtEvents, ConnectionEvents)</li>
 *    <li>the I2P DHT ({@link DHT}) once started</li>
 *    <li>global configuration (bandwidth, connection limits, temp dir,
 *        trackers, UDP tracker flag)</li>
 *    <li>name resolution: b32 → {@link PeerIdentity} via the connector's
 *        session lookup; base64 → {@link PeerIdentity} via the factory</li>
 *  </ul>
 *
 *  The host application constructs one ClientContext per engine session
 *  and hands it to the session adapter; {@code Snark}s receive it and
 *  never touch the transport directly.
 *
 *  Constants keep the upstream values (I2PSnarkUtil / Snark /
 *  TrackerClient) so the ported code behaves identically.
 */
public class ClientContext {

    /** BitTorrent-over-I2P convention port (TrackerClient.PORT) */
    public static final int PORT = 6881;
    /** Outbound request size in bytes (PeerState.PARTSIZE) */
    public static final int PARTSIZE = 16 * 1024;
    /** Per-torrent connection limit (I2PSnarkUtil.MAX_CONNECTIONS) */
    public static final int MAX_CONNECTIONS = 24;
    /** Global upload slot limit (Snark.MAX_TOTAL_UPLOADERS) */
    public static final int MAX_TOTAL_UPLOADERS = 20;
    /** Default global upload cap, KBps (SnarkManager.DEFAULT_MAX_UP_BW) */
    public static final int DEFAULT_MAX_UP_BW = 25 * 1024;
    /** Default global download cap, KBps (SnarkManager.DEFAULT_MAX_DOWN_BW) */
    public static final int DEFAULT_MAX_DOWN_BW = 200 * 1024;
    /** HTTP-over-I2P user agent (I2PSnarkUtil.EEPGET_USER_AGENT) */
    public static final String EEPGET_USER_AGENT = "I2PSnark";
    /** DHT on by default (I2PSnarkUtil.DEFAULT_USE_DHT) */
    public static final boolean DEFAULT_USE_DHT = true;
    /** max files per torrent (SnarkManager.DEFAULT_MAX_FILES_PER_TORRENT) */
    public static final int DEFAULT_MAX_FILES_PER_TORRENT = 10;

    /** 1 + Hash.HASH_LENGTH * 8 / 5 — length of a b32 hash without suffix */
    private static final int BASE32_HASH_LENGTH = 52;

    private final Environment _environment;
    private final StreamConnector _connector;
    private final StreamServer _server;
    private final PeerIdentityFactory _identityFactory;
    private final EventBus _eventBus;
    private final File _tempDir;

    // config (defaults as upstream)
    private int _maxConnections = MAX_CONNECTIONS;
    private int _maxUploaders = MAX_TOTAL_UPLOADERS;
    private int _maxUpBW = DEFAULT_MAX_UP_BW;
    private int _maxFilesPerTorrent = DEFAULT_MAX_FILES_PER_TORRENT;
    private boolean _areFilesPublic;
    private boolean _udpEnabled = true;
    private boolean _useDHT = DEFAULT_USE_DHT;
    private List<String> _openTrackers = Collections.emptyList();
    private List<String> _backupTrackers = Collections.emptyList();

    // set once the DHT is started (Snark.start → startDHT)
    private volatile DHT _dht;

    // set by the host application (TrackerClient / WebPeer HTTP)
    private volatile DataFetcher _dataFetcher;

    // set by the host application (UDP tracker client, extra DHT nodes)
    private volatile DatagramTransportFactory _datagramTransportFactory;

    // created by the engine when UDP trackers are enabled (Snark.start)
    private volatile UDPTrackerClient _udpTracker;

    /**
     *  @param environment the engine environment (never null)
     *  @param connector outbound streams, already connected (never null)
     *  @param server inbound accepts (never null)
     *  @param identityFactory identity deserializer (never null)
     *  @param eventBus shared event bus (never null)
     *  @param tempDir directory for partial-piece temp files (never null)
     */
    public ClientContext(Environment environment, StreamConnector connector,
                         StreamServer server, PeerIdentityFactory identityFactory,
                         EventBus eventBus, File tempDir) {
        if (environment == null)
            throw new IllegalArgumentException("environment");
        if (connector == null)
            throw new IllegalArgumentException("connector");
        if (server == null)
            throw new IllegalArgumentException("server");
        if (identityFactory == null)
            throw new IllegalArgumentException("identityFactory");
        if (eventBus == null)
            throw new IllegalArgumentException("eventBus");
        if (tempDir == null)
            throw new IllegalArgumentException("tempDir");
        _environment = environment;
        _connector = connector;
        _server = server;
        _identityFactory = identityFactory;
        _eventBus = eventBus;
        _tempDir = tempDir;
    }

    // ── Environment access ───────────────────────────────────────────

    /** @return the engine environment */
    public Environment getEnvironment() {
        return _environment;
    }

    /** @return the current time in ms */
    public long now() {
        return _environment.clock().now();
    }

    /** @return the random source */
    public RandomSource random() {
        return _environment.random();
    }

    /** @return a Log for the given class */
    public Log log(Class<?> clazz) {
        return Logs.getLog(clazz);
    }

    /** @return the shared event bus */
    public EventBus getEventBus() {
        return _eventBus;
    }

    // ── Connectivity (owned by the host transport) ───────────────────

    /** @return the outbound stream connector */
    public StreamConnector getStreamConnector() {
        return _connector;
    }

    /** @return the inbound stream server */
    public StreamServer getStreamServer() {
        return _server;
    }

    /** @return true if the transport is up (replaces I2PSnarkUtil.connect()) */
    public boolean connect() {
        return connected();
    }

    /**
     *  Connect to the given peer (replaces I2PSnarkUtil.connect(PeerID)).
     *  The I2P banlist is not ported (transport-level concern).
     *
     *  @throws IOException on failure
     */
    public Stream connect(PeerID peer) throws IOException {
        if (_connector == null)
            throw new IOException("No stream connector");
        PeerIdentity addr = peer.getAddress();
        if (addr == null)
            throw new IOException("Null address");
        if (addr.equals(getMyDestination()))
            throw new IOException("Attempt to connect to myself");
        return _connector.connect(addr);
    }

    /** @return true if the transport session is up */
    public boolean connected() {
        return _connector != null && !_connector.isClosed();
    }

    /** @return true while a (re)connect is in progress — always false here; the transport owns it */
    public boolean isConnecting() {
        return false;
    }

    /** No-op: the transport lifecycle is owned by the host application */
    public void disconnect() {
    }

    /** @return true once the transport is configured */
    public boolean configured() {
        return _connector != null && _server != null;
    }

    /** @return our own identity, or null if not connected */
    public PeerIdentity getMyDestination() {
        if (!connected())
            return null;
        return _connector.getLocalIdentity();
    }

    /** @return our own destination as base64, or "unknown" */
    public String getOurIPString() {
        PeerIdentity id = getMyDestination();
        return id != null ? id.toBase64() : "unknown";
    }

    // ── Name resolution ──────────────────────────────────────────────

    /**
     *  Resolve a host name to a peer identity (replaces
     *  {@code I2PSnarkUtil.getDestination(String)}):
     *  <ul>
     *    <li>base32 + ".b32.i2p" → session lookup (efficient, primary)</li>
     *    <li>base64 key + ".i2p" (len &gt;= 520) → factory parse</li>
     *    <li>plain base64 → factory parse</li>
     *    <li>other ".i2p" names (naming service) → unsupported, null</li>
     *  </ul>
     */
    public PeerIdentity getDestination(String ip) {
        if (ip == null)
            return null;
        if (ip.endsWith(".i2p")) {
            if (ip.length() < 520) {
                if (ip.length() == BASE32_HASH_LENGTH + 8 && ip.endsWith(".b32.i2p")) {
                    // Use the existing session for b32 lookups — much more
                    // efficient than a naming service
                    byte[] b = Base32.decode(ip.substring(0, BASE32_HASH_LENGTH));
                    if (b != null) {
                        try {
                            return _connector.lookup(b, 15 * 1000);
                        } catch (IOException ise) {
                            log(ClientContext.class).warn("b32 lookup failed for " + ip, ise);
                        }
                    }
                }
                log(ClientContext.class).info("No naming service for " + ip);
                return null;
            }
            // base64 key + ".i2p"
            return getDestinationFromBase64(ip.substring(0, ip.length() - 4));
        }
        return getDestinationFromBase64(ip);
    }

    /**
     *  Parse a base64 destination (no naming service) — replaces
     *  {@code I2PSnarkUtil.getDestinationFromBase64(String)}.
     *
     *  @return the identity or null on bad data
     */
    public PeerIdentity getDestinationFromBase64(String ip) {
        if (ip == null)
            return null;
        try {
            return _identityFactory.fromBase64(ip);
        } catch (IllegalArgumentException dfe) {
            return null;
        }
    }

    /** @return the identity factory */
    public PeerIdentityFactory getIdentityFactory() {
        return _identityFactory;
    }

    // ── HTTP over I2P (trackers, web seeds) ──────────────────────────

    /** @return the HTTP fetcher, or null if the host app did not provide one */
    public DataFetcher getDataFetcher() {
        return _dataFetcher;
    }

    /** Set by the host application; null disables HTTP trackers/web seeds */
    public void setDataFetcher(DataFetcher fetcher) {
        _dataFetcher = fetcher;
    }

    /** @return the datagram transport factory, or null if not provided */
    public DatagramTransportFactory getDatagramTransportFactory() {
        return _datagramTransportFactory;
    }

    /** Set by the host application; null disables UDP trackers */
    public void setDatagramTransportFactory(DatagramTransportFactory factory) {
        _datagramTransportFactory = factory;
    }

    /** @return the UDP tracker client, or null if not started/disabled */
    public UDPTrackerClient getUDPTrackerClient() {
        return _udpTracker;
    }

    /** Called once by the engine when UDP trackers start */
    public void setUDPTrackerClient(UDPTrackerClient udpTracker) {
        _udpTracker = udpTracker;
    }

    /**
     *  Rewrite an old-style announce URL (replaces
     *  {@code I2PSnarkUtil.rewriteAnnounce(String)}):
     *  <pre>
     *    http://KEY.i2p/foo/announce  →  http://i2p/KEY/foo/announce
     *    http://tracker.blah.i2p/foo  →  unchanged
     *  </pre>
     */
    public static String rewriteAnnounce(String origAnnounce) {
        int destStart = "http://".length();
        int destEnd = origAnnounce.indexOf(".i2p");
        if (destEnd < destStart + 516)
            return origAnnounce;
        int pathStart = origAnnounce.indexOf('/', destEnd);
        return "http://i2p/" + origAnnounce.substring(destStart, destEnd)
                + origAnnounce.substring(pathStart);
    }

    // ── DHT ──────────────────────────────────────────────────────────

    /** @return the DHT, or null if not started/disabled */
    public DHT getDHT() {
        return _dht;
    }

    /** Called once by the engine when the DHT starts */
    public void setDHT(DHT dht) {
        _dht = dht;
    }

    /** @return whether the DHT should be used (config) */
    public boolean shouldUseDHT() {
        return _useDHT;
    }

    /** Configure DHT usage */
    public void setUseDHT(boolean yes) {
        _useDHT = yes;
    }

    // ── Config ───────────────────────────────────────────────────────

    /** @return per-torrent max connections */
    public int getMaxConnections() {
        return _maxConnections;
    }

    public void setMaxConnections(int limit) {
        _maxConnections = Math.max(limit, 1);
    }

    /** @return global max upload slots */
    public int getMaxUploaders() {
        return _maxUploaders;
    }

    public void setMaxUploaders(int limit) {
        _maxUploaders = Math.max(limit, 1);
    }

    /** @return global upload cap in Bps */
    public int getMaxUpBW() {
        return _maxUpBW;
    }

    public void setMaxUpBW(int limit) {
        _maxUpBW = Math.max(limit, 1);
    }

    /** @return max files per torrent */
    public int getMaxFilesPerTorrent() {
        return _maxFilesPerTorrent;
    }

    public void setMaxFilesPerTorrent(int max) {
        _maxFilesPerTorrent = Math.max(max, 1);
    }

    /** @return whether files are served publicly (WebPeer); false in the abstract layer */
    public boolean getFilesPublic() {
        return _areFilesPublic;
    }

    public void setFilesPublic(boolean yes) {
        _areFilesPublic = yes;
    }

    /** @return whether UDP (datagram) trackers are enabled */
    public boolean udpEnabled() {
        return _udpEnabled;
    }

    public void setUDPEnabled(boolean yes) {
        _udpEnabled = yes;
    }

    /** @return the directory for partial-piece temp files */
    public File getTempDir() {
        return _tempDir;
    }

    /** @return open (well-known) tracker URLs to add to new torrents */
    public List<String> getOpenTrackers() {
        return _openTrackers;
    }

    public void setOpenTrackers(List<String> ot) {
        _openTrackers = ot != null ? ot : Collections.<String>emptyList();
    }

    /** @return backup tracker URLs (e.g. from the UI) */
    public List<String> getBackupTrackers() {
        return _backupTrackers;
    }

    public void setBackupTrackers(List<String> bt) {
        _backupTrackers = bt != null ? bt : Collections.<String>emptyList();
    }

    /**
     *  UI strings — the abstract layer is UI-free, so the key itself
     *  is returned; host apps may map keys to localized text.
     */
    public String getString(String key) {
        return key;
    }

    /** {@code getString(key) + ' ' + o} */
    public String getString(String key, Object o) {
        return key + ' ' + o;
    }

    /** {@code getString(key) + ' ' + o + ' ' + o2} */
    public String getString(String key, Object o, Object o2) {
        return key + ' ' + o + ' ' + o2;
    }

    @Override
    public String toString() {
        return "ClientContext[connected=" + connected()
                + ", maxConnections=" + _maxConnections
                + ", maxUploaders=" + _maxUploaders
                + ", maxUpBW=" + _maxUpBW + ']';
    }
}
