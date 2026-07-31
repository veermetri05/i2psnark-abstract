package org.klomp.snark.spi;

/**
 *  Abstract logging — replaces {@code net.i2p.util.Log}.
 *
 *  An implementation is provided by the host application via
 *  {@link Logs#setFactory(LogFactory)}. The default factory writes
 *  to {@code System.err}.
 *
 *  Level constants match I2P's {@code net.i2p.util.Log} so ported
 *  I2PSnark code keeps its existing {@code Log.WARN} style constants.
 *
 *  @since 0.1.0
 */
public interface Log {

    /** Priority level for a critical error */
    int CRIT = 0;
    /** Priority level for an error */
    int ERROR = 1;
    /** Priority level for a warning */
    int WARN = 2;
    /** Priority level for informational messages */
    int INFO = 3;
    /** Priority level for debugging messages */
    int DEBUG = 4;

    /** @return true if the given level is currently being logged */
    boolean shouldLog(int level);

    /** @return true if INFO or lower is being logged */
    boolean shouldInfo();

    /** @return true if WARN or lower is being logged */
    default boolean shouldWarn() {
        return shouldLog(WARN);
    }

    /** @return true if DEBUG or lower is being logged */
    default boolean shouldDebug() {
        return shouldLog(DEBUG);
    }

    /** Log a message at the given level */
    void log(int level, String message);

    /** Log a message and throwable at the given level */
    void log(int level, String message, Throwable t);

    // ── Convenience defaults ─────────────────────────────────────────

    default void error(String message) {
        log(ERROR, message);
    }

    default void error(String message, Throwable t) {
        log(ERROR, message, t);
    }

    default void warn(String message) {
        log(WARN, message);
    }

    default void warn(String message, Throwable t) {
        log(WARN, message, t);
    }

    default void info(String message) {
        log(INFO, message);
    }

    default void info(String message, Throwable t) {
        log(INFO, message, t);
    }

    default void debug(String message) {
        log(DEBUG, message);
    }

    default void debug(String message, Throwable t) {
        log(DEBUG, message, t);
    }
}
