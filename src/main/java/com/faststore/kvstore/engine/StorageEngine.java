package com.faststore.kvstore.engine;

public interface StorageEngine {
    void put(String key, String value);
    String get(String key);
}
