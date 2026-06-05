# Dual-Architecture Comparison Map

This diagram illustrates the two distinct data paths implemented in this application.

```text
                     CLIENT (benchmark.py)
                              |
          -----------------------------------------
          |                                       |
    [HTTP / 8080]                           [TCP / 8081]
          |                                       |
+-------------------+                   +-------------------+
|   SPRING BOOT     |                   |       NETTY       |
|    (Tomcat)       |                   |   (Event Loop)    |
+-------------------+                   +-------------------+
| Thread Pool       |                   | NioEventLoopGroup |
| (Thread-per-Req)  |                   | (1 Thread / Core) |
+---------+---------+                   +---------+---------+
          |                                       |
   [StandardRestController]                [NettyServerHandler]
          |                                       |
          |                                       |
+---------v---------+                   +---------v---------+
|   OnHeapEngine    |                   |   OffHeapEngine   |
| (ConcurrentHashMap|                   | (DirectByteBuffer)|
|  <String, String>)|                   |                   |
+-------------------+                   +-------------------+
          |                                       |
     [JVM HEAP]                             [NATIVE OS MEMORY]
- Managed by GC                           - Unmanaged by GC
- Subject to STW pauses                   - No GC Pauses
- Object Overhead                         - Raw Bytes Only
```

## Key Differences

### Spring Boot Path (Left)
- **Transport:** HTTP over TCP. Incurs HTTP parsing overhead.
- **Concurrency:** Thread-per-request. Relies on OS thread scheduling.
- **Storage:** On-heap `ConcurrentHashMap`. Data is managed by the JVM Garbage Collector.
- **Data Format:** Standard Java `String` objects.

### Netty Path (Right)
- **Transport:** Raw TCP. Custom, lightweight framing (`PUT:key:value`).
- **Concurrency:** Asynchronous Event Loop (Reactor Pattern). Non-blocking I/O.
- **Storage:** Off-heap `ByteBuffer`. Allocated directly via OS (`malloc`/`mmap`).
- **Data Format:** Raw byte sequences tracked by offset and length.
