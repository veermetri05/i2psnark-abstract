import io

SRC_DIR = "C:/Users/VeerMetri/Documents/projects/LibreTorrentI2P/i2p.i2p/apps/i2psnark/java/src/org/klomp/snark"
DST = "C:/Users/VeerMetri/Documents/projects/i2psnark-abstract/src/main/java/org/klomp/snark/PeerCoordinator.java"

s = io.open(SRC_DIR + "/PeerCoordinator.java", encoding="utf-8").read()

# imports
s = s.replace("import net.i2p.I2PAppContext;\n", "")
s = s.replace("import net.i2p.data.ByteArray;", "import org.klomp.snark.data.ByteArray;")
s = s.replace("import net.i2p.data.DataHelper;", "import org.klomp.snark.data.DataHelper;")
s = s.replace("import net.i2p.data.Destination;\n", "")
s = s.replace("import net.i2p.util.ConcurrentHashSet;", "import org.klomp.snark.util.ConcurrentHashSet;")
s = s.replace("import net.i2p.util.I2PAppThread;\n", "")
s = s.replace("import net.i2p.util.Log;", "import org.klomp.snark.spi.Log;\nimport org.klomp.snark.spi.Logs;")
s = s.replace("import net.i2p.util.RandomSource;", "import org.klomp.snark.spi.RandomSource;")
s = s.replace("import net.i2p.util.SimpleTimer2;", "import org.klomp.snark.spi.Cancellable;\nimport org.klomp.snark.spi.Scheduler;")
s = s.replace("import org.klomp.snark.comments.Comment;\n", "")
s = s.replace("import org.klomp.snark.comments.CommentSet;\n", "")
s = s.replace("I2PSnarkUtil", "ClientContext")

# fields
s = s.replace("  private final ClientContext _util;", "  private final ClientContext _ctx;")
s = s.replace("_util.", "_ctx.")
s = s.replace("  private final SimpleTimer2.TimedEvent rerequestTimer;",
              "  private final RerequestEvent rerequestTimer;")
s = s.replace("""  private final AtomicLong _commentsLastRequested = new AtomicLong();
  private final AtomicInteger _commentsNotRequested = new AtomicInteger();
""", "")
s = s.replace("""  private static final long COMMENT_REQ_INTERVAL = 12*60*60*1000L;
  private static final long COMMENT_REQ_DELAY = 60*60*1000L;
  private static final int MAX_COMMENT_NOT_REQ = 10;
""", "")

# ctor
s = s.replace("""    _ctx = ctx;
    _random = ctx.getContext().random();
    _log = ctx.getContext().logManager().getLog(PeerCoordinator.class);""",
              """    _ctx = ctx;
    _random = ctx.random();
    _log = Logs.getLog(PeerCoordinator.class);""")
s = s.replace("""    timer = new CheckEvent(_ctx.getContext(), new PeerCheckerTask(_ctx, this));
    timer.schedule((CHECK_PERIOD / 2) + _random.nextInt((int) CHECK_PERIOD));

    // NOT scheduled until needed
    rerequestTimer = new RerequestEvent();

    // we don't store the last-requested time, so just delay a random amount
    _commentsLastRequested.set(ctx.getContext().clock().now() - (COMMENT_REQ_INTERVAL - _random.nextLong(COMMENT_REQ_DELAY)));""",
              """    timer = new CheckEvent(new PeerCheckerTask(_ctx, this));
    timer.schedule((CHECK_PERIOD / 2) + _random.nextInt((int) CHECK_PERIOD));

    // NOT scheduled until needed
    rerequestTimer = new RerequestEvent();""")

# CheckEvent
s = s.replace("""  /**
   *  Run the PeerCheckerTask via the SimpleTimer2 executors
   *  @since 0.8.2
   */
  private static class CheckEvent extends SimpleTimer2.TimedEvent {
      private final PeerCheckerTask _task;
      public CheckEvent(I2PAppContext ctx, PeerCheckerTask task) {
          super(ctx.simpleTimer2());
          _task = task;
      }
      public void timeReached() {
          _task.run();
          schedule(CHECK_PERIOD);
      }
  }""",
              """  /**
   *  Run the PeerCheckerTask via the scheduler (reschedules itself)
   *  @since 0.8.2
   */
  private static class CheckEvent {
      private final PeerCheckerTask _task;
      private Cancellable _handle;
      public CheckEvent(PeerCheckerTask task) {
          _task = task;
      }
      public void schedule(long delay) {
          _handle = Scheduler.getInstance().schedule(() -> {
              _task.run();
              schedule(CHECK_PERIOD);
          }, delay);
      }
      public void cancel() {
          if (_handle != null)
              _handle.cancel();
      }
  }""")

# RerequestEvent
s = s.replace("""  private class RerequestEvent extends SimpleTimer2.TimedEvent {
      /** caller must schedule */
      public RerequestEvent() {
          super(_ctx.getContext().simpleTimer2());
      }

      public void timeReached() {
          if (bwListener.shouldRequest(null, 0)) {
              if (_log.shouldWarn())
                  _log.warn("Now unthrottled, rerequest timer poking all peers");
              // so shouldRequest() won't fire us up again
              synchronized(rerequestLock) {
                  wasRequestAllowed = true;
              }
              for (Peer p : peers) {
                  if (p.isInteresting() && !p.isChoked())
                      p.request();
              }
              synchronized(rerequestLock) {
                  isRerequestScheduled = false;
              }
          } else {
              if (_log.shouldWarn())
                  _log.warn("Still throttled, rerequest timer reschedule");
              synchronized(rerequestLock) {
                  wasRequestAllowed = false;
              }
              schedule(2*1000);
          }
      }
  }""",
              """  private class RerequestEvent {
      private Cancellable _handle;

      /** caller must schedule */
      public RerequestEvent() {}

      public void schedule(long delay) {
          _handle = Scheduler.getInstance().schedule(() -> {
              if (bwListener.shouldRequest(null, 0)) {
                  if (_log.shouldWarn())
                      _log.warn("Now unthrottled, rerequest timer poking all peers");
                  // so shouldRequest() won't fire us up again
                  synchronized(rerequestLock) {
                      wasRequestAllowed = true;
                  }
                  for (Peer p : peers) {
                      if (p.isInteresting() && !p.isChoked())
                          p.request();
                  }
                  synchronized(rerequestLock) {
                      isRerequestScheduled = false;
                  }
              } else {
                  if (_log.shouldWarn())
                      _log.warn("Still throttled, rerequest timer reschedule");
                  synchronized(rerequestLock) {
                      wasRequestAllowed = false;
                  }
                  schedule(2*1000);
              }
          }, delay);
      }
  }""")

# I2PAppThread -> Thread
s = s.replace("""        String threadName = "Snark peer " + peer.toString();
        new I2PAppThread(r, threadName).start();""",
              """        String threadName = "Snark peer " + peer.toString();
        Thread t = new Thread(r, threadName);
        t.setDaemon(true);
        t.start();""")

# needOutboundPeers: drop comments clause
s = s.replace("""        //return wantedBytes != 0 && needPeers();
        // minus two to make it a little easier for new peers to get in on large swarms
        return (wantedBytes != 0 ||
                (_ctx.utCommentsEnabled() &&
                 // we should also check SnarkManager.getSavedCommentsEnabled() for this torrent,
                 // but that reads in the config file, there's no caching.
                 // TODO
                 _commentsLastRequested.get() < _ctx.getContext().clock().now() - COMMENT_REQ_INTERVAL)) &&
               !halted &&
               peers.size() < getMaxConnections() - 2 &&
               (storage == null || !storage.isChecking());""",
              """        //return wantedBytes != 0 && needPeers();
        // minus two to make it a little easier for new peers to get in on large swarms
        return wantedBytes != 0 &&
               !halted &&
               peers.size() < getMaxConnections() - 2 &&
               (storage == null || !storage.isChecking());""")

# misc ctx accessors
s = s.replace("_ctx.getContext().random()", "_ctx.random()")
s = s.replace("_ctx.getContext().clock().now()", "_ctx.now()")
s = s.replace("_ctx.getContext().simpleTimer2()", "Scheduler.getInstance()")

# sendCommentReq removal (whole method)
start = s.index("  /**\n   *  Send a commment request message to the peer, if he supports it.")
end = s.index("  /**\n   *  PeerListener callback\n   *  Tell the DHT to ping it", start)
s = s[:start] + s[end:]

# gotCommentReq + gotComments removal
start = s.index("  /**\n   * Called when comments are requested via ut_comment")
end = s.index("  /**\n   *  Called by TrackerClient", start)
s = s[:start] + s[end:]

# gotExtension handshake handler: drop comment send
s = s.replace("""          sendDHT(peer);
          if (_ctx.utCommentsEnabled())
              sendCommentReq(peer);
      }""", """          sendDHT(peer);
      }""")

# gotPeers Destination -> PeerIdentity
s = s.replace("Destination myDest = _ctx.getMyDestination();", "PeerIdentity myDest = _ctx.getMyDestination();")

io.open(DST, "w", encoding="utf-8", newline="\n").write(s)
print("ok")
