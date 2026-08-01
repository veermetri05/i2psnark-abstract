package org.klomp.snark.spi;

import java.io.IOException;

/**
 *  Abstract I2P session view — the common surface of the streaming
 *  and datagram transports, replacing the parts of
 *  {@code net.i2p.client.I2PSession} the abstract layer needs.
 *
 *  @since 0.1.0
 */
public interface Session {

    /** @return our own identity */
    PeerIdentity getLocalIdentity();

    /**
     *  Resolve a 32-byte SHA-256 hash (as returned by the DHT) to a
     *  full identity — replaces {@code I2PSession.lookupDest()}.
     *
     *  @param sha256Hash the 32-byte hash to resolve
     *  @param timeoutMs maximum time to wait
     *  @return the resolved identity, or null if unknown
     *  @throws IOException on lookup failure
     */
    PeerIdentity lookup(byte[] sha256Hash, long timeoutMs) throws IOException;

    /**
     *  Resolve an I2P host name (e.g. {@code tracker2.postman.i2p}) via
     *  the transport's naming service — SAM NAMING LOOKUP (router
     *  addressbook) or the I2CP session lookup for names.
     *
     *  Default: no naming service (returns null). Transport
     *  implementations with a naming service override this.
     *
     *  @param name the host name (with or without the {@code .i2p}
     *              suffix)
     *  @param timeoutMs maximum time to wait
     *  @return the resolved identity, or null if the name is unknown
     *  @throws IOException on lookup failure
     */
    default PeerIdentity lookupName(String name, long timeoutMs) throws IOException {
        return null; // no naming service
    }

    /** @return true if the underlying session is closed */
    boolean isClosed();
}
