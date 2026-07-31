package org.klomp.snark.spi;

import org.klomp.snark.data.Hash;

/**
 *  Abstract peer identity — replaces {@code net.i2p.data.Destination}.
 *
 *  An identity is an opaque, immutable handle to a peer's address.
 *  The I2P implementation wraps a {@code Destination}; other
 *  implementations (loopback, SAM, test doubles) provide their own.
 *
 *  Implementations MUST implement {@code equals()}/{@code hashCode()}
 *  on the underlying address bytes.
 *
 *  @since 0.1.0
 */
public interface PeerIdentity {

    /** @return the raw address bytes */
    byte[] getData();

    /**
     *  @return the 32-byte SHA-256 hash of the address — this is the
     *          peer identifier used on the I2P DHT wire (compact peer
     *          info) and by the naming service for lookups
     */
    Hash calculateHash();

    /** @return base64 encoding of the address (with padding) */
    String toBase64();

    /** @return base32 encoding (I2P style, lowercase, with .b32.i2p suffix) */
    String toBase32();

    /**
     *  @return true if this identity uses the DSA-SHA1 signature type
     *          (I2P). Default false; the I2P transport implementation
     *          overrides. Used to skip trackers that only accept
     *          DSA peers.
     */
    default boolean isDSA() {
        return false;
    }
}
