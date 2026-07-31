package org.klomp.snark.spi;

import org.klomp.snark.util.DefaultEnvironment;

/**
 *  Abstract application environment — replaces
 *  {@code net.i2p.I2PAppContext} for the pieces the abstract layer
 *  needs (clock, randomness, logging, timers, storage).
 *
 *  Host applications typically build one environment per session:
 *  <pre>
 *      Environment env = new DefaultEnvironment(storage);
 *      // or for full control:
 *      Environment env = new Environment() { ... delegate to your I2P context ... };
 *  </pre>
 *
 *  @since 0.1.0
 */
public interface Environment {

    /** @return the time source */
    Clock clock();

    /** @return the random source */
    RandomSource random();

    /** @return the timer service */
    Scheduler scheduler();

    /** @return the file storage */
    Storage storage();

    /** @return a Log for the given class (convenience for Logs.getLog) */
    Log log(Class<?> clazz);

    /** @return a default environment (default clock/random/scheduler/logs, given storage) */
    static Environment basic(Storage storage) {
        return new DefaultEnvironment(storage);
    }
}
