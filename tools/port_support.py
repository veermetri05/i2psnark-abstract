import io
import re

SRC = "C:/Users/VeerMetri/Documents/projects/LibreTorrentI2P/i2p.i2p/apps/i2psnark/java/src/org/klomp/snark/"
DST = "C:/Users/VeerMetri/Documents/projects/i2psnark-abstract/src/main/java/org/klomp/snark/"

def load(fn):
    return io.open(SRC + fn, encoding="utf-8").read()

def save(fn, s):
    io.open(DST + fn, "w", encoding="utf-8", newline="\n").write(s)

# ── PeerAcceptor ────────────────────────────────────────────────────
s = load("PeerAcceptor.java")
s = s.replace("import net.i2p.I2PAppContext;\n", "")
s = s.replace("import net.i2p.client.streaming.I2PSocket;", "import org.klomp.snark.spi.Stream;")
s = s.replace("import net.i2p.data.Base64;", "import org.klomp.snark.data.Base64;")
s = s.replace("import net.i2p.data.DataHelper;", "import org.klomp.snark.data.DataHelper;")
s = s.replace("import net.i2p.util.Log;", "import org.klomp.snark.spi.Log;\nimport org.klomp.snark.spi.Logs;")
s = s.replace("I2PAppContext.getGlobalContext().logManager().getLog(PeerAcceptor.class)",
              "Logs.getLog(PeerAcceptor.class)")
s = s.replace("public void connection(I2PSocket socket,", "public void connection(Stream socket,")
s = s.replace("socket.getPeerDestination()", "socket.getPeer()")
save("PeerAcceptor.java", s)

# ── ConnectionAcceptor ──────────────────────────────────────────────
s = load("ConnectionAcceptor.java")
s = s.replace("import net.i2p.I2PException;\n", "")
s = s.replace("import net.i2p.client.streaming.I2PServerSocket;", "import org.klomp.snark.spi.StreamServer;")
s = s.replace("import net.i2p.client.streaming.I2PSocket;", "import org.klomp.snark.spi.Stream;")
s = s.replace("import net.i2p.client.streaming.RouterRestartException;\n", "")
s = s.replace("import net.i2p.data.Hash;", "import org.klomp.snark.data.Hash;")
s = s.replace("import net.i2p.util.I2PAppThread;\n", "")
s = s.replace("import net.i2p.util.Log;", "import org.klomp.snark.spi.Log;\nimport org.klomp.snark.spi.Logs;")
s = s.replace("import net.i2p.util.ObjectCounter;", "import org.klomp.snark.util.ObjectCounter;")
s = s.replace("import net.i2p.util.SimpleTimer2;\n", "")
s = s.replace("I2PSnarkUtil", "ClientContext")
s = s.replace("  private final ClientContext _util;", "  private final ClientContext _ctx;")
s = s.replace("_util.", "_ctx.")
s = s.replace("I2PAppContext.getGlobalContext().logManager().getLog(ConnectionAcceptor.class)",
              "Logs.getLog(ConnectionAcceptor.class)")
s = s.replace("""  private final SimpleTimer2.TimedEvent _cleaner;""",
              """  private final org.klomp.snark.spi.Cancellable _cleaner;""")
s = s.replace("""    thread = new I2PAppThread(this, "I2PSnark acceptor");
    thread.setDaemon(true);""",
              """    thread = new Thread(this, "I2PSnark acceptor");
    thread.setDaemon(true);""")
s = s.replace("""    thread = new I2PAppThread(this, "I2PSnark acceptor");
    thread.setDaemon(true);""",
              """    thread = new Thread(this, "I2PSnark acceptor");
    thread.setDaemon(true);""")
s = s.replace("thread = new I2PAppThread(this, \"I2PSnark acceptor\");",
              "thread = new Thread(this, \"I2PSnark acceptor\");")
s = s.replace("I2PServerSocket ss = _ctx.getServerSocket();", "StreamServer ss = _ctx.getStreamServer();")
s = s.replace("I2PServerSocket serverSocket = _ctx.getServerSocket();", "StreamServer serverSocket = _ctx.getStreamServer();")
s = s.replace("I2PSocket socket = serverSocket.accept();", "Stream socket = serverSocket.accept();")
s = s.replace("socket.getPeerDestination()", "socket.getPeer()")
s = s.replace("catch(I2PException ioe) { }", "catch(IOException ioe) { }")
s = s.replace("catch (RouterRestartException rre)", "catch (IOException rre)")
# cleaner timer: find the _cleaner init (SimpleTimer2.getInstance().addEvent(...))
s = re.sub(r"_cleaner = SimpleTimer2\.getInstance\(\)\.addEvent\([^;]+;",
           "_cleaner = org.klomp.snark.spi.Scheduler.getInstance().schedulePeriodic(() -> _cleanup(), 120*1000, 120*1000);", s)
save("ConnectionAcceptor.java", s)

# ── IdleChecker ─────────────────────────────────────────────────────
s = load("IdleChecker.java")
s = s.replace("import net.i2p.client.I2PSession;\n", "")
s = s.replace("import net.i2p.client.streaming.I2PSocketManager;\n", "")
s = s.replace("import net.i2p.util.Log;", "import org.klomp.snark.spi.Log;\nimport org.klomp.snark.spi.Logs;")
s = s.replace("import net.i2p.util.SimpleTimer2;\n", "")
s = s.replace("I2PAppContext.getGlobalContext().logManager().getLog(IdleChecker.class)",
              "Logs.getLog(IdleChecker.class)")
s = s.replace("SimpleTimer2.TimedEvent", "org.klomp.snark.spi.Cancellable")
save("IdleChecker.java", s)

print("ok")
