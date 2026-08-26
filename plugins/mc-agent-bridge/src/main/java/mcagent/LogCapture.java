package mcagent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * Bounded, timestamped ring buffer of recent server log lines so the API can
 * return logs for a given time window. Attaches a {@link Handler} to the root logger.
 */
public class LogCapture {

    public static final class Entry {
        public final long time;
        public final String level;
        public final String logger;
        public final String message;

        Entry(long time, String level, String logger, String message) {
            this.time = time;
            this.level = level;
            this.logger = logger;
            this.message = message;
        }

        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("time", time);
            m.put("level", level);
            m.put("logger", logger);
            m.put("message", message);
            return m;
        }
    }

    private final int max;
    private final Deque<Entry> entries = new ArrayDeque<>();
    private final Handler handler = new Handler() {
        @Override
        public void publish(LogRecord record) {
            Entry e = new Entry(record.getMillis(), record.getLevel().getName(),
                    record.getLoggerName(), record.getMessage());
            synchronized (entries) {
                entries.addLast(e);
                while (entries.size() > max) entries.removeFirst();
            }
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    };

    public LogCapture(int max) {
        this.max = Math.max(1, max);
    }

    public void attach() {
        Logger.getLogger("").addHandler(handler);
    }

    public void detach() {
        Logger.getLogger("").removeHandler(handler);
    }

    public List<Map<String, Object>> query(long since, long until, String minLevel, String contains, int limit) {
        synchronized (entries) {
            List<Map<String, Object>> out = new ArrayList<>();
            int min = levelValue(minLevel);
            for (Entry e : entries) {
                if (since > 0 && e.time < since) continue;
                if (until > 0 && e.time > until) continue;
                if (min >= 0 && levelValue(e.level) < min) continue;
                if (contains != null && !contains.isEmpty()
                        && !e.message.toLowerCase().contains(contains.toLowerCase())) continue;
                out.add(e.toMap());
            }
            if (limit > 0 && out.size() > limit) out = out.subList(out.size() - limit, out.size());
            return out;
        }
    }

    public List<Map<String, Object>> recent(int n) {
        synchronized (entries) {
            List<Map<String, Object>> out = new ArrayList<>();
            for (Entry e : entries) out.add(e.toMap());
            if (n > 0 && out.size() > n) out = out.subList(out.size() - n, out.size());
            return out;
        }
    }

    private static int levelValue(String l) {
        if (l == null || l.isEmpty()) return -1;
        switch (l.toUpperCase()) {
            case "OFF": return 1000;
            case "SEVERE": case "ERROR": return 1000;
            case "WARNING": case "WARN": return 900;
            case "INFO": return 800;
            case "CONFIG": return 700;
            case "FINE": return 500;
            case "FINER": return 400;
            case "FINEST": return 300;
            default: return -1;
        }
    }
}
