package io.github.macaque0.aifei.cache;

import cn.aifei.aop.Aop;
import cn.aifei.aop.Inject;
import cn.aifei.util.PropKit;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class MemoryCacheTest {

    @Test
    public void basicOps() {
        for (String type : localBackends()) {
            Cache cache = create(type);
            cache.put("k", "v");
            assertEquals(type, "v", cache.get("k"));
            assertTrue(type, cache.exists("k"));
            cache.remove("k");
            assertNull(type, cache.get("k"));
            cache.close();
        }
    }

    @Test
    public void ttlWorks() throws Exception {
        for (String type : localBackends()) {
            Cache cache = create(type);
            cache.put("k", "v", 1);
            assertEquals(type, "v", cache.get("k"));
            Thread.sleep(1100);
            assertNull(type, cache.get("k"));
            cache.close();
        }
    }

    @Test
    public void getOrSetWorks() {
        for (String type : localBackends()) {
            Cache cache = create(type);
            AtomicInteger n = new AtomicInteger();
            assertEquals(type, "v", cache.getOrSet("k", () -> {
                n.incrementAndGet();
                return "v";
            }));
            assertEquals(type, "v", cache.getOrSet("k", () -> {
                n.incrementAndGet();
                return "x";
            }));
            assertEquals(type, 1, n.get());
            cache.close();
        }
    }

    @Test
    public void getOrSetCachesNullWhenEnabled() {
        for (String type : localBackends()) {
            Cache cache = create(new CacheConfig()
                    .setType(type)
                    .setDefaultName("test_null_" + type + "_" + System.nanoTime())
                    .setCacheNull(true)
                    .setMaxSize(100));
            AtomicInteger n = new AtomicInteger();
            assertNull(type, cache.getOrSet("missing", () -> {
                n.incrementAndGet();
                return null;
            }));
            assertNull(type, cache.getOrSet("missing", () -> {
                n.incrementAndGet();
                return "x";
            }));
            assertTrue(type, cache.exists("missing"));
            assertEquals(type, 1, n.get());
            cache.close();
        }
    }

    @Test
    public void getOrSetRunsSupplierOnceUnderContention() throws Exception {
        for (String type : localBackends()) {
            Cache cache = create(type);
            ExecutorService executor = Executors.newFixedThreadPool(8);
            CountDownLatch start = new CountDownLatch(1);
            AtomicInteger n = new AtomicInteger();
            List<Future<String>> futures = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return cache.getOrSet("hot", () -> {
                        n.incrementAndGet();
                        sleep(50);
                        return "v";
                    });
                }));
            }
            start.countDown();
            for (Future<String> future : futures) {
                assertEquals(type, "v", future.get());
            }
            assertEquals(type, 1, n.get());
            executor.shutdownNow();
            cache.close();
        }
    }

    @Test
    public void incrAndDecrWork() {
        for (String type : localBackends()) {
            Cache cache = create(type);
            assertEquals(type, 1L, cache.incr("n"));
            assertEquals(type, 6L, cache.incr("n", "x", 6));
            assertEquals(type, 4L, cache.decr("n", "x", 2));
            cache.close();
        }
    }

    @Test
    public void counterOverflowIsRejected() {
        for (String type : localBackends()) {
            Cache cache = create(type);
            cache.put("n", Long.MAX_VALUE);
            try {
                cache.incr("n");
                fail(type + " should reject counter overflow");
            } catch (IllegalStateException expected) {
                assertTrue(expected.getMessage().contains("counter overflow"));
            } finally {
                cache.close();
            }
        }
    }

    @Test
    public void incrUsesDefaultTtl() throws Exception {
        for (String type : localBackends()) {
            Cache cache = create(new CacheConfig()
                    .setType(type)
                    .setDefaultName("test_counter_ttl_" + type + "_" + System.nanoTime())
                    .setDefaultTtlSeconds(1)
                    .setMaxSize(100));
            assertEquals(type, 1L, cache.incr("n"));
            assertTrue(type, cache.exists("n"));
            Thread.sleep(1100);
            assertFalse(type, cache.exists("n"));
            cache.close();
        }
    }

    @Test
    public void incrRejectsNonNumericValue() {
        for (String type : localBackends()) {
            Cache cache = create(type);
            cache.put("n", "not-a-number");
            try {
                cache.incr("n");
                fail(type + " should reject non-numeric counters");
            } catch (IllegalStateException expected) {
                assertTrue(expected.getMessage().contains("not an integer counter"));
            } finally {
                cache.close();
            }
        }
    }

    @Test
    public void invalidArgumentsAreRejected() {
        for (String type : localBackends()) {
            Cache cache = create(type);
            assertIllegalArgument(() -> cache.get(null));
            assertIllegalArgument(() -> cache.get("", "k"));
            assertIllegalArgument(() -> cache.put("k", "v", -1));
            assertIllegalArgument(() -> cache.getOrSet("k", null));
            assertIllegalArgument(() -> cache.decr("default", "n", -1));
            cache.close();
        }
        assertIllegalArgument(() -> create(new CacheConfig().setDefaultName(" ")));
        assertIllegalArgument(() -> create(new CacheConfig().setMaxSize(-1)));
    }

    @Test
    public void memoryHonorsMaxSize() {
        Cache cache = create(new CacheConfig()
                .setType("memory")
                .setDefaultName("test_memory_size_" + System.nanoTime())
                .setMaxSize(2));
        cache.put("a", 1);
        cache.put("b", 2);
        cache.put("c", 3);

        int existing = 0;
        existing += cache.exists("a") ? 1 : 0;
        existing += cache.exists("b") ? 1 : 0;
        existing += cache.exists("c") ? 1 : 0;
        assertEquals(2, existing);
        cache.close();
    }

    @Test
    public void defaultPluginStartsWithoutPropKit() {
        PropKit.clear();
        CachePlugin plugin = new CachePlugin();
        try {
            plugin.start();
            CacheKit.put("k", "v");
            assertEquals("v", CacheKit.get("k"));
        } finally {
            plugin.stop();
        }
    }

    @Test
    public void pluginStartIsIdempotentAndStopDoesNotClearNewerCache() {
        CachePlugin first = new CachePlugin(new CacheConfig()
                .setType("memory")
                .setDefaultName("plugin_first_" + System.nanoTime()));
        CachePlugin second = new CachePlugin(new CacheConfig()
                .setType("memory")
                .setDefaultName("plugin_second_" + System.nanoTime()));
        try {
            first.start();
            Cache firstCache = CacheKit.getCache();
            first.start();
            assertSame(firstCache, CacheKit.getCache());

            second.start();
            Cache secondCache = CacheKit.getCache();
            assertNotSame(firstCache, secondCache);

            first.stop();
            assertSame(secondCache, CacheKit.getCache());
            CacheKit.put("k", "v");
            assertEquals("v", CacheKit.get("k"));
        } finally {
            first.stop();
            second.stop();
        }
    }

    @Test
    public void cacheCanBeInjectedAndDelegatesToCurrentCacheKitInstance() {
        CachePlugin first = new CachePlugin(new CacheConfig()
                .setType("memory")
                .setDefaultName("inject_first_" + System.nanoTime()));
        CachePlugin second = new CachePlugin(new CacheConfig()
                .setType("memory")
                .setDefaultName("inject_second_" + System.nanoTime()));
        try {
            first.start();
            CacheConsumer consumer = Aop.inject(new CacheConsumer());
            consumer.cache.put("k", "v");
            assertEquals("v", CacheKit.get("k"));
            assertEquals("v", consumer.cache.get("k"));

            first.stop();
            second.start();
            assertNull(consumer.cache.get("k"));
            consumer.cache.put("k2", "v2");
            assertEquals("v2", CacheKit.get("k2"));
        } finally {
            first.stop();
            second.stop();
        }
    }

    private String[] localBackends() {
        return new String[]{"memory", "caffeine", "ehcache"};
    }

    private Cache create(String type) {
        return create(new CacheConfig()
                .setType(type)
                .setDefaultName("test_" + type + "_" + System.nanoTime())
                .setMaxSize(100));
    }

    private Cache create(CacheConfig config) {
        return new CachePlugin(config).createCache(config);
    }

    private void assertIllegalArgument(Runnable action) {
        try {
            action.run();
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public static class CacheConsumer {
        @Inject
        Cache cache;
    }
}
