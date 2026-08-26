package mcagent;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 * Folia / Paper aware scheduling helper.
 *
 * <p>On Folia the standard Bukkit scheduler's {@code runTask}/{@code callSyncMethod}
 * execute on the <em>global region</em> thread, which is allowed to read/write any
 * world or entity data. This keeps the plugin safe on both Paper and Folia without
 * needing the Folia-only scheduler classes at compile time.</p>
 */
public final class Schedulers {

    private static final boolean FOLIA;

    static {
        boolean f = false;
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            f = true;
        } catch (Throwable ignored) {
            // not Folia
        }
        FOLIA = f;
    }

    public static boolean isFolia() {
        return FOLIA;
    }

    /** Run {@code task} on the server (global region) thread and return its result. */
    public static <T> T sync(JavaPlugin plugin, Callable<T> task) throws Exception {
        if (Bukkit.getServer().isPrimaryThread()) {
            return task.call();
        }
        BukkitScheduler scheduler = Bukkit.getScheduler();
        Future<T> future = scheduler.callSyncMethod(plugin, task);
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("interrupted waiting for server thread", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception) throw (Exception) cause;
            if (cause instanceof RuntimeException) throw (RuntimeException) cause;
            throw new RuntimeException(cause);
        }
    }

    /** Run {@code task} on the server (global region) thread without a result. */
    public static void syncRun(JavaPlugin plugin, Runnable task) {
        if (Bukkit.getServer().isPrimaryThread()) {
            task.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }
}
