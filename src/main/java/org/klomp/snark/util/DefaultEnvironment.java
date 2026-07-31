package org.klomp.snark.util;

import org.klomp.snark.spi.Clock;
import org.klomp.snark.spi.Environment;
import org.klomp.snark.spi.Log;
import org.klomp.snark.spi.Logs;
import org.klomp.snark.spi.RandomSource;
import org.klomp.snark.spi.Scheduler;
import org.klomp.snark.spi.Storage;

/**
 *  Default {@link Environment} — uses the global clock, random
 *  source, scheduler and log registry, plus the given storage.
 */
public class DefaultEnvironment implements Environment {

    private final Storage _storage;

    public DefaultEnvironment(Storage storage) {
        if (storage == null)
            throw new IllegalArgumentException("storage must not be null");
        _storage = storage;
    }

    @Override
    public Clock clock() {
        return Clock.getInstance();
    }

    @Override
    public RandomSource random() {
        return RandomSource.getInstance();
    }

    @Override
    public Scheduler scheduler() {
        return Scheduler.getInstance();
    }

    @Override
    public Storage storage() {
        return _storage;
    }

    @Override
    public Log log(Class<?> clazz) {
        return Logs.getLog(clazz);
    }
}
