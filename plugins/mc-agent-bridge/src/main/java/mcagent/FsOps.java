package mcagent;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.RandomAccessFile;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Sandboxed file operations confined to the server root directory.
 * Any path that escapes the root is rejected.
 */
public final class FsOps {

    private final File root;

    public FsOps(File root) {
        this.root = root;
    }

    public File getRoot() {
        return root;
    }

    private File resolve(String p) throws Exception {
        if (p == null || p.isEmpty()) p = ".";
        File f = new File(root, p);
        String canonical = f.getCanonicalPath();
        String rootCanon = root.getCanonicalPath();
        if (!canonical.equals(rootCanon) && !canonical.startsWith(rootCanon + File.separator)) {
            throw new IllegalArgumentException("path escapes server root: " + p);
        }
        return f;
    }

    public Map<String, Object> info(String p) throws Exception {
        File f = resolve(p);
        if (!f.exists()) return map("path", p, "exists", false);
        Map<String, Object> m = map("path", p, "exists", true, "name", f.getName(),
                "is_directory", f.isDirectory(), "size", f.length(),
                "last_modified", f.lastModified(), "readable", f.canRead(), "writable", f.canWrite());
        return m;
    }

    public Map<String, Object> list(String p) throws Exception {
        File f = resolve(p);
        if (!f.exists()) return map("path", p, "exists", false);
        if (f.isFile()) return map("path", p, "is_directory", false);
        Map<String, Object> m = map("path", p, "is_directory", true);
        List<Object> entries = new ArrayList<>();
        File[] children = f.listFiles();
        if (children != null) {
            Arrays.sort(children, Comparator.comparing(File::getName));
            for (File c : children) {
                Map<String, Object> e = new LinkedHashMap<>();
                e.put("name", c.getName());
                e.put("is_directory", c.isDirectory());
                e.put("size", c.length());
                e.put("last_modified", c.lastModified());
                entries.add(e);
            }
        }
        m.put("entries", entries);
        m.put("count", entries.size());
        return m;
    }

    public Map<String, Object> read(String p, int maxBytes) throws Exception {
        File f = resolve(p);
        if (!f.exists()) return map("path", p, "exists", false);
        if (f.isDirectory()) return map("path", p, "is_directory", true);
        long size = f.length();
        int limit = maxBytes <= 0 ? 1_000_000 : maxBytes;
        int toRead = (int) Math.min(size, limit);
        byte[] buf = new byte[toRead];
        try (RandomAccessFile raf = new RandomAccessFile(f, "r")) {
            if (toRead > 0) raf.readFully(buf);
        }
        String content = new String(buf, StandardCharsets.UTF_8);
        return map("path", p, "size", size, "truncated", size > limit, "content", content);
    }

    public Map<String, Object> write(String p, String content, boolean append) throws Exception {
        File f = resolve(p);
        File parent = f.getParentFile();
        if (parent != null) parent.mkdirs();
        try (Writer w = new OutputStreamWriter(new FileOutputStream(f, append), StandardCharsets.UTF_8)) {
            w.write(content == null ? "" : content);
        }
        return map("path", p, "written", true, "size", f.length());
    }

    public Map<String, Object> delete(String p) throws Exception {
        File f = resolve(p);
        if (!f.exists()) return map("path", p, "exists", false);
        boolean ok = f.isDirectory() ? deleteRecursively(f) : f.delete();
        return map("path", p, "deleted", ok);
    }

    public Map<String, Object> mkdir(String p) throws Exception {
        File f = resolve(p);
        boolean ok = f.mkdirs();
        return map("path", p, "created", ok || f.exists());
    }

    public Map<String, Object> copy(String src, String dst) throws Exception {
        File s = resolve(src);
        File d = resolve(dst);
        if (s.isDirectory()) copyDir(s, d);
        else copyFile(s, d);
        return map("src", src, "dst", dst, "copied", true);
    }

    public Map<String, Object> move(String src, String dst) throws Exception {
        File s = resolve(src);
        File d = resolve(dst);
        if (d.exists()) throw new IllegalArgumentException("destination exists: " + dst);
        boolean ok = s.renameTo(d);
        if (!ok) {
            if (s.isDirectory()) copyDir(s, d);
            else copyFile(s, d);
            deleteRecursively(s);
        }
        return map("src", src, "dst", dst, "moved", true);
    }

    private static void copyFile(File s, File d) throws Exception {
        File parent = d.getParentFile();
        if (parent != null) parent.mkdirs();
        try (InputStream in = new FileInputStream(s); OutputStream out = new FileOutputStream(d)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        }
    }

    private static void copyDir(File s, File d) throws Exception {
        d.mkdirs();
        File[] kids = s.listFiles();
        if (kids == null) return;
        for (File k : kids) {
            if (k.isDirectory()) copyDir(k, new File(d, k.getName()));
            else copyFile(k, new File(d, k.getName()));
        }
    }

    private static boolean deleteRecursively(File f) {
        File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) deleteRecursively(k);
        return f.delete();
    }

    private static Map<String, Object> map(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put(String.valueOf(kv[i]), kv[i + 1]);
        return m;
    }
}
