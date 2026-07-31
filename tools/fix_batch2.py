import io
import re

BASE = "C:/Users/VeerMetri/Documents/projects/i2psnark-abstract/src/main/java/org/klomp/snark/"

def load(name):
    return io.open(BASE + name, encoding="utf-8").read()

def save(name, s):
    io.open(BASE + name, "w", encoding="utf-8", newline="\n").write(s)

# 1. ClientContext: connect(PeerID) + imports
p = "ClientContext.java"
s = load(p)
s = s.replace("import org.klomp.snark.spi.DataFetcher;",
              "import org.klomp.snark.spi.DataFetcher;\nimport org.klomp.snark.spi.DatagramTransportFactory;\nimport org.klomp.snark.spi.Stream;")
s = s.replace("    /** @return true if the transport session is up */\n    public boolean connected() {",
              """    /**
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
    public boolean connected() {""")
save(p, s)

# 2. PeerCoordinatorSet import
p = "PeerCoordinatorSet.java"
s = load(p)
s = s.replace("import net.i2p.crypto.SHA1Hash;", "import org.klomp.snark.data.SHA1Hash;")
save(p, s)

# 3. Storage: I2PSnarkUtil -> ClientContext
p = "Storage.java"
s = load(p)
s = s.replace("I2PSnarkUtil", "ClientContext")
s = s.replace("  private final ClientContext _util;", "  private final ClientContext _ctx;")
s = s.replace("_util.", "_ctx.")
s = re.sub(r"public Storage\(ClientContext \w+, ", "public Storage(ClientContext ctx, ", s)
s = re.sub(r"this\(util, ", "this(ctx, ", s)
s = s.replace("_ctx = util;", "_ctx = ctx;")
s = s.replace("SnarkManager.PROP_MAX_FILES_PER_TORRENT + '=' + sz + \" in \" +\n                                  SnarkManager.CONFIG_FILE + \" and restart\");",
              "\"i2psnark.maxFilesPerTorrent\" + '=' + sz + \" in \" +\n                                  \"i2psnark.config\" + \" and restart\");")
save(p, s)

# 4. Peer: runConnection(ClientContext ctx) + usages
p = "Peer.java"
s = load(p)
s = s.replace("import net.i2p.util.Log;", "import org.klomp.snark.spi.Log;\nimport org.klomp.snark.spi.Logs;")
s = s.replace("public void runConnection(I2PSnarkUtil util, PeerListener listener, BandwidthListener bwl, BitField bitfield,",
              "public void runConnection(ClientContext ctx, PeerListener listener, BandwidthListener bwl, BitField bitfield,")
s = s.replace("sock = util.connect(peerID);", "sock = ctx.connect(peerID);")
s = s.replace("""            boolean pexAndMetadata = metainfo == null || !metainfo.isPrivate();
            boolean dht = util.getDHT() != null;
            boolean comment = util.utCommentsEnabled();
            out.sendExtension(0, ExtensionHandler.getHandshake(metasize, pexAndMetadata, dht, uploadOnly, comment));""",
              """            boolean pexAndMetadata = metainfo == null || !metainfo.isPrivate();
            boolean dht = ctx.getDHT() != null;
            out.sendExtension(0, ExtensionHandler.getHandshake(metasize, pexAndMetadata, dht, uploadOnly));""")
s = s.replace("connected = util.getContext().clock().now();", "connected = ctx.now();")
save(p, s)

# 5. PeerCoordinator: reschedule + import
p = "PeerCoordinator.java"
s = load(p)
s = s.replace("import org.klomp.snark.spi.Log;", "import org.klomp.snark.spi.Log;\nimport org.klomp.snark.spi.PeerIdentity;")
s = s.replace("""      public void schedule(long delay) {
          _handle = Scheduler.getInstance().schedule(() -> {
              if (bwListener.shouldRequest(null, 0)) {""",
              """      public void schedule(long delay) {
          if (_handle != null)
              _handle.cancel();
          _handle = Scheduler.getInstance().schedule(() -> {
              if (bwListener.shouldRequest(null, 0)) {""")
s = s.replace("""          }, delay);
      }
  }""",
              """          }, delay);
      }

      public void reschedule(long delay) {
          schedule(delay);
      }
  }""")
save(p, s)

# 6. ConnectionAcceptor ctors + reschedule
p = "ConnectionAcceptor.java"
s = load(p)
s = s.replace("""  public ConnectionAcceptor(ClientContext util, PeerCoordinatorSet set) {
      _ctx = ctx;
      _cleaner = new Cleaner();
      peeracceptor = new PeerAcceptor(set);
  }""",
              """  public ConnectionAcceptor(ClientContext ctx, PeerCoordinatorSet set) {
      _ctx = ctx;
      peeracceptor = new PeerAcceptor(set);
      _cleaner = org.klomp.snark.spi.Scheduler.getInstance().schedulePeriodic(this::cleanBad, BAD_CLEAN_INTERVAL, BAD_CLEAN_INTERVAL);
  }""")
s = s.replace("""  public ConnectionAcceptor(ClientContext util,
                            PeerAcceptor peeracceptor)
  {
    this.peeracceptor = peeracceptor;
    _ctx = ctx;
    
    thread = new Thread(this, "I2PSnark acceptor");
    thread.setDaemon(true);
    thread.start();
    _cleaner = new Cleaner();
  }""",
              """  public ConnectionAcceptor(ClientContext ctx,
                            PeerAcceptor peeracceptor)
  {
    this.peeracceptor = peeracceptor;
    _ctx = ctx;
    
    thread = new Thread(this, "I2PSnark acceptor");
    thread.setDaemon(true);
    thread.start();
    _cleaner = org.klomp.snark.spi.Scheduler.getInstance().schedulePeriodic(this::cleanBad, BAD_CLEAN_INTERVAL, BAD_CLEAN_INTERVAL);
  }""")
s = s.replace("          _cleaner.reschedule(BAD_CLEAN_INTERVAL, false);\n", "")
save(p, s)

# 7. UDPTrackerClient: drop leftover import, port ReplyWaiter
p = "UDPTrackerClient.java"
s = load(p)
s = s.replace("import net.i2p.client.datagram.I2PInvalidDatagramException;\n", "")
s = s.replace("    private class ReplyWaiter extends SimpleTimer2.TimedEvent {\n        private final int tid;",
              "    private class ReplyWaiter {\n        private final int tid;")
s = s.replace("""        public ReplyWaiter(int tid, Tracker tracker, int action, byte[] payload, long toWait) {
            super(SimpleTimer2.getInstance(), toWait);
            this.tid = tid;""",
              """        private org.klomp.snark.spi.Cancellable _handle;

        public ReplyWaiter(int tid, Tracker tracker, int action, byte[] payload, long toWait) {
            this.tid = tid;""")
s = s.replace("""        public synchronized void gotReply(boolean success) {
            cancel();""",
              """        public synchronized void gotReply(boolean success) {
            cancelTimer();""")
s = s.replace("""        @Override
        public synchronized void schedule(long toWait) {
            state = WaitState.INIT;
            super.schedule(toWait);
        }""",
              """        public synchronized void schedule(long toWait) {
            state = WaitState.INIT;
            if (_handle != null)
                _handle.cancel();
            _handle = org.klomp.snark.spi.Scheduler.getInstance().schedule(() -> timeReached(), toWait);
        }

        public synchronized void cancelTimer() {
            if (_handle != null) {
                _handle.cancel();
                _handle = null;
            }
        }""")
save(p, s)

# 8. Snark: this(util..), makeID random, fatalRouter, imports
p = "Snark.java"
s = load(p)
s = s.replace("      this(util, torrent, ih, trackerURL, complistener, peerCoordinatorSet, connectionAcceptor, rootDir);",
              "      this(ctx, torrent, ih, trackerURL, complistener, peerCoordinatorSet, connectionAcceptor, rootDir);")
s = s.replace("_ctx.random().nextBytes(rv, 12, 8);", "RandomSource.getInstance().nextBytes(rv, 12, 8);")
s = s.replace("""    _log.error(s, t);
    if (!_ctx.getContext().isRouterContext())
        System.out.println(s);
    stopTorrent(true);""",
              """    _log.error(s, t);
    System.out.println(s);
    stopTorrent(true);""")
s = s.replace("import org.klomp.snark.spi.Log;", "import org.klomp.snark.spi.Log;\nimport org.klomp.snark.spi.RandomSource;")
save(p, s)

# 9. MetaInfo.checkPiece(PartialPiece) — WS-1.2 increment
p = "MetaInfo.java"
s = load(p)
s = s.rstrip()[:-1]
s += """
  /**
   *  Verify a partial piece against the expected SHA-1 hash —
   *  restored from upstream (WS-1.2).
   *
   *  @param pp the partial piece, must be complete
   *  @return true if the hash matches
   */
  public boolean checkPiece(PartialPiece pp) {
    int piece = pp.getPiece();
    byte[] hash;
    try {
        hash = pp.getHash();
    } catch (java.io.IOException ioe) {
        // could be caused by closing a peer connection
        org.klomp.snark.spi.Logs.getLog(MetaInfo.class).warn("Error checking", ioe);
        return false;
    }
    byte[] hashes = getPieceHashes();
    if (hashes == null || piece < 0 || piece >= getPieces())
        return false;
    for (int i = 0; i < 20; i++) {
        if (hash[i] != hashes[20 * piece + i])
            return false;
    }
    return true;
  }
}
"""
save(p, s)
print("done")
