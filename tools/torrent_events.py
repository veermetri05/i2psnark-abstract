import io

BASE = "C:/Users/VeerMetri/Documents/projects/i2psnark-abstract/src/main/java/org/klomp/snark/"

def load(name):
    return io.open(BASE + name, encoding="utf-8").read()

def save(name, s):
    io.open(BASE + name, "w", encoding="utf-8", newline="\n").write(s)

p = "Snark.java"
s = load(p)

# import
s = s.replace("import org.klomp.snark.spi.Log;",
              "import org.klomp.snark.event.TorrentEvent;\nimport org.klomp.snark.spi.Log;")

# field for SPEED throttle
s = s.replace("""  private long savedUploaded;""",
              """  private long savedUploaded;
  /** throttle SPEED events to one per second */
  private long _lastSpeedEvent;""")

# fire helper + sequential/first-last setters (insert before "Begin StorageListener methods")
s = s.replace("""  ///////////// Begin StorageListener methods""",
              """  /**
   *  Post a TorrentEvent on the shared bus (WS-1.3).
   */
  private void fire(TorrentEvent.Type type, String message) {
      org.klomp.snark.spi.EventBus bus = _ctx != null ? _ctx.getEventBus() : null;
      if (bus != null)
          bus.post(new TorrentEvent(type, infoHash, message));
  }

  /**
   *  Sequential download mode (WS-1.4) — maps to
   *  TorrentDownload.setSequentialDownload() in the app adapter.
   */
  public void setSequentialDownload(boolean sequential) {
      if (coordinator != null)
          coordinator.setPieceSelection(sequential
                  ? ClientContext.PieceSelection.SEQUENTIAL
                  : null);
  }

  /**
   *  First/last piece priority (WS-1.4) — maps to
   *  TorrentDownload.setFirstLastPiecePriority().
   */
  public void setFirstLastPiecePriority(boolean priority) {
      if (coordinator != null)
          coordinator.setPieceSelection(priority
                  ? ClientContext.PieceSelection.FIRST_LAST
                  : null);
  }

  ///////////// Begin StorageListener methods""")

# peerChange -> PEERS_CHANGED
s = s.replace("""  public void peerChange(PeerCoordinator coordinator, Peer peer)
  {
    // System.out.println(peer.toString());
  }""",
              """  public void peerChange(PeerCoordinator coordinator, Peer peer)
  {
    fire(TorrentEvent.Type.PEERS_CHANGED, null);
  }""")

# gotMetaInfo -> METADATA_ADDED (at the end of the method, after meta = metainfo)
s = s.replace("""          // ... so don't set meta until here
          meta = metainfo;
          if (completeListener != null) {
              String newName = completeListener.gotMetaInfo(this);
              if (newName != null)
                  torrent = newName;
              // else some horrible problem
          }
          coordinator.setStorage(storage);""",
              """          // ... so don't set meta until here
          meta = metainfo;
          if (completeListener != null) {
              String newName = completeListener.gotMetaInfo(this);
              if (newName != null)
                  torrent = newName;
              // else some horrible problem
          }
          coordinator.setStorage(storage);
          fire(TorrentEvent.Type.METADATA_ADDED, null);""")

# storageChecked: PIECES_CHANGED on download, PROGRESS on check; throttled SPEED
s = s.replace("""    if (!checking) {
        if (_log.shouldLog(Log.INFO))
            _log.info("Got " + (checked ? "" : "BAD ") + "piece: " + num);
        if (completeListener != null)
            completeListener.gotPiece(this);
    }
  }""",
              """    if (!checking) {
        if (_log.shouldLog(Log.INFO))
            _log.info("Got " + (checked ? "" : "BAD ") + "piece: " + num);
        fire(TorrentEvent.Type.PIECES_CHANGED, null);
        long now = System.currentTimeMillis();
        if (now - _lastSpeedEvent > 1000) {
            _lastSpeedEvent = now;
            fire(TorrentEvent.Type.SPEED, null);
        }
        if (completeListener != null)
            completeListener.gotPiece(this);
    } else {
        fire(TorrentEvent.Type.PROGRESS, "checked " + num);
    }
  }""")

# storageAllChecked -> RECHECK_DONE
s = s.replace("""    allChecked = true;
    checking = false;
    if (storage.isChanged() && completeListener != null) {""",
              """    allChecked = true;
    checking = false;
    fire(TorrentEvent.Type.RECHECK_DONE, null);
    if (storage.isChanged() && completeListener != null) {""")

# storageCompleted -> STATE_CHANGED
s = s.replace("""    if (_log.shouldLog(Log.INFO))
        _log.info("Completely received " + torrent);
    //storage.close();
    //System.out.println("Completely received: " + torrent);
    if (completeListener != null) {""",
              """    if (_log.shouldLog(Log.INFO))
        _log.info("Completely received " + torrent);
    fire(TorrentEvent.Type.STATE_CHANGED, "complete");
    if (completeListener != null) {""")

# setTrackerProblems -> TRACKERS_CHANGED
s = s.replace("""    public void setTrackerProblems(String p) {""",
              """    public void setTrackerProblems(String p) {
        fire(TorrentEvent.Type.TRACKERS_CHANGED, p);""")

# fatalRouter -> ERROR
s = s.replace("""  private void fatalRouter(String s, Throwable t) throws RouterException {
    _log.error(s, t);
    System.out.println(s);
    stopTorrent(true);""",
              """  private void fatalRouter(String s, Throwable t) throws RouterException {
    _log.error(s, t);
    System.out.println(s);
    fire(TorrentEvent.Type.ERROR, s);
    stopTorrent(true);""")

# fatal(String, Throwable) -> ERROR (check the method)
s = s.replace("""  private void fatal(String s, Throwable t) {
    _log.error(s, t);
    System.out.println(s);
    stopTorrent(true);""",
              """  private void fatal(String s, Throwable t) {
    _log.error(s, t);
    System.out.println(s);
    fire(TorrentEvent.Type.ERROR, s);
    stopTorrent(true);""")

save(p, s)
print("done")
