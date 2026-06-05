# 🎯 FastStore: Interview Preparation Guide

When you put "Zero-GC Off-Heap Key-Value Store" on your resume, you **will** be grilled by senior engineers. This document contains the exact questions they will ask you, and the highly technical answers you need to give to prove you engineered this system yourself.

---

### 1. "Why didn't you just use `ConcurrentHashMap`?"
**Your Answer:** 
"While `ConcurrentHashMap` is highly optimized for standard applications, it allocates Java objects (like `Node` or `Map.Entry`) for every single insert. At 50,000 writes per second, this generates massive amounts of garbage. The JVM Garbage Collector will eventually have to pause the application to clean up this memory (Stop-The-World pause). In a low-latency environment like HFT, a 50ms pause is unacceptable. I bypassed the JVM entirely by writing raw bytes into a direct `ByteBuffer` and using a primitive `AtomicLongArray` for the index, guaranteeing zero object allocation on the critical path."

---

### 2. "How do you handle Hash Collisions in your custom map?"
**Your Answer:** 
"I implemented **Linear Probing** (Open Addressing). If a key hashes to a slot that is already occupied by a different key's pointer, the algorithm uses a `while` loop to check the next sequential slot `(slot + 1) % tableSize` until it finds an empty slot (`0`). I chose Linear Probing over Separate Chaining (Linked Lists) because Linked Lists require allocating new node objects (violating the zero-GC requirement) and they ruin CPU cache locality. Arrays provide perfect cache-line prefetching."

---

### 3. "What happens when your 100MB memory buffer gets full?"
**Your Answer:** 
"I implemented a Lock-Free Ring Buffer. When the atomic `currentOffset` exceeds the maximum capacity, I use a Compare-And-Swap (CAS) operation to reset the offset back to `0`. The engine seamlessly begins overwriting the oldest data. Because the `get()` method compares the actual bytes of the key in memory, if a client requests an old key that has been overwritten by the Ring Buffer, the engine correctly sees a mismatch and returns `null` (Cache Miss) instead of returning corrupted data."

---

### 4. "How did you achieve durability without killing latency?"
**Your Answer:** 
"I used **Memory-Mapped Files (mmap)** via `FileChannel.map()`. This maps the physical `kvstore_data.bin` file directly into the application's virtual address space. When my engine writes to the buffer, it's actually writing to RAM. The OS kernel's page cache automatically flushes these dirty pages to disk asynchronously in the background. This gave me Write-Ahead Log (WAL) durability with exactly zero disk I/O latency on the critical path."

---

### 5. "How do you ensure Thread-Safety without using `synchronized`?"
**Your Answer:** 
"Locks and `synchronized` blocks cause context switching, which destroys latency. I relied entirely on hardware-level **Compare-And-Swap (CAS)** instructions. 
1. Space allocation is done via `AtomicInteger.getAndAdd()`.
2. Claiming an index slot is done via `AtomicLongArray.compareAndSet()`. 
If two threads collide, the loser simply spins in a tight `while(true)` loop and tries again. Because the operations are microsecond-fast, the spin-lock contention is negligible."

---

### 6. "Why did you build the Python Microservice with gRPC in the Workout Tracker, but not here?"
**Your Answer:** 
"They solve different problems. The Workout Tracker was a full-stack, distributed web application where asynchronous analysis made sense. *This* project is a hyper-optimized infrastructure component—it is the database itself. In systems engineering, adding a gRPC network hop to an in-memory database would add 1-2 milliseconds of latency, completely defeating the purpose of the nanosecond-optimized off-heap engine."

---

### 7. "If I boot up your server, the latency starts at 0.01ms and continuously drops to 0.003ms over the first few minutes. Why?"
**Your Answer:** 
"That is the JVM 'Warming Up', which happens for two main systems-level reasons:
1. **JIT Compilation (C1/C2):** When the Java server starts, it executes code using a slower interpreter. When the JVM notices my `OffHeapEngine.put()` method being called 50,000 times a second, it flags it as a 'Hot Spot'. The C2 compiler kicks in asynchronously and aggressively compiles my Java bytecode down to raw, highly-optimized native machine code. As the C2 compiler optimizes more of the execution path, latency plummets.
2. **CPU Cache Locality (L1/L2 Caches):** Initially, the `AtomicLongArray` index and `MappedByteBuffer` are loaded from Main Memory (RAM), which takes ~100 nanoseconds to read. As the engine repeatedly hammers the exact same memory addresses, the physical CPU pins those addresses into the ultra-fast L1/L2 hardware caches located directly on the CPU die. Accessing the L1 cache takes ~1 nanosecond.

**Follow-up Note:** In a real HFT environment, we never deploy a Java engine right when the market opens. We always run a 'Warmup Phase' where we blast millions of fake payloads through the engine for 5 minutes before the opening bell. This forces the JIT compiler to generate the native C2 machine code and packs the CPU L1 caches, ensuring that our latency is already bottomed out the exact millisecond real trading begins."
