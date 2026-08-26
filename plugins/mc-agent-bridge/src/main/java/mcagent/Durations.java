package mcagent;

/** Parses short duration strings like "30m", "1h", "10s", "2d", "500ms". */
public final class Durations {

    private Durations() {}

    public static long parse(String s) {
        if (s == null || s.isEmpty()) return -1;
        s = s.trim().toLowerCase();
        int i = 0;
        while (i < s.length() && Character.isDigit(s.charAt(i))) i++;
        if (i == 0) throw new IllegalArgumentException("invalid duration: " + s);
        long num = Long.parseLong(s.substring(0, i));
        String unit = s.substring(i).trim();
        switch (unit) {
            case "ms": return num;
            case "s": case "sec": case "": return num * 1000;
            case "m": case "min": return num * 60_000;
            case "h": case "hr": return num * 3_600_000;
            case "d": case "day": case "days": return num * 86_400_000;
            default: throw new IllegalArgumentException("unknown duration unit: " + unit);
        }
    }
}
