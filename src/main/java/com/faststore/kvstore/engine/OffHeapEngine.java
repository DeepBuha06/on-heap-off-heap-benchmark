package com.faststore.kvstore.engine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLongArray;

/**
 * OffHeapEngine implements a zero-GC, open-addressing concurrent hash map.
 * Data is stored in a direct ByteBuffer (off-heap) to avoid JVM Garbage Collection pauses.
 * Keys and values are serialized directly into native memory.
 */
@Component
public class OffHeapEngine implements StorageEngine {

    private static final Logger log = LoggerFactory.getLogger(OffHeapEngine.class);

    @Value("${kvstore.memory-capacity-mb:100}")
    private int memoryCapacityMb;

    @Value("${kvstore.table-size:200000}")
    private int tableSize;

    @Value("${kvstore.data-file:kvstore_data.bin}")
    private String dataFile;

    private static final int HEADER_SIZE = 6; // 2 bytes for key length, 4 bytes for value length

    private MappedByteBuffer memory;
    private AtomicInteger currentOffset;
    private AtomicLongArray hashTable;
    private int capacityBytes;

    @PostConstruct
    public void init() {
        this.capacityBytes = memoryCapacityMb * 1024 * 1024;
        this.currentOffset = new AtomicInteger(0);
        this.hashTable = new AtomicLongArray(tableSize);

        try {
            log.info("Initializing OffHeapEngine with {} MB capacity and {} table size.", memoryCapacityMb, tableSize);
            
            // Create parent directories if they don't exist (useful for Docker volume mounts)
            java.nio.file.Path path = Paths.get(dataFile);
            if (path.getParent() != null) {
                java.nio.file.Files.createDirectories(path.getParent());
            }

            try (FileChannel channel = FileChannel.open(path, 
                    StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE)) {
                this.memory = channel.map(FileChannel.MapMode.READ_WRITE, 0, capacityBytes);
            }
            
            // Rebuild index from WAL
            rebuildIndex();
            
            log.info("OffHeapEngine initialization complete.");
        } catch (Exception e) {
            log.error("Failed to map persistence file", e);
            throw new RuntimeException("Failed to map persistence file", e);
        }
    }

    private void rebuildIndex() {
        log.info("Scanning Write-Ahead Log to rebuild index...");
        int offset = 0;
        int recovered = 0;
        
        while (offset < capacityBytes - HEADER_SIZE) {
            short keyLen = memory.getShort(offset);
            if (keyLen <= 0 || keyLen > 1024) break; // Reached end of valid data or corrupted
            
            int valLen = memory.getInt(offset + 2);
            if (valLen < 0 || offset + HEADER_SIZE + keyLen + valLen > capacityBytes) break;
            
            byte[] keyBytes = new byte[keyLen];
            for (int i = 0; i < keyLen; i++) {
                keyBytes[i] = memory.get(offset + HEADER_SIZE + i);
            }
            
            String key = new String(keyBytes, StandardCharsets.UTF_8);
            insertIntoHashTable(key, offset);
            
            recovered++;
            offset += HEADER_SIZE + keyLen + valLen;
        }
        
        currentOffset.set(offset);
        log.info("Successfully recovered {} keys from WAL. Current offset: {}", recovered, offset);
    }

    @PreDestroy
    public void shutdown() {
        log.info("Shutting down OffHeapEngine. Forcing memory mapped file to disk...");
        if (memory != null) {
            memory.force();
        }
        log.info("Shutdown complete.");
    }

    @Override
    public void put(String key, String value) {
        byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
        byte[] valBytes = value.getBytes(StandardCharsets.UTF_8);

        int entryLength = HEADER_SIZE + keyBytes.length + valBytes.length;
        int offset;

        while (true) {
            int current = currentOffset.get();
            int next = current + entryLength;
            
            if (next > capacityBytes) {
                log.warn("Memory capacity exceeded! Wrapping Ring Buffer to 0. Old data will be overwritten.");
                if (currentOffset.compareAndSet(current, entryLength)) {
                    offset = 0;
                    break;
                }
            } else {
                if (currentOffset.compareAndSet(current, next)) {
                    offset = current;
                    break;
                }
            }
        }

        memory.putShort(offset, (short) keyBytes.length);
        memory.putInt(offset + 2, valBytes.length);
        
        for (int i = 0; i < keyBytes.length; i++) {
            memory.put(offset + HEADER_SIZE + i, keyBytes[i]);
        }
        
        for (int i = 0; i < valBytes.length; i++) {
            memory.put(offset + HEADER_SIZE + keyBytes.length + i, valBytes[i]);
        }

        insertIntoHashTable(key, offset);
    }
    
    private void insertIntoHashTable(String key, int offset) {
        byte[] targetKeyBytes = key.getBytes(StandardCharsets.UTF_8);
        int hash = Math.abs(key.hashCode());
        int slot = hash % tableSize;

        while (true) {
            long existingOffset = hashTable.get(slot);
            if (existingOffset == 0) {
                if (hashTable.compareAndSet(slot, 0, offset)) {
                    break;
                }
            } else {
                // Check if this slot already belongs to the same key
                short keyLen = memory.getShort((int) existingOffset);
                boolean match = (keyLen == targetKeyBytes.length);
                
                if (match) {
                    for (int i = 0; i < keyLen; i++) {
                        if (memory.get((int) existingOffset + HEADER_SIZE + i) != targetKeyBytes[i]) {
                            match = false;
                            break;
                        }
                    }
                }
                
                if (match) {
                    // Update the existing slot pointer to the new memory offset (in-place update)
                    hashTable.set(slot, offset);
                    break;
                }

                // Collision with a DIFFERENT key, probe next slot
                slot = (slot + 1) % tableSize;
            }
        }
    }

    @Override
    public String get(String key) {
        byte[] targetKeyBytes = key.getBytes(StandardCharsets.UTF_8);
        int hash = Math.abs(key.hashCode());
        int slot = hash % tableSize;

        while (true) {
            long offset = hashTable.get(slot);
            if (offset == 0) {
                return null;
            }

            short keyLen = memory.getShort((int) offset);
            boolean match = (keyLen == targetKeyBytes.length);
            
            if (match) {
                for (int i = 0; i < keyLen; i++) {
                    if (memory.get((int) offset + HEADER_SIZE + i) != targetKeyBytes[i]) {
                        match = false;
                        break;
                    }
                }
            }

            if (match) {
                int valLen = memory.getInt((int) offset + 2);
                byte[] valBytes = new byte[valLen];
                for (int i = 0; i < valLen; i++) {
                    valBytes[i] = memory.get((int) offset + HEADER_SIZE + keyLen + i);
                }
                return new String(valBytes, StandardCharsets.UTF_8);
            }

            slot = (slot + 1) % tableSize;
        }
    }
}

