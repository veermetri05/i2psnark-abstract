package org.klomp.snark.event;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

import org.klomp.snark.spi.EventBus;
import org.klomp.snark.spi.Log;
import org.klomp.snark.spi.LogFactory;

/**
 *  A {@link LogFactory} that posts every record to an
 *  {@link org.klomp.snark.spi.EventBus} as a {@link LogEvent},
 *  optionally mirroring to another factory (e.g. console).
 *
 *  Install at startup to feed the UI's Event Log screen:
 *  <pre>
 *      EventBus bus = new SimpleEventBus();
 *      Logs.setFactory(new BusLogFactory(bus, new ConsoleLogFactory()));
 *      bus.register(LogListener.class, myLogScreen);
 *  </pre>
 *
 *  @since 0.1.0
 */
public class BusLogFactory implements LogFactory {

    private final EventBus _bus;
    private final LogFactory _delegate;
    private final Map<String, BusLog> _logs = new ConcurrentHashMap<String, BusLog>();

    public BusLogFactory(EventBus bus) {
        this(bus, null);
    }

    /**
     *  @param bus the bus to post LogEvents to
     *  @param delegate optional secondary factory (console/file), may be null
     */
    public BusLogFactory(EventBus bus, LogFactory delegate) {
        if (bus == null)
            throw new IllegalArgumentException("bus must not be null");
        _bus = bus;
        _delegate = delegate;
    }

    @Override
    public Log getLog(Class<?> clazz) {
        return getLog(clazz.getName());
    }

    @Override
    public Log getLog(String name) {
        BusLog log = _logs.get(name);
        if (log == null) {
            log = new BusLog(name);
            _logs.put(name, log);
        }
        return log;
    }

    private final class BusLog implements Log {
        private final String _tag;
        private final Log _delegateLog;

        BusLog(String name) {
            int idx = name.lastIndexOf('.');
            _tag = idx >= 0 ? name.substring(idx + 1) : name;
            _delegateLog = _delegate != null ? _delegate.getLog(name) : null;
        }

        @Override
        public boolean shouldLog(int level) {
            return _delegateLog == null || _delegateLog.shouldLog(level);
        }

        @Override
        public boolean shouldInfo() {
            return _delegateLog == null || _delegateLog.shouldInfo();
        }

        @Override
        public void log(int level, String message) {
            try {
                _bus.post(new LogEvent(level, _tag, message));
            } catch (RuntimeException e) {
                // bus down — fall through to delegate
            }
            if (_delegateLog != null)
                _delegateLog.log(level, message);
        }

        @Override
        public void log(int level, String message, Throwable t) {
            try {
                _bus.post(new LogEvent(level, _tag, message + ' ' + t));
            } catch (RuntimeException e) {
                // bus down — fall through to delegate
            }
            if (_delegateLog != null)
                _delegateLog.log(level, message, t);
        }
    }
}
