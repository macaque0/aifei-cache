package io.github.macaque0.aifei.cache.serializer;

public interface CacheSerializer {
    byte[] serialize(Object value);
    Object deserialize(byte[] bytes);
}
