package com.faststore.kvstore;

import com.faststore.kvstore.engine.OffHeapEngine;
import com.faststore.kvstore.engine.OnHeapEngine;
import org.openjdk.jmh.annotations.*;

import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 3, time = 2)
@Fork(1)
public class BenchmarkRunner {

    private OnHeapEngine onHeapEngine;
    private OffHeapEngine offHeapEngine;
    private String testKey;
    private String testValue;

    @Setup
    public void setup() {
        onHeapEngine = new OnHeapEngine();
        offHeapEngine = new OffHeapEngine();
        testKey = "jmh_test_key";
        testValue = "jmh_test_value_data_blob";
        
        // Pre-populate for GET tests
        onHeapEngine.put(testKey, testValue);
        offHeapEngine.put(testKey, testValue);
    }

    @Benchmark
    public void testOnHeapPut() {
        onHeapEngine.put(testKey, testValue);
    }

    @Benchmark
    public void testOffHeapPut() {
        offHeapEngine.put(testKey, testValue);
    }

    @Benchmark
    public String testOnHeapGet() {
        return onHeapEngine.get(testKey);
    }

    @Benchmark
    public String testOffHeapGet() {
        return offHeapEngine.get(testKey);
    }
}
