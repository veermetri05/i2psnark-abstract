package org.klomp.snark.data;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 *  SHA-256 helpers — pure replacement for I2P's SHA256Generator
 *  (the DHT's 32-byte peer hash).
 */
public final class SHA256 {

    private SHA256() {}

    /** @return the SHA-256 digest of the input (32 bytes) */
    public static byte[] hash(byte[] data) {
        return hash(data, 0, data.length);
    }

    /** @return the SHA-256 digest of data[off..off+len) (32 bytes) */
    public static byte[] hash(byte[] data, int off, int len) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(data, off, len);
            return md.digest();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
