import io
import re

BASE = "C:/Users/VeerMetri/Documents/projects/i2psnark-abstract/src/main/java/org/klomp/snark/"
SRC = "C:/Users/VeerMetri/Documents/projects/LibreTorrentI2P/i2p.i2p/apps/i2psnark/java/src/org/klomp/snark/"

def load(name, base=BASE):
    return io.open(base + name, encoding="utf-8").read()

def save(name, s):
    io.open(BASE + name, "w", encoding="utf-8", newline="\n").write(s)

# 1. Storage ctor params
p = "Storage.java"
s = load(p)
s = s.replace("_util = util;", "_ctx = ctx;")
s = s.replace("public Storage(ClientContext util,", "public Storage(ClientContext ctx,")
save(p, s)

# 2. TrackerClient UDP path fetch + UDP tracker client slot
p = "TrackerClient.java"
s = load(p)
s = s.replace("byte[] fetched = _ctx.get(s, true, fast ? -1 : 0, small ? 128 : 1024, small ? 1024 : 32*1024);",
              "byte[] fetched = fetch(s, fast ? -1 : 0, small ? 1024 : 32*1024);")
s = s.replace("UDPTrackerClient udptc = _ctx.getUDPTrackerClient();", "UDPTrackerClient udptc = _ctx.getUDPTrackerClient();")
save(p, s)

# 3. ClientContext UDP tracker slot
p = "ClientContext.java"
s = load(p)
s = s.replace("""    // set by the host application (UDP tracker client, extra DHT nodes)
    private volatile DatagramTransportFactory _datagramTransportFactory;""",
              """    // set by the host application (UDP tracker client, extra DHT nodes)
    private volatile DatagramTransportFactory _datagramTransportFactory;

    // created by the engine when UDP trackers are enabled (Snark.start)
    private volatile UDPTrackerClient _udpTracker;""")
s = s.replace("""    /** Set by the host application; null disables UDP trackers */
    public void setDatagramTransportFactory(DatagramTransportFactory factory) {
        _datagramTransportFactory = factory;
    }""",
              """    /** Set by the host application; null disables UDP trackers */
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
    }""")
save(p, s)

# 4. PeerAcceptor timeout cast
p = "PeerAcceptor.java"
s = load(p)
s = s.replace("socket.setReadTimeout(HASH_READ_TIMEOUT);", "socket.setReadTimeout((int) HASH_READ_TIMEOUT);")
save(p, s)

# 5. DataHelper.skip
p = "data/DataHelper.java"
s = load(p)
s = s.rstrip()[:-1]
s += """
  /**
   *  Skip n bytes on a stream, reading byte-by-byte if necessary —
   *  port of {@code net.i2p.data.DataHelper.skip} (public domain, I2P).
   *  @throws java.io.IOException
   */
  public static void skip(java.io.InputStream in, long n) throws java.io.IOException {
      if (n < 0)
          throw new IllegalArgumentException();
      if (n == 0)
          return;
      long read = 0;
      long nm1 = n - 1;
      if (nm1 > 0) {
          // skip all but the last byte
          read = in.skip(nm1);
          // if the stream didn't skip, read the bytes
          if (read < nm1) {
              byte[] buf = new byte[(int) Math.min(4096, n - read)];
              while (read < nm1) {
                  int sz = (int) Math.min(buf.length, nm1 - read);
                  int r = in.read(buf, 0, sz);
                  if (r < 0)
                      break;
                  read += r;
              }
          }
      }
      // skip/read the last byte
      if (read < n) {
          if (in.read() < 0)
              throw new java.io.EOFException("EOF while skipping");
      }
  }
}
"""
save(p, s)

# 6. PeerCheckerTask: drop comment-request block
p = "PeerCheckerTask.java"
s = load(p)
s = s.replace("""            // send Comment Request, about every 30 minutes
            if (fetchComments && ((_runCount + i) % 47) == 0)
                coordinator.sendCommentReq(peer);
""", "")
s = s.replace("        boolean fetchComments = false;\n", "")
save(p, s)

# 7. ExtensionHandler: re-add ByteArrayInputStream import
p = "ExtensionHandler.java"
s = load(p)
s = s.replace("import java.io.IOException;", "import java.io.ByteArrayInputStream;\nimport java.io.IOException;")
save(p, s)

# 8. UDPTrackerClient stop(): w.cancel -> cancelTimer
p = "UDPTrackerClient.java"
s = load(p)
s = s.replace("""        for (ReplyWaiter w : _sentQueries.values()) {
            w.cancel();
        }""",
              """        for (ReplyWaiter w : _sentQueries.values()) {
            w.cancelTimer();
        }""")
save(p, s)

# 9. URIUtil port
s = load("URIUtil.java", SRC)
s = s.replace("import net.i2p.data.DataHelper;", "import org.klomp.snark.data.DataHelper;")
save("URIUtil.java", s)

print("done")
