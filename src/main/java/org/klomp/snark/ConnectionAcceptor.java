/* ConnectionAcceptor - Accepts connections and routes them to sub-acceptors.
   Copyright (C) 2003 Mark J. Wielaard

   This file is part of Snark.
   
   This program is free software; you can redistribute it and/or modify
   it under the terms of the GNU General Public License as published by
   the Free Software Foundation; either version 2, or (at your option)
   any later version.
 
   This program is distributed in the hope that it will be useful,
   but WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
   GNU General Public License for more details.
 
   You should have received a copy of the GNU General Public License
   along with this program; if not, write to the Free Software Foundation,
   Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
*/

package org.klomp.snark;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ConnectException;

import org.klomp.snark.spi.StreamServer;
import org.klomp.snark.spi.PeerIdentity;
import org.klomp.snark.spi.Stream;
import org.klomp.snark.data.Hash;
import org.klomp.snark.spi.Log;
import org.klomp.snark.spi.Logs;
import org.klomp.snark.util.ObjectCounter;

/**
 * Accepts connections on a I2PServerSocket and routes them to PeerAcceptors.
 */
public class ConnectionAcceptor implements Runnable
{
  private final Log _log = Logs.getLog(ConnectionAcceptor.class);
  private final PeerAcceptor peeracceptor;
  private Thread thread;
  private final ClientContext _ctx;
  private final ObjectCounter<Hash> _badCounter = new ObjectCounter<Hash>();
  private final org.klomp.snark.spi.Cancellable _cleaner;

  private volatile boolean stop;

  // protocol errors before blacklisting.
  private static final int MAX_BAD = 1;
  private static final long BAD_CLEAN_INTERVAL = 30*60*1000;

  /**
   *  Multitorrent. Caller MUST call startAccepting()
   */
  public ConnectionAcceptor(ClientContext ctx, PeerCoordinatorSet set) {
      _ctx = ctx;
      peeracceptor = new PeerAcceptor(set);
      _cleaner = org.klomp.snark.spi.Scheduler.getInstance().schedulePeriodic(this::cleanBad, BAD_CLEAN_INTERVAL, BAD_CLEAN_INTERVAL);
  }
  
  /**
   *  May be called even when already running. May be called to start up again after halt().
   */
  public synchronized void startAccepting() {
      stop = false;
      if (_log.shouldLog(Log.WARN))
          _log.warn("ConnectionAcceptor startAccepting new thread? " + (thread == null));
      if (thread == null) {
          thread = new Thread(this, "I2PSnark acceptor");
          thread.setDaemon(true);
          thread.start();
      }
  }
  
  /**
   *  Unused (single torrent).
   *  Do NOT call startAccepting().
   */
  public ConnectionAcceptor(ClientContext ctx,
                            PeerAcceptor peeracceptor)
  {
    this.peeracceptor = peeracceptor;
    _ctx = ctx;
    
    thread = new Thread(this, "I2PSnark acceptor");
    thread.setDaemon(true);
    thread.start();
    _cleaner = org.klomp.snark.spi.Scheduler.getInstance().schedulePeriodic(this::cleanBad, BAD_CLEAN_INTERVAL, BAD_CLEAN_INTERVAL);
  }

  /**
   *  May be restarted later with startAccepting().
   */
  public synchronized void halt()
  {
    if (stop) return;
    stop = true;
    locked_halt();
    Thread t = thread;
    if (t != null) {
      t.interrupt();
      thread = null;
    }
  }


  /**
   *  Caller must synch
   *  @since 0.9.9
   */
  private void locked_halt()
  {
    StreamServer ss = _ctx.getStreamServer();
    if (ss != null) {
      ss.close();
    }
    _badCounter.clear();
    _cleaner.cancel();
  }
  
  /**
   *  Effectively unused, would only be called if we changed
   *  I2CP host/port, which is hidden in the gui if in router context
   */
  public synchronized void restart() {
      Thread t = thread;
      if (t != null)
          t.interrupt();
      else
          startAccepting();
  }

  public int getPort()
  {
    return TrackerClient.PORT; // serverSocket.getLocalPort();
  }

  public void run()
  {
      try {
          run2();
      } finally {
          synchronized(this) {
              thread = null;
          }
      }
  }

  private void run2()
  {
    while(!stop)
      {
        StreamServer serverSocket = _ctx.getStreamServer();
        while ( (serverSocket == null) && (!stop)) {
            if (!(_ctx.isConnecting() || _ctx.connected())) {
                stop = true;
                break;
            }
            try { Thread.sleep(10*1000); } catch (InterruptedException ie) {}
            serverSocket = _ctx.getStreamServer();
        }
        if(stop)
            break;
        try
          {
            Stream socket = serverSocket.accept();
            if (socket == null) {
                    continue;
            } else {
                if (socket.getPeer() != null && socket.getPeer().equals(_ctx.getMyDestination())) {
                    _log.error("Incoming connection from myself");
                    socket.close();
                    continue;
                }
                PeerIdentity peer = socket.getPeer();
                if (peer == null) {
                    if (_log.shouldWarn())
                        _log.warn("Dropping connection with unknown peer identity");
                    socket.close();
                    continue;
                }
                Hash h = peer.calculateHash();
                if (socket.getLocalPort() == 80) {
                     _badCounter.increment(h);
                    if (_log.shouldLog(Log.WARN))
                        _log.error("Dropping incoming HTTP from " + h);
                    socket.close();
                    continue;
                }
                int bad = _badCounter.count(h);
                if (bad >= MAX_BAD) {
                    if (_log.shouldLog(Log.WARN))
                        _log.warn("Rejecting connection from " + h +
                                  " after " + bad + " failures, max is " + MAX_BAD);
                    socket.close();
                    continue;
                }
                Thread t = new Thread(new Handler(socket), "I2PSnark incoming connection");
                t.start();
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
        }
        // catch oom?
      }
      if (_log.shouldLog(Log.WARN))
          _log.warn("ConnectionAcceptor closed");
  }
  
  private class Handler implements Runnable {
      private final Stream _socket;

      public Handler(Stream socket) {
          _socket = socket;
      }

      public void run() {
          try {
              InputStream in = _socket.getInputStream();
              OutputStream out = _socket.getOutputStream();
              // this is for the readahead in PeerAcceptor.connection()
              in = new BufferedInputStream(in);
              if (_log.shouldLog(Log.DEBUG))
                  _log.debug("Handling socket from " + _socket.getPeer().calculateHash());
              peeracceptor.connection(_socket, in, out);
          } catch (PeerAcceptor.ProtocolException ihe) {
              _badCounter.increment(_socket.getPeer().calculateHash());
              if (_log.shouldLog(Log.INFO))
                  _log.info("Protocol error from " + _socket.getPeer().calculateHash(), ihe);
              _socket.close();
          } catch (IOException ioe) {
              if (_log.shouldLog(Log.DEBUG))
                  _log.debug("Error handling connection from " + _socket.getPeer().calculateHash(), ioe);
              _socket.close();
          }
      }
  }

    /** @since 0.9.1 */
    private void cleanBad() {
        if (stop)
            return;
        _badCounter.clear();
    }
}
