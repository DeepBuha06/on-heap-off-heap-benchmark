package com.faststore.kvstore.server;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

@RestController
@RequestMapping("/api/metrics")
public class AdminController {

    private final AtomicLong totalRequests = new AtomicLong(0);
    private final AtomicLong totalLatencyNs = new AtomicLong(0);

    public void recordRequest(long latencyNs) {
        totalRequests.incrementAndGet();
        totalLatencyNs.addAndGet(latencyNs);
    }

    @GetMapping
    public Map<String, Object> getMetrics() {
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        
        long reqs = totalRequests.get();
        long lat = totalLatencyNs.get();
        
        double avgLatencyMs = reqs > 0 ? (lat / (double) reqs) / 1_000_000.0 : 0.0;
        
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("totalRequests", reqs);
        metrics.put("averageLatencyMs", avgLatencyMs);
        metrics.put("heapUsedMb", memoryBean.getHeapMemoryUsage().getUsed() / (1024 * 1024));
        metrics.put("heapMaxMb", memoryBean.getHeapMemoryUsage().getMax() / (1024 * 1024));
        
        return metrics;
    }
}
