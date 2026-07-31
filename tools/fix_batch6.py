import io

BASE = "C:/Users/VeerMetri/Documents/projects/i2psnark-abstract/src/main/java/org/klomp/snark/"

def load(name):
    return io.open(BASE + name, encoding="utf-8").read()

def save(name, s):
    io.open(BASE + name, "w", encoding="utf-8", newline="\n").write(s)

p = "ConnectionAcceptor.java"
s = load(p)

# close() has no checked exceptions on the SPI — drop the try/catch wrappers
s = s.replace("""      try
        {
          ss.close();
        }
      catch(IOException ioe) { }""",
              """      ss.close();""")
s = s.replace("try { socket.close(); } catch (IOException ioe) {}", "socket.close();")
s = s.replace("try { _socket.close(); } catch (IOException ignored) { }", "_socket.close();")

# restructure accept-loop catches: ConnectException (subclass) first, then IOException
old_catches = """        catch (IOException rre) {
            if (_log.shouldWarn())
                _log.warn("Waiting for router restart", rre);
            try {
                Thread.sleep(2*60*1000);
            } catch (InterruptedException ie) {}
            while (true) {
                if (_ctx.connected())
                    break;
                if (_ctx.connect())
                    break;
                try {
                    Thread.sleep(60*1000);
                } catch (InterruptedException ie) { break; }
            }
            if (_log.shouldWarn())
                _log.warn("Router restarted");
        }
        catch (IOException ioe)
          {
            int level = stop ? Log.WARN : Log.ERROR;
            if (_log.shouldLog(level))
                _log.log(level, "Error while accepting", ioe);
            synchronized(this) {
                if (!stop) {
                    locked_halt();
                    thread = null;
                    stop = true;
                }
            }
          }
        catch (ConnectException ioe)
          {
            // This is presumed to be due to socket closing by ClientContext.disconnect(),
            // which does not currently call our halt(), although it should
            if (_log.shouldWarn())
                _log.warn("Error while accepting", ioe);
            synchronized(this) {
                if (!stop) {
                    locked_halt();
                    thread = null;
                    stop = true;
                }
            }
          }
        catch (IOException ioe)
          {
            int level = stop ? Log.WARN : Log.ERROR;
            if (_log.shouldLog(level))
                _log.log(level, "Error while accepting", ioe);
            synchronized(this) {
                if (!stop) {
                    locked_halt();
                    thread = null;
                    stop = true;
                }
            }
          }"""
new_catches = """        catch (ConnectException ioe)
          {
            // This is presumed to be due to socket closing by ClientContext.disconnect(),
            // which does not currently call our halt(), although it should
            if (_log.shouldWarn())
                _log.warn("Error while accepting", ioe);
            synchronized(this) {
                if (!stop) {
                    locked_halt();
                    thread = null;
                    stop = true;
                }
            }
          }
        catch (IOException rre)
          {
            // The upstream RouterRestartException / I2PException branches
            // collapse into this: on accept failure, wait for the router
            // (the transport reports when it reconnects)
            if (_log.shouldWarn())
                _log.warn("Waiting for router restart", rre);
            try {
                Thread.sleep(2*60*1000);
            } catch (InterruptedException ie) {}
            while (true) {
                if (_ctx.connected())
                    break;
                if (_ctx.connect())
                    break;
                try {
                    Thread.sleep(60*1000);
                } catch (InterruptedException ie) { break; }
            }
            if (_log.shouldWarn())
                _log.warn("Router restarted");
        }"""
assert old_catches in s, "catch block not found"
s = s.replace(old_catches, new_catches)
save(p, s)
print("done")
