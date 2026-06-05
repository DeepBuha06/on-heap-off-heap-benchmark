# JVM Internals: Direct ByteBuffers

In standard Java, when you create an object, memory is allocated on the **JVM Heap**. The Heap is a contiguous block of memory managed strictly by the JVM. The operating system does not interact directly with objects on the heap.

## The Problem with On-Heap I/O

When Java needs to read or write data to a socket (TCP) or a file, the OS requires a contiguous block of memory in native space. 
If your data is stored on-heap (e.g., a `byte[]`), Java must:
1. Allocate a temporary buffer in native memory.
2. Copy the data from the heap `byte[]` to the native buffer.
3. Pass the native buffer to the OS system call (e.g., `send()`).

This extra memory copy degrades throughput and increases latency. Furthermore, the GC must track the on-heap `byte[]`.

## ByteBuffer.allocateDirect()

To solve this, Java NIO introduced `ByteBuffer.allocateDirect()`. 

When you call this method:
1. The JVM makes a system call to the OS (like `malloc` in C) to allocate a block of memory *outside* the JVM Heap.
2. This memory is located in **Native OS Memory**.
3. The JVM only retains a small "DirectByteBuffer" wrapper object on the heap, which holds the memory address (pointer) to the native memory block.

## Why Off-Heap is Faster for Systems Programming

1. **Zero-Copy I/O:** When you pass a DirectByteBuffer to a network socket, the OS can read directly from that memory address. There is no intermediate copying from the JVM heap to native memory.
2. **Invisible to GC:** The Garbage Collector only scans the JVM Heap. Since the 100MB chunk we allocate for our `OffHeapEngine` resides in native memory, the GC does not know or care about the data stored inside it. This means zero STW pauses related to tracking millions of cache entries.
3. **Contiguous Memory:** Standard Java objects suffer from memory fragmentation and pointer chasing. A Direct ByteBuffer allows us to read/write sequential bytes, maximizing CPU cache line utilization (L1/L2 cache hits).
