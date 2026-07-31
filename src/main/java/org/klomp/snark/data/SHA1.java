package org.klomp.snark.data;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 *  SHA-1 digest helper — replaces {@code net.i2p.crypto.SHA1}
 *  (public domain, I2P) with a plain JCE wrapper.
 *
 *  Note: SHA-1 here is the BitTorrent metainfo/piece hash, not a
 *  security boundary; the I2P DHT uses SHA-256 ({@link Hash}).
 */
public final class SHA1 {

    private SHA1() {}

    /** @return a fresh SHA-1 MessageDigest */
    public static MessageDigest getInstance() {
        try {
            return MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            // every JVM/Android has SHA-1
            throw new RuntimeException("SHA-1 unavailable", e);
        }
    }

    /** @return the SHA-1 digest of the data */
    public static byte[] hash(byte[] data) {
        return getInstance().digest(data);
    }
}
