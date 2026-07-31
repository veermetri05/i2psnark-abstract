package org.klomp.snark.data;

/**
 *  A 32-byte SHA-256 hash — port of {@code net.i2p.data.Hash}
 *  (public domain, I2P). Used on the DHT wire as the compact peer
 *  identifier (SHA-256 of a peer's address).
 */
public class Hash extends SimpleDataStructure {

    public static final int HASH_LENGTH = 32;
    public static final Hash FAKE_HASH = new Hash(new byte[HASH_LENGTH]);

    /** @param data 32 bytes (may be null) */
    public Hash(byte[] data) {
        super(data);
    }

    /** @return a new Hash from the data */
    public static Hash create(byte[] data) {
        return new Hash(data);
    }

    /** @return a new Hash from data starting at offset (32 bytes) */
    public static Hash create(byte[] data, int off) {
        byte[] d = new byte[HASH_LENGTH];
        System.arraycopy(data, off, d, 0, HASH_LENGTH);
        return new Hash(d);
    }

    @Override
    public int length() {
        return HASH_LENGTH;
    }

    /** A Hash is its own hash */
    @Override
    public Hash calculateHash() {
        return this;
    }

    @Override
    public boolean equals(Object object) {
        return super.equals(object);
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }
}
