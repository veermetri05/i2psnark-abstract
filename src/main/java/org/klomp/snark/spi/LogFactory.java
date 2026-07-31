package org.klomp.snark.spi;

/**
 *  Creates {@link Log} instances — replaces I2P's
 *  {@code net.i2p.util.LogManager}.
 *
 *  @since 0.1.0
 */
public interface LogFactory {

    /** @return a Log for the given class (cached by the factory) */
    Log getLog(Class<?> clazz);

    /** @return a Log for the given name (cached by the factory) */
    Log getLog(String name);
}
