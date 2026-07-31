package org.klomp.snark.data;

/**
 *  A 20-byte SHA-1 hash — port of {@code net.i2p.crypto.SHA1Hash}
 *  (public domain, I2P). Used for NIDs (node IDs) and info hashes
 *  in the DHT.
 */
public class SHA1Hash extends SimpleDataStructure {

    public static final int HASH_LENGTH = 20;

    /** @param data 20 bytes (may be null) */
    public SHA1Hash(byte[] data) {
        super(data);
    }

    @Override
    public int length() {
        return HASH_LENGTH;
    }

    /** A SHA1Hash is its own hash */
    @Override
    public Hash calculateHash() {
        return Hash.create(getData());
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
