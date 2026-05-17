package com.msaas.runtime;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.msaas.config.AppProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class MockStateStore {
    private static final String STATE_PREFIX = "msaas:state:";

    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redisTemplate;
    private final AppProperties properties;

    public MockStateStore(ObjectMapper objectMapper, ObjectProvider<StringRedisTemplate> redisTemplate, AppProperties properties) {
        this.objectMapper = objectMapper;
        this.redisTemplate = redisTemplate.getIfAvailable();
        this.properties = properties;
    }

    public void put(RuntimeSlot slot, String collectionKey, String id, Object value) {
        if (useRedis()) {
            redisTemplate.opsForHash().put(redisKey(slot, collectionKey), id, write(value));
            return;
        }
        slot.collection(collectionKey).put(id, value);
    }

    public Object get(RuntimeSlot slot, String collectionKey, String id) {
        if (useRedis()) {
            Object value = redisTemplate.opsForHash().get(redisKey(slot, collectionKey), id);
            return value == null ? null : read(String.valueOf(value));
        }
        return slot.collection(collectionKey).get(id);
    }

    public List<Object> values(RuntimeSlot slot, String collectionKey) {
        if (useRedis()) {
            return redisTemplate.opsForHash().values(redisKey(slot, collectionKey))
                    .stream()
                    .map(value -> read(String.valueOf(value)))
                    .toList();
        }
        return new ArrayList<>(slot.collection(collectionKey).values());
    }

    public boolean isEmpty(RuntimeSlot slot, String collectionKey) {
        if (useRedis()) {
            Long size = redisTemplate.opsForHash().size(redisKey(slot, collectionKey));
            return size == null || size == 0;
        }
        return slot.collection(collectionKey).isEmpty();
    }

    public void remove(RuntimeSlot slot, String collectionKey, String id) {
        if (useRedis()) {
            redisTemplate.opsForHash().delete(redisKey(slot, collectionKey), id);
            return;
        }
        slot.collection(collectionKey).remove(id);
    }

    public Map<String, Object> snapshot(RuntimeSlot slot) {
        if (!useRedis()) {
            return slot.snapshot();
        }
        Map<String, Object> snapshot = new LinkedHashMap<>();
        String prefix = STATE_PREFIX + slot.getInstanceId() + ":";
        for (String key : keys(prefix + "*")) {
            String collection = key.substring(prefix.length());
            Map<String, Object> items = new LinkedHashMap<>();
            redisTemplate.opsForHash().entries(key).forEach((field, value) -> items.put(String.valueOf(field), read(String.valueOf(value))));
            snapshot.put(collection, items);
        }
        return snapshot;
    }

    public void reset(RuntimeSlot slot) {
        if (!useRedis()) {
            slot.resetState();
            return;
        }
        String prefix = STATE_PREFIX + slot.getInstanceId() + ":";
        Set<String> keys = keys(prefix + "*");
        if (!keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    private Set<String> keys(String pattern) {
        Set<String> keys = redisTemplate.keys(pattern);
        return keys == null ? Collections.emptySet() : keys;
    }

    private boolean useRedis() {
        return redisTemplate != null && !properties.getRuntime().isEmbedded();
    }

    private String redisKey(RuntimeSlot slot, String collectionKey) {
        return STATE_PREFIX + slot.getInstanceId() + ":" + collectionKey;
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not serialize mock state", ex);
        }
    }

    private Object read(String value) {
        try {
            return objectMapper.readValue(value, new TypeReference<LinkedHashMap<String, Object>>() {
            });
        } catch (Exception ignored) {
            try {
                return objectMapper.readValue(value, Object.class);
            } catch (Exception ex) {
                return value;
            }
        }
    }
}
