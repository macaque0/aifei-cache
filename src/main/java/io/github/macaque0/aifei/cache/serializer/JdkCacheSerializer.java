package io.github.macaque0.aifei.cache.serializer;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

public class JdkCacheSerializer implements CacheSerializer {

    @Override
    public byte[] serialize(Object value) {
        if (value == null) {
            throw new IllegalArgumentException("Redis cache value can not be null");
        }
        if (!(value instanceof Serializable)) {
            throw new IllegalArgumentException("Redis cache value must implement Serializable: " + value.getClass().getName());
        }
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
                out.writeObject(value);
            }
            return bytes.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize cache value", e);
        }
    }

    @Override
    public Object deserialize(byte[] bytes) {
        if (bytes == null) {
            throw new IllegalArgumentException("bytes can not be null");
        }
        try {
            try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
                return in.readObject();
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize cache value", e);
        }
    }
}
