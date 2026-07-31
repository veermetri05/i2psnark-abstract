package org.klomp.snark.spi;

/**
 *  Creates {@link DatagramTransport} instances — the abstract
 *  replacement for "give me a session datagram channel". The host
 *  application (transport module) supplies the factory; the engine
 *  asks for one per DHT node / UDP-tracker client with an explicit
 *  query port.
 *
 *  @since 0.2.0
 */
public interface DatagramTransportFactory {

    /**
     *  @param queryPort explicit query port, or 0 for a random one
     *  @return a fresh datagram transport bound to the session
     */
    DatagramTransport createDatagramTransport(int queryPort);
}
