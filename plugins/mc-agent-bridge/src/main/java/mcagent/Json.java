package mcagent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal JSON serializer + parser (no external dependencies).
 */
public final class Json {

    private Json() {}

    public static String toJson(Object o) {
        StringBuilder sb = new StringBuilder();
        write(o, sb);
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static void write(Object o, StringBuilder sb) {
        if (o == null) { sb.append("null"); return; }
        if (o instanceof String) { sb.append('"').append(escape((String) o)).append('"'); return; }
        if (o instanceof Number || o instanceof Boolean) { sb.append(o.toString()); return; }
        if (o instanceof Map) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<Object, Object> e : ((Map<Object, Object>) o).entrySet()) {
                if (!first) sb.append(',');
                first = false;
                sb.append('"').append(escape(String.valueOf(e.getKey()))).append("\":");
                write(e.getValue(), sb);
            }
            sb.append('}');
            return;
        }
        if (o instanceof Collection) {
            sb.append('[');
            boolean first = true;
            for (Object v : (Collection<?>) o) {
                if (!first) sb.append(',');
                first = false;
                write(v, sb);
            }
            sb.append(']');
            return;
        }
        if (o.getClass().isArray()) {
            sb.append('[');
            int len = java.lang.reflect.Array.getLength(o);
            boolean first = true;
            for (int k = 0; k < len; k++) {
                if (!first) sb.append(',');
                first = false;
                write(java.lang.reflect.Array.get(o, k), sb);
            }
            sb.append(']');
            return;
        }
        sb.append('"').append(escape(o.toString())).append('"');
    }

    private static String escape(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                default:
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
            }
        }
        return sb.toString();
    }

    // ---- minimal parser (objects, arrays, strings, numbers, bool, null) ----

    @SuppressWarnings("unchecked")
    public static Object parse(String s) {
        return new Parser(s).parse();
    }

    private static final class Parser {
        private final String s;
        private int i;

        Parser(String s) { this.s = s; }

        Object parse() { skipWs(); return value(); }

        private void skipWs() {
            while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
        }

        private Object value() {
            skipWs();
            char c = s.charAt(i);
            if (c == '{') return obj();
            if (c == '[') return arr();
            if (c == '"') return str();
            if (c == 't' || c == 'f') return bool();
            if (c == 'n') { i += 4; return null; }
            return num();
        }

        private Map<String, Object> obj() {
            Map<String, Object> m = new LinkedHashMap<>();
            i++;
            skipWs();
            if (s.charAt(i) == '}') { i++; return m; }
            while (true) {
                skipWs();
                String k = str();
                skipWs();
                i++; // ':'
                Object v = value();
                m.put(k, v);
                skipWs();
                char c = s.charAt(i);
                if (c == ',') { i++; continue; }
                if (c == '}') { i++; break; }
                throw new RuntimeException("bad json at " + i);
            }
            return m;
        }

        private List<Object> arr() {
            List<Object> a = new ArrayList<>();
            i++;
            skipWs();
            if (s.charAt(i) == ']') { i++; return a; }
            while (true) {
                a.add(value());
                skipWs();
                char c = s.charAt(i);
                if (c == ',') { i++; continue; }
                if (c == ']') { i++; break; }
                throw new RuntimeException("bad json at " + i);
            }
            return a;
        }

        private String str() {
            i++; // opening quote
            StringBuilder sb = new StringBuilder();
            while (i < s.length()) {
                char c = s.charAt(i++);
                if (c == '"') return sb.toString();
                if (c == '\\') {
                    char e = s.charAt(i++);
                    switch (e) {
                        case '"': sb.append('"'); break;
                        case '\\': sb.append('\\'); break;
                        case '/': sb.append('/'); break;
                        case 'n': sb.append('\n'); break;
                        case 't': sb.append('\t'); break;
                        case 'r': sb.append('\r'); break;
                        case 'b': sb.append('\b'); break;
                        case 'f': sb.append('\f'); break;
                        case 'u': sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16)); i += 4; break;
                        default: sb.append(e);
                    }
                } else sb.append(c);
            }
            return sb.toString();
        }

        private Object num() {
            int start = i;
            while (i < s.length()) {
                char c = s.charAt(i);
                if ("0123456789+-.eE".indexOf(c) >= 0) i++;
                else break;
            }
            String n = s.substring(start, i);
            if (n.contains(".") || n.contains("e") || n.contains("E")) return Double.parseDouble(n);
            try { return Long.parseLong(n); } catch (Exception e) { return Double.parseDouble(n); }
        }

        private boolean bool() {
            if (s.charAt(i) == 't') { i += 4; return true; }
            i += 5;
            return false;
        }
    }
}
