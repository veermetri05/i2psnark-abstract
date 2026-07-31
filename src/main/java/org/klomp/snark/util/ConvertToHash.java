package org.klomp.snark.util;

import org.klomp.snark.data.Base32;
import org.klomp.snark.data.Hash;

/**
 *  Resolve a host name (base32 or base64 form, with or without the
 *  ".i2p" suffix) to a {@link Hash} — a trimmed port of
 *  {@code net.i2p.util.ConvertToHash} (GPLv2+, I2P) covering what
 *  TrackerClient needs: b32 host names.
 */
public class ConvertToHash {

    private ConvertToHash() {}

    /**
     *  @param host e.g. "abc....b32.i2p" (with or without suffix)
     *  @return the 32-byte hash, or null if the host is not a b32 name
     */
    public static Hash getHash(String host) {
        if (host == null)
            return null;
        String name = host;
        if (name.endsWith(".b32.i2p"))
            name = name.substring(0, name.length() - 8);
        else if (name.endsWith(".i2p"))
            return null;
        if (name.length() != 52)
            return null;
        byte[] b = Base32.decode(name);
        if (b == null)
            return null;
        return Hash.create(b);
    }
}
