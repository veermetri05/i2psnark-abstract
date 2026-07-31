import io
import re

BASE = "C:/Users/VeerMetri/Documents/projects/i2psnark-abstract/src/main/java/org/klomp/snark/"

def load(name):
    return io.open(BASE + name, encoding="utf-8").read()

def save(name, s):
    io.open(BASE + name, "w", encoding="utf-8", newline="\n").write(s)

# 1. SPI Stream: getReadTimeout
p = "spi/Stream.java"
s = load(p)
s = s.replace("""    /** Set the read timeout in milliseconds (0 = infinite) */
    void setReadTimeout(int timeoutMs);""",
              """    /** Set the read timeout in milliseconds (0 = infinite) */
    void setReadTimeout(int timeoutMs);

    /** @return the current read timeout in milliseconds (0 = infinite, default) */
    default int getReadTimeout() {
        return 0;
    }""")
save(p, s)

# 2. ClientContext: getString(String, Object, Object)
p = "ClientContext.java"
s = load(p)
s = s.replace("""    /** {@code getString(key) + ' ' + o} */
    public String getString(String key, Object o) {
        return key + ' ' + o;
    }""",
              """    /** {@code getString(key) + ' ' + o} */
    public String getString(String key, Object o) {
        return key + ' ' + o;
    }

    /** {@code getString(key) + ' ' + o + ' ' + o2} */
    public String getString(String key, Object o, Object o2) {
        return key + ' ' + o + ' ' + o2;
    }""")
save(p, s)

# 3. Peer: close() without checked exception
p = "Peer.java"
s = load(p)
s = s.replace("""    if ( (csock != null) && (!csock.isClosed()) ) {
        try {
            csock.close(); 
        } catch (IOException ioe) { 
            _log.warn("Error disconnecting " + toString(), ioe);
        }
    }""",
              """    if ( (csock != null) && (!csock.isClosed()) ) {
        csock.close();
    }""")
save(p, s)

# 4. PeerAcceptor: read timeout long -> int
p = "PeerAcceptor.java"
s = load(p)
s = s.replace("""        long timeout = socket.getReadTimeout();
        socket.setReadTimeout(HASH_READ_TIMEOUT);""",
              """        int timeout = socket.getReadTimeout();
        socket.setReadTimeout(HASH_READ_TIMEOUT);""")
save(p, s)

# 5. ConnectionAcceptor: I2PException -> IOException
p = "ConnectionAcceptor.java"
s = load(p)
s = s.replace("        catch (I2PException ioe)\n          {", "        catch (IOException ioe)\n          {")
save(p, s)

# 6. TrackerClient: ctx accessors + SnarkManager literal
p = "TrackerClient.java"
s = load(p)
s = s.replace("_ctx.getContext().clock().now()", "_ctx.now()")
s = s.replace("_ctx.getContext().random()", "_ctx.random()")
s = s.replace("SnarkManager.DEFAULT_BACKUP_TRACKER", '"http://opentracker.dg2.i2p/a"')
save(p, s)

# 7. PeerCoordinator: restore setStorage (was cut with the comments block)
p = "PeerCoordinator.java"
s = load(p)
s = s.replace("""  /**
   *  PeerListener callback
   *  Tell the DHT to ping it, this will get back the node info
   *  @param rport must be port + 1
   *  @since 0.8.4
   */
  public void gotPort(Peer peer, int port, int rport) {""",
              """  /**
   *  Snark calls this after we call gotMetaInfo()
   *  @since 0.8.4
   */
  public void setStorage(Storage stg) {
      storage = stg;
      setWantedPieces();
      // ok we should be in business
      for (Peer p : peers) {
          p.setMetaInfo(metainfo);
      }
  }

  /**
   *  PeerListener callback
   *  Tell the DHT to ping it, this will get back the node info
   *  @param rport must be port + 1
   *  @since 0.8.4
   */
  public void gotPort(Peer peer, int port, int rport) {""")
save(p, s)

print("done")
