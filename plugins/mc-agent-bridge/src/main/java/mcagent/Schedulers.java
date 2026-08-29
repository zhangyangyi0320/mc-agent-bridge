package mcagent;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Folia / Paper aware scheduling helper.
 *
 * <p>On Folia the standard Bukkit scheduler refuses to schedule a synchronous task
 * from a non-main thread, and forbids any sync scheduling during plugin startup.
 * Folia instead exposes {@code GlobalRegionScheduler} (the global region thread is the
 * main thread) which can be scheduled from any thread; we use it via reflection so the
 * plugin still compiles against the plain paper-api jar.</p>
 *
 * <p>On Paper / Spigot we use {@link org.bukkit.scheduler.BukkitScheduler#callSyncMethod}
 * which already worked across versions, falling back to running directly when we are
 * already on the main thread.</p>
 *
 * <p>IMPORTANT: never call a blocking {@link #sync} (or await a region task) from the
 * main thread &mdash; the region task would run on the main thread and you would deadlock.
 * Fire-and-forget work from {@code onEnable} must use {@link #submit}.</p>
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

    /** Run {@code task} on the main (global region) thread and return its result. */
    @SuppressWarnings("unchecked")
    public static <T> T sync(JavaPlugin plugin, Callable<T> task) throws Exception {
        if (Bukkit.getServer().isPrimaryThread()) {
            return task.call();
        }
        if (FOLIA) {
            return foliaSync(plugin, task);
        }
        // Paper / Spigot: schedule the task on the main thread and wait for it.
        // (callSyncMethod exists on most versions but has inconsistent behaviour on
        //  older servers such as 1.8.8, so we use runTask + a latch everywhere.)
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<T> ref = new AtomicReference<>();
        final AtomicReference<Throwable> err = new AtomicReference<>();
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                ref.set(task.call());
            } catch (Throwable t) {
                err.set(t);
            } finally {
                latch.countDown();
            }
        });
        latch.await();
        Throwable t = err.get();
        if (t != null) {
            if (t instanceof Exception) throw (Exception) t;
            if (t instanceof RuntimeException) throw (RuntimeException) t;
            throw new RuntimeException(t);
        }
        return ref.get();
    }

    /** Run {@code task} on the main (global region) thread without a result. */
    public static void syncRun(JavaPlugin plugin, Runnable task) {
        try {
            sync(plugin, () -> {
                task.run();
                return null;
            });
        } catch (RuntimeException re) {
            throw re;
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    /** Fire-and-forget: schedule {@code task} on the main (global region) thread. Never blocks. */
    public static void submit(JavaPlugin plugin, Runnable task) {
        if (FOLIA) {
            Object scheduler = foliaScheduler();
            Consumer<Object> consumer = h -> task.run();
            try {
                scheduler.getClass()
                        .getMethod("run", org.bukkit.plugin.Plugin.class, Consumer.class)
                        .invoke(scheduler, plugin, consumer);
            } catch (Throwable t) {
                throw new RuntimeException(t);
            }
            return;
        }
        task.run();
    }

    /** Resolve the Folia region scheduler, trying the various method names across Folia versions. */
    private static Object foliaScheduler() {
        for (String m : new String[]{"getGlobalRegionScheduler", "getRegionScheduler", "getAsyncScheduler"}) {
            try {
                return Class.forName("org.bukkit.Bukkit").getMethod(m).invoke(null);
            } catch (Throwable ignored) {
            }
        }
        throw new RuntimeException("Folia region scheduler not found (getGlobalRegionScheduler/getRegionScheduler/getAsyncScheduler)");
    }

    private static <T> T foliaSync(JavaPlugin plugin, Callable<T> task) throws Exception {
        Object scheduler = foliaScheduler();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<T> ref = new AtomicReference<>();
        AtomicReference<Throwable> err = new AtomicReference<>();
        Consumer<Object> consumer = h -> {
            try {
                ref.set(task.call());
            } catch (Throwable t) {
                err.set(t);
            } finally {
                latch.countDown();
            }
        };
        scheduler.getClass()
                .getMethod("run", org.bukkit.plugin.Plugin.class, Consumer.class)
                .invoke(scheduler, plugin, consumer);
        latch.await();
        Throwable t = err.get();
        if (t != null) {
            if (t instanceof Exception) throw (Exception) t;
            if (t instanceof RuntimeException) throw (RuntimeException) t;
            throw new RuntimeException(t);
        }
        return ref.get();
    }
}
