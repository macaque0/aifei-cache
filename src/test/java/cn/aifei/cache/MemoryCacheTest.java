package cn.aifei.cache;

import org.junit.Test;

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
    public void incrAndDecrWork() {
        for (String type : localBackends()) {
            Cache cache = create(type);
            assertEquals(type, 1L, cache.incr("n"));
            assertEquals(type, 6L, cache.incr("n", "x", 6));
            assertEquals(type, 4L, cache.decr("n", "x", 2));
            cache.close();
        }
    }

    private String[] localBackends() {
        return new String[]{"memory", "caffeine", "ehcache"};
    }

    private Cache create(String type) {
        CacheConfig config = new CacheConfig()
                .setType(type)
                .setDefaultName("test_" + type + "_" + System.nanoTime())
                .setMaxSize(100);
        return new CachePlugin(config).createCache(config);
    }
}
