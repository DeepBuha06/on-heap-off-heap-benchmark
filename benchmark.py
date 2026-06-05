import http.client
import socket
import time
import urllib.parse
import statistics

# Configuration
NUM_REQUESTS = 10000
KEY_PREFIX = "testkey_"
VAL_PREFIX = "testval_"

def test_spring_heap(num_requests):
    latencies = []
    
    # Pre-create connection for keep-alive
    conn = http.client.HTTPConnection("localhost", 8080)
    
    # Warm up / testing PUT
    for i in range(num_requests):
        key = f"{KEY_PREFIX}{i}"
        val = f"{VAL_PREFIX}{i}"
        
        start = time.perf_counter()
        
        # We're just testing the latency, so GET is sufficient for a simple metric after some PUTs.
        # Let's do 50% PUT and 50% GET for a mixed workload.
        if i % 2 == 0:
            params = urllib.parse.urlencode({'key': key, 'value': val})
            headers = {"Content-type": "application/x-www-form-urlencoded", "Accept": "text/plain"}
            conn.request("POST", "/api/heap", params, headers)
            response = conn.getresponse()
            response.read()
        else:
            conn.request("GET", f"/api/heap?key={key}")
            response = conn.getresponse()
            response.read()
            
        end = time.perf_counter()
        latencies.append((end - start) * 1000) # Convert to ms
        
    conn.close()
    return latencies

def test_netty_offheap(num_requests):
    latencies = []
    
    # Pre-create TCP connection
    s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    s.connect(("localhost", 8081))
    
    for i in range(num_requests):
        key = f"{KEY_PREFIX}{i}"
        val = f"{VAL_PREFIX}{i}"
        
        start = time.perf_counter()
        
        if i % 2 == 0:
            msg = f"PUT:{key}:{val}\n"
            s.sendall(msg.encode('utf-8'))
            resp = s.recv(1024)
        else:
            msg = f"GET:{key}\n"
            s.sendall(msg.encode('utf-8'))
            resp = s.recv(1024)
            
        end = time.perf_counter()
        latencies.append((end - start) * 1000) # Convert to ms
        
    s.close()
    return latencies

def print_summary(spring_latencies, netty_latencies):
    spring_p50 = statistics.median(spring_latencies)
    spring_p99 = statistics.quantiles(spring_latencies, n=100)[98]
    
    netty_p50 = statistics.median(netty_latencies)
    netty_p99 = statistics.quantiles(netty_latencies, n=100)[98]

    print("\n# Benchmark Summary")
    print("| Metric | Spring + Heap (8080) | Netty + Off-Heap (8081) |")
    print("|---|---|---|")
    print(f"| P50 Latency | {spring_p50:.4f} ms | {netty_p50:.4f} ms |")
    print(f"| P99 Latency | {spring_p99:.4f} ms | {netty_p99:.4f} ms |")
    print("\nNote: The first few requests may include JIT compilation overhead. For true micro-benchmarking, a tool like JMH is recommended.")

if __name__ == "__main__":
    print(f"Starting Benchmark: {NUM_REQUESTS} requests per architecture (50% PUT, 50% GET)")
    
    print("Testing Spring Boot (Heap)...")
    spring_latencies = test_spring_heap(NUM_REQUESTS)
    
    print("Testing Netty (Off-Heap)...")
    netty_latencies = test_netty_offheap(NUM_REQUESTS)
    
    print_summary(spring_latencies, netty_latencies)
