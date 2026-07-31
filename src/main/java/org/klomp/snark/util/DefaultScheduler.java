package org.klomp.snark.util;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import org.klomp.snark.spi.Cancellable;
import org.klomp.snark.spi.Scheduler;

/**
 *  Default {@link Scheduler} — a daemon-thread scheduled executor.
 */
public class DefaultScheduler implements Scheduler {

    private final ScheduledExecutorService _executor;

    public DefaultScheduler() {
        this(2, "i2psnark-scheduler");
    }

    public DefaultScheduler(int threads, String name) {
        ThreadFactory tf = new ThreadFactory() {
            private int _n;
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, name + '-' + (++_n));
                t.setDaemon(true);
                return t;
            }
        };
        _executor = Executors.newScheduledThreadPool(threads, tf);
    }

    @Override
    public Cancellable schedule(Runnable task, long delayMs) {
        final ScheduledFuture<?> f = _executor.schedule(task, delayMs, TimeUnit.MILLISECONDS);
        return new Cancellable() {
            @Override
            public void cancel() {
                f.cancel(false);
            }
        };
    }

    @Override
    public Cancellable schedulePeriodic(Runnable task, long initialDelayMs, long periodMs) {
        final ScheduledFuture<?> f = _executor.scheduleAtFixedRate(task, initialDelayMs, periodMs, TimeUnit.MILLISECONDS);
        return new Cancellable() {
            @Override
            public void cancel() {
                f.cancel(false);
            }
        };
    }
}
