package com.faststore.kvstore.engine;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

@Component
public class OnHeapEngine implements StorageEngine {

    private final ConcurrentHashMap<String, String> cache = new ConcurrentHashMap<>();

    @Override
    public void put(String key, String value) {
        cache.put(key, value);
    }

    @Override
    public String get(String key) {
        return cache.get(key);
    }
}
