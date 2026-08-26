package mcagent;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Creates a zip archive of the server directory. */
public final class Backup {

    private Backup() {}

    public static File zip(File root, File destDir, String name) throws Exception {
        if (!destDir.exists()) destDir.mkdirs();
        File out = new File(destDir, name);
        String destDirCanon = destDir.getCanonicalPath();
        try (ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(out)))) {
            zos.setLevel(Deflater.DEFAULT_COMPRESSION);
            walk(root, root, zos, destDirCanon);
        }
        return out;
    }

    private static void walk(File dir, File root, ZipOutputStream zos, String destDirCanon) throws Exception {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            String canon = f.getCanonicalPath();
            if (canon.equals(destDirCanon) || canon.startsWith(destDirCanon + File.separator)) continue;
            String rel = root.toPath().relativize(f.toPath()).toString().replace('\\', '/');
            if (f.isDirectory()) {
                zos.putNextEntry(new ZipEntry(rel + "/"));
                zos.closeEntry();
                walk(f, root, zos, destDirCanon);
            } else {
                zos.putNextEntry(new ZipEntry(rel));
                try (InputStream in = new FileInputStream(f)) {
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = in.read(buf)) > 0) zos.write(buf, 0, n);
                }
                zos.closeEntry();
            }
        }
    }
}
