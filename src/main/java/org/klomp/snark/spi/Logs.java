package org.klomp.snark.spi;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

import org.klomp.snark.util.ConsoleLogFactory;

/**
 *  Static registry for the {@link Log} implementation — the abstract
 *  replacement for {@code I2PAppContext.getGlobalContext().logManager()}.
 *
 *  Host applications install their own logging (e.g. an Android
 *  triplex logger or a file logger) exactly once at startup:
 *  <pre>
 *      Logs.setFactory(new MyLogFactory());
 *      Log log = Logs.getLog(MyClass.class);
 *  </pre>
 *
 *  @since 0.1.0
 */
public final class Logs {

    private static volatile LogFactory _factory = new ConsoleLogFactory();
    private static final Map<Object, Log> CACHE = new ConcurrentHashMap<Object, Log>();

    private Logs() {}

    /** Replace the log factory. Null restores the console default. */
    public static void setFactory(LogFactory factory) {
        _factory = factory != null ? factory : new ConsoleLogFactory();
        CACHE.clear();
    }

    /** @return the current log factory */
    public static LogFactory getFactory() {
        return _factory;
    }

    /** @return a Log for the given class */
    public static Log getLog(Class<?> clazz) {
        Log log = CACHE.get(clazz);
        if (log == null) {
            log = _factory.getLog(clazz);
            CACHE.put(clazz, log);
        }
        return log;
    }

    /** @return a Log for the given name */
    public static Log getLog(String name) {
        Log log = CACHE.get(name);
        if (log == null) {
            log = _factory.getLog(name);
            CACHE.put(name, log);
        }
        return log;
    }
}
