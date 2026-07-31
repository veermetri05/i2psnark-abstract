package org.klomp.snark.spi;

/**
 *  Creates {@link PeerIdentity} instances from serialized forms —
 *  needed to deserialize identities stored in the DHT persistence
 *  file and returned by the HTTP bootstrap server.
 *
 *  The I2P implementation wraps {@code Destination.create(...)} /
 *  {@code new Destination(base64)}.
 *
 *  @since 0.1.0
 */
public interface PeerIdentityFactory {

    /**
     *  Create an identity from raw address bytes.
     *  @throws IllegalArgumentException on malformed data
     */
    PeerIdentity fromBytes(byte[] data);

    /**
     *  Create an identity from its base64 form.
     *  @throws IllegalArgumentException on malformed data
     */
    PeerIdentity fromBase64(String base64);
}
