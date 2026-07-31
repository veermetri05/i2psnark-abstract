/* PeerID - All public information concerning a peer.
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

import java.util.Map;

import org.klomp.snark.data.Base32;
import org.klomp.snark.data.Base64;
import org.klomp.snark.data.DataHelper;
import org.klomp.snark.spi.PeerIdentity;

import org.klomp.snark.bencode.BEValue;
import org.klomp.snark.bencode.InvalidBEncodingException;

/**
 *  Store the address information about a peer.
 *  Prior to 0.8.1, an instantiation required a peer ID, and full Destination address.
 *  Starting with 0.8.1, to support compact tracker responses,
 *  a PeerID can be instantiated with a Destination Hash alone.
 *  The full destination lookup is deferred until getAddress() is called,
 *  and the PeerID is not required.
 *  Equality is now determined solely by the dest hash.
 */
public class PeerID implements Comparable<PeerID>
{
  private byte[] id;
  private PeerIdentity address;
  private final int port;
  private byte[] destHash;
  /** whether we have tried to get the dest from the hash - only do once */

  /** WebPeer.IDBytes (WebSeedBEP19) — inlined so PeerID does not depend on WebPeer */
  private static final byte[] WEBSEED_ID = DataHelper.getASCII("WebSeedBEP19");
  private boolean triedDestLookup;
  private final int hash;
  private final ClientContext ctx;
  private String _toStringCache;

  public PeerID(byte[] id, PeerIdentity address)
  {
    this.id = id;
    this.address = address;
    this.port = ClientContext.PORT;
    this.destHash = address.calculateHash().getData();
    hash = calculateHash();
    ctx = null;
  }

  /**
   * Creates a PeerID from a Map containing BEncoded peer id, ip and
   * port.
   */
  /**
   * Creates a PeerID from a Map containing BEncoded peer id, ip and
   * port (non-compact tracker response). The "ip" is a base64
   * destination, resolved via the client context's identity factory.
   */
  public PeerID(Map<String, BEValue> m, ClientContext ctx)
    throws InvalidBEncodingException
  {
    BEValue bevalue = m.get("peer id");
    if (bevalue == null)
      throw new InvalidBEncodingException("peer id missing");
    id = bevalue.getBytes();

    bevalue = m.get("ip");
    if (bevalue == null)
      throw new InvalidBEncodingException("ip missing");
    address = ctx.getDestinationFromBase64(bevalue.getString());
    if (address == null)
        throw new InvalidBEncodingException("Invalid destination [" + bevalue.getString() + "]");

    port = ClientContext.PORT;
    this.destHash = address.calculateHash().getData();
    hash = calculateHash();
    this.ctx = ctx;
  }

  /**
   * Creates a PeerID from a destHash
   * @param ctx for eventual destination lookup
   * @since 0.8.1
   */
  public PeerID(byte[] dest_hash, ClientContext ctx) throws InvalidBEncodingException
  {
    // id and address remain null
    port = ClientContext.PORT;
    if (dest_hash.length != 32)
        throw new InvalidBEncodingException("bad hash length");
    destHash = dest_hash;
    hash = DataHelper.hashCode(dest_hash);
    this.ctx = ctx;
  }

  public byte[] getID()
  {
    return id;
  }

  /** for connecting out to peer based on desthash @since 0.8.1 */
  public void setID(byte[] xid)
  {
    id = xid;
  }

  /**
   *  Get the destination.
   *  If this PeerId was instantiated with a destHash,
   *  and we have not yet done so, lookup the full destination, which may take
   *  up to 15 seconds.
   *  @return identity or null if unknown
   */
  public synchronized PeerIdentity getAddress()
  {
    if (address == null && destHash != null && !triedDestLookup) {
        String b32 = Base32.encode(destHash) + ".b32.i2p";
        if (ctx != null)
            address = ctx.getDestination(b32);
        triedDestLookup = true;
    }
    return address;
  }

  public int getPort()
  {
    return port;
  }

  /** @since 0.8.1 */
  public byte[] getDestHash()
  {
    return destHash;
  }

  private int calculateHash()
  {
    return DataHelper.hashCode(destHash);
  }

  /**
   * The hash code of a PeerID is the hashcode of the desthash
   */
    @Override
  public int hashCode()
  {
    return hash;
  }

  /**
   * Returns true if and only if this peerID and the given peerID have
   * the same destination hash
   */
  public boolean sameID(PeerID pid)
  {
    return DataHelper.eq(destHash, pid.getDestHash());
  }

  /**
   * Two PeerIDs are equal when they have the same dest hash
   */
    @Override
  public boolean equals(Object o)
  {
    if (o instanceof PeerID)
      {
        PeerID pid = (PeerID)o;

        return sameID(pid);
      }
    else
      return false;
  }

  /**
   * Compares port, address and id.
   * @deprecated unused? and will NPE now that address can be null?
   */
  @Deprecated
  public int compareTo(PeerID pid)
  {
    int result = port - pid.port;
    if (result != 0)
      return result;

    result = address.hashCode() - pid.address.hashCode();
    if (result != 0)
      return result;

    for (int i = 0; i < id.length; i++)
      {
        result = id[i] - pid.id[i];
        if (result != 0)
          return result;
      }

    return 0;
  }

  /**
   * Returns the String "id@address" where id is the first 4 chars of the base64 encoded id
   * and address is the first 6 chars of the base64 dest (was the base64 hash of the dest) which
   * should match what the bytemonsoon tracker reports on its web pages.
   */
  @Override
  public String toString()
  {
    if (_toStringCache != null)
        return _toStringCache;
    if (id != null && DataHelper.eq(id, 0, WEBSEED_ID, 0, WEBSEED_ID.length)) {
        _toStringCache = "WebSeed@" + Base32.encode(destHash) + ".b32.i2p";
        return _toStringCache;
    }
    if (id == null || address == null)
        return "unkn@" + Base64.encode(destHash).substring(0, 6);
    int nonZero = 0;
    for (int i = 0; i < id.length; i++) {
        if (id[i] != 0) {
            nonZero = i;
            break;
        }
    }
    _toStringCache = Base64.encode(id, nonZero, id.length-nonZero).substring(0,4) + "@" + address.toBase64().substring(0,6);
    return _toStringCache;
  }

  /**
   * Encode an id as a hex encoded string and remove leading zeros.
   */
  public static String idencode(byte[] bs)
  {
    boolean leading_zeros = true;

    StringBuilder sb = new StringBuilder(bs.length*2);
    for (int i = 0; i < bs.length; i++)
      {
        int c = bs[i] & 0xFF;
        if (leading_zeros && c == 0)
          continue;
        else
          leading_zeros = false;

        if (c < 16)
          sb.append('0');
        sb.append(Integer.toHexString(c));
      }

    return sb.toString();
  }

}
