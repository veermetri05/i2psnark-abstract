package org.klomp.snark.event;

/**
 *  A log record — posted by {@link BusLogFactory} so the UI's log
 *  screen (Event Log) is fed by the same logging pipeline used by
 *  the protocol core, without polling.
 *
 *  @since 0.1.0
 */
public class LogEvent extends Event<LogListener> {

    private final int _level;
    private final String _tag;
    private final String _message;

    public LogEvent(int level, String tag, String message) {
        _level = level;
        _tag = tag;
        _message = message;
    }

    /** @return log level (see org.klomp.snark.spi.Log constants) */
    public int getLevel() {
        return _level;
    }

    /** @return the log tag (class simple name or custom) */
    public String getTag() {
        return _tag;
    }

    /** @return the log message */
    public String getMessage() {
        return _message;
    }

    @Override
    public Class<LogListener> listenerType() {
        return LogListener.class;
    }

    @Override
    public void dispatch(LogListener listener) {
        listener.onLogEvent(this);
    }
}
