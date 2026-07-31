package org.klomp.snark.util;

import java.util.Locale;

import org.klomp.snark.data.Base32;
import org.klomp.snark.data.Base64;
import org.klomp.snark.data.Hash;

/**
 *  Resolve a host name (base32/base64 hash or full destination, with
 *  or without a ".i2p" suffix) to a {@link Hash} — a port of
 *  {@code net.i2p.util.ConvertToHash} (GPLv2+, I2P) minus the naming
 *  service (non-hash ".i2p" names resolve to null in the abstract layer).
 */
public class ConvertToHash {

    private ConvertToHash() {}

    /**
     *  @param peer a host name or URL host:port-ish string, e.g.
     *              "abc....b32.i2p", "abc...(52).i2p", "http://..." 
     *  @return the 32-byte hash, or null if not a hash name
     */
    public static Hash getHash(String peer) {
        if (peer == null)
            return null;
        String peerLC = peer.toLowerCase(Locale.US);
        if (peerLC.startsWith("http://")) {
            peer = peer.substring(7);
            peerLC = peerLC.substring(7);
        } else if (peerLC.startsWith("https://")) {
            peer = peer.substring(8);
            peerLC = peerLC.substring(8);
        }
        if (peer.endsWith("/")) {
            peer = peer.substring(0, peer.length() - 1);
            peerLC = peerLC.substring(0, peerLC.length() - 1);
        }
        if (peerLC.endsWith(".i2p.alt")) {
            peer = peer.substring(0, peer.length() - 4);
            peerLC = peerLC.substring(0, peerLC.length() - 4);
        }
        // b64 hash
        if (peer.length() == 44 && !peerLC.endsWith(".i2p")) {
            byte[] b = Base64.decode(peer);
            if (b != null && b.length == Hash.HASH_LENGTH)
                return Hash.create(b);
        }
        // b64 hash.i2p
        if (peer.length() == 48 && peerLC.endsWith(".i2p")) {
            byte[] b = Base64.decode(peer.substring(0, 44));
            if (b != null && b.length == Hash.HASH_LENGTH)
                return Hash.create(b);
        }
        // b64 dest.i2p
        if (peer.length() >= 520 && peerLC.endsWith(".i2p")) {
            byte[] b = Base64.decode(peer.substring(0, peer.length() - 4));
            if (b != null && b.length == Hash.HASH_LENGTH)
                return Hash.create(b);
        }
        // b64 dest
        if (peer.length() >= 516 && !peerLC.endsWith(".i2p")) {
            byte[] b = Base64.decode(peer);
            if (b != null && b.length == Hash.HASH_LENGTH)
                return Hash.create(b);
        }
        // b32 hash.b32.i2p
        if (peer.length() == 60 && peerLC.endsWith(".b32.i2p")) {
            byte[] b = Base32.decode(peer.substring(0, 52));
            if (b != null && b.length == Hash.HASH_LENGTH)
                return Hash.create(b);
        }
        // b32 hash
        if (peer.length() == 52 && !peerLC.endsWith(".i2p")) {
            byte[] b = Base32.decode(peer);
            if (b != null && b.length == Hash.HASH_LENGTH)
                return Hash.create(b);
        }
        // example.i2p — naming service not in the abstract layer
        return null;
    }
}
