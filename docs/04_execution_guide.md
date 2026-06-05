# Execution Guide

Follow these steps to run the benchmark and observe the latency differences between the Spring/Heap architecture and the Netty/Off-Heap architecture.

## 1. Start the Server

You need to run the Spring Boot application. Since we have configured an embedded Netty server to start alongside Spring Boot, running the main application will start both services.

From the `kvstore` directory, run:

```bash
# Windows
.\mvnw.cmd spring-boot:run

# Linux/Mac
./mvnw spring-boot:run
```

You should see logs indicating that Tomcat started on port `8080` and the Netty Server started on port `8081`.

## 2. Run the Benchmark

Once the server is running, open a **new terminal window** and navigate to the `kvstore` directory.

Run the Python benchmark script:

```bash
python benchmark.py
```

The script will:
1. Send 5,000 PUT requests and 5,000 GET requests to the Spring (8080) endpoint.
2. Send 5,000 PUT requests and 5,000 GET requests to the Netty (8081) endpoint.
3. Calculate and display the P50 (median) and P99 (tail) latencies.

*Note: Depending on your hardware and OS, the Netty off-heap implementation should demonstrate lower and more stable latencies, particularly in the P99 tail, as it avoids HTTP overhead, thread switching, and GC pauses.*
