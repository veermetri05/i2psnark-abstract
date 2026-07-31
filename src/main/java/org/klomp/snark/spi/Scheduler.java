package org.klomp.snark.spi;

import org.klomp.snark.util.DefaultScheduler;

/**
 *  Abstract timer service — replaces {@code net.i2p.util.SimpleTimer2}.
 *
 *  All timers run on daemon threads. Returned handles are used to
 *  cancel pending (or periodic) tasks.
 *
 *  @since 0.1.0
 */
public interface Scheduler {

    /**
     *  Schedule a one-shot task.
     *
     *  @param task the runnable to run
     *  @param delayMs delay before the task runs
     *  @return a handle to cancel the pending task
     */
    Cancellable schedule(Runnable task, long delayMs);

    /**
     *  Schedule a fixed-rate periodic task.
     *
     *  @param task the runnable to run
     *  @param initialDelayMs delay before the first run
     *  @param periodMs period between runs
     *  @return a handle to cancel the task
     */
    Cancellable schedulePeriodic(Runnable task, long initialDelayMs, long periodMs);

    /** @return the global scheduler (defaults to {@link DefaultScheduler}) */
    static Scheduler getInstance() {
        return Holder.INSTANCE;
    }

    /** Replace the global scheduler. Null restores the default. */
    static void setInstance(Scheduler scheduler) {
        Holder.INSTANCE = scheduler != null ? scheduler : new DefaultScheduler();
    }

    /** lazy holder so the default can be swapped at runtime */
    final class Holder {
        static volatile Scheduler INSTANCE = new DefaultScheduler();
        private Holder() {}
    }
}
