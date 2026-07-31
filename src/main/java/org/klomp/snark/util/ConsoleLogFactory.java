package org.klomp.snark.util;

import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

import org.klomp.snark.spi.Log;
import org.klomp.snark.spi.LogFactory;

/**
 *  Default {@link LogFactory} — writes to {@code System.err} with
 *  timestamps, level and tag. Levels at or below {@link Log#INFO}
 *  are logged; DEBUG is suppressed by default.
 */
public class ConsoleLogFactory implements LogFactory {

    private static final String[] LEVELS = {"CRIT", "ERROR", "WARN ", "INFO ", "DEBUG"};

    private final PrintStream _out;
    private final int _level;
    private final Map<String, ConsoleLog> _logs = new ConcurrentHashMap<String, ConsoleLog>();

    public ConsoleLogFactory() {
        this(System.err, Log.INFO);
    }

    public ConsoleLogFactory(PrintStream out, int level) {
        _out = out;
        _level = level;
    }

    @Override
    public Log getLog(Class<?> clazz) {
        return getLog(clazz.getName());
    }

    @Override
    public Log getLog(String name) {
        ConsoleLog log = _logs.get(name);
        if (log == null) {
            log = new ConsoleLog(name);
            _logs.put(name, log);
        }
        return log;
    }

    private final class ConsoleLog implements Log {
        private final String _tag;
        private final SimpleDateFormat _fmt = new SimpleDateFormat("HH:mm:ss.SSS");

        ConsoleLog(String name) {
            int idx = name.lastIndexOf('.');
            _tag = idx >= 0 ? name.substring(idx + 1) : name;
        }

        @Override
        public boolean shouldLog(int level) {
            return level <= _level;
        }

        @Override
        public boolean shouldInfo() {
            return Log.INFO <= _level;
        }

        @Override
        public void log(int level, String message) {
            if (shouldLog(level)) {
                synchronized (_out) {
                    _out.println(_fmt.format(new Date()) + " [" + LEVELS[level] + "] " + _tag + ": " + message);
                }
            }
        }

        @Override
        public void log(int level, String message, Throwable t) {
            if (shouldLog(level)) {
                synchronized (_out) {
                    _out.println(_fmt.format(new Date()) + " [" + LEVELS[level] + "] " + _tag + ": " + message);
                    t.printStackTrace(_out);
                }
            }
        }
    }
}
