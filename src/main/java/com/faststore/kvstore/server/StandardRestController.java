package com.faststore.kvstore.server;

import com.faststore.kvstore.engine.OffHeapEngine;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/heap")
public class StandardRestController {

    private final OffHeapEngine offHeapEngine;
    private final AdminController adminController;

    public StandardRestController(OffHeapEngine offHeapEngine, AdminController adminController) {
        this.offHeapEngine = offHeapEngine;
        this.adminController = adminController;
    }

    @PostMapping
    public String put(@RequestParam String key, @RequestParam String value) {
        long start = System.nanoTime();
        offHeapEngine.put(key, value);
        adminController.recordRequest(System.nanoTime() - start);
        return "OK";
    }

    @GetMapping
    public String get(@RequestParam String key) {
        long start = System.nanoTime();
        String value = offHeapEngine.get(key);
        adminController.recordRequest(System.nanoTime() - start);
        return value != null ? value : "NOT_FOUND";
    }
}
