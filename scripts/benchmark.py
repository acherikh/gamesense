import urllib.request
import urllib.error
import json
import time
import statistics
import math

# Configuration
BASE_URL = "http://localhost:8080/api"
ITERATIONS = 50  # Number of requests per test
WARMUP = 5       # Warmup requests (discarded)

# Colors
GREEN = '\033[92m'
RESET = '\033[0m'
RED = '\033[91m'

class Benchmark:
    def __init__(self):
        self.token = None
        self.headers = {"Content-Type": "application/json"}
        self.admin_user = f"bench_admin_{int(time.time())}"
        self.test_game_id = None
        self.test_user_id = None

    def _request(self, method, endpoint, data=None):
        url = f"{BASE_URL}{endpoint}"
        req = urllib.request.Request(url, method=method)
        
        for k, v in self.headers.items():
            req.add_header(k, v)
            
        if data:
            json_data = json.dumps(data).encode('utf-8')
            req.add_header("Content-Length", len(json_data))
        else:
            json_data = None

        start = time.perf_counter()
        try:
            with urllib.request.urlopen(req, data=json_data) as response:
                payload = response.read().decode('utf-8')
                end = time.perf_counter()
                return (end - start) * 1000, json.loads(payload) if payload else {}
        except urllib.error.HTTPError as e:
            print(f"{RED}Error {e.code} on {endpoint}: {e.read().decode()}{RESET}")
            return None, None

    def setup(self):
        print(f"{GREEN}[SETUP] Initializing Benchmark Environment...{RESET}")
        
        # 1. Register Admin
        _, res = self._request("POST", "/auth/register", {
            "username": self.admin_user,
            "email": f"{self.admin_user}@test.com",
            "password": "password123",
            "bio": "Benchmark Bot"
        })
        self.test_user_id = res['id']
        print(f" - Registered User: {self.test_user_id}")

        # 2. Login
        _, res = self._request("POST", "/auth/login", {
            "username": self.admin_user,
            "password": "password123"
        })
        self.token = res['token']
        self.headers["Authorization"] = f"Bearer {self.token}"
        print(f" - Authenticated")

        # 3. Create Test Game
        _, res = self._request("POST", "/games", {
            "title": f"Benchmark Game {int(time.time())}",
            "genres": ["Action", "Benchmark"],
            "platforms": ["PC"],
            "releaseDate": "2024-01-01",
            "developer": "Bench Dev",
            "publisher": "Bench Pub"
        })
        self.test_game_id = res['id']
        print(f" - Created Game: {self.test_game_id}")
        print("-" * 50)

    def run_test(self, name, method, endpoint, payload=None):
        print(f"Running: {name:<30}", end="", flush=True)
        
        # Warmup
        for _ in range(WARMUP):
            self._request(method, endpoint, payload)
            
        latencies = []
        for _ in range(ITERATIONS):
            latency, _ = self._request(method, endpoint, payload)
            if latency is not None:
                latencies.append(latency)
            time.sleep(0.05) # Small sleep to prevent DoS-ing self

        avg = statistics.mean(latencies)
        p99 = statistics.quantiles(latencies, n=100)[98] # Approx P99
        print(f"| Avg: {avg:6.2f}ms | P99: {p99:6.2f}ms")
        
        return {
            "name": name,
            "avg": avg,
            "p99": p99,
            "min": min(latencies),
            "max": max(latencies)
        }

    def execute(self):
        self.setup()
        
        results = []
        
        # --- SIMPLE READS ---
        results.append(self.run_test(
            "Simple Read (Game Details)", 
            "GET", 
            f"/games/{self.test_game_id}"
        ))

        # --- WRITES ---
        results.append(self.run_test(
            "Write (Create Review)", 
            "POST", 
            "/reviews",
            {
                "gameId": self.test_game_id,
                "userId": self.test_user_id,
                "rating": 10,
                "content": "Benchmark Load Test",
                "timestamp": "2024-01-01T12:00:00Z"
            }
        ))
        
        # --- DUAL WRITE (Consistency Service) ---
        results.append(self.run_test(
            "Dual Write (Add to Library)", 
            "POST", 
            f"/users/{self.test_user_id}/games/{self.test_game_id}?status=PLAYING"
        ))

        # --- AGGREGATIONS ---
        results.append(self.run_test(
            "Agg: Hype Meter", 
            "GET", 
            "/analytics/trending?days=30&limit=10"
        ))
        
        results.append(self.run_test(
            "Agg: Team Win Rates", 
            "GET", 
            "/analytics/teams/performance"
        ))
        
        results.append(self.run_test(
            "Agg: Genre Trends", 
            "GET", 
            "/analytics/genres/trends"
        ))

        # --- GRAPH TRAVERSAL ---
        results.append(self.run_test(
            "Graph: Recommendations", 
            "GET", 
            f"/graph/recommendations/{self.test_user_id}"
        ))

        self.print_latex_table(results)

    def print_latex_table(self, results):
        print("\n" + "="*50)
        print("LATEX TABLE CODE")
        print("="*50)
        print(r"\begin{table}[H]")
        print(r"\centering")
        print(r"\begin{tabular}{|l|c|c|c|c|}")
        print(r"\hline")
        print(r"\textbf{Operation} & \textbf{Avg (ms)} & \textbf{Min (ms)} & \textbf{Max (ms)} & \textbf{P99 (ms)} \\ \hline")
        
        for r in results:
            name = r['name']
            # Escape underscores for Latex
            # name = name.replace("_", "\\_") 
            print(f"{name} & {r['avg']:.2f} & {r['min']:.2f} & {r['max']:.2f} & {r['p99']:.2f} \\\\ \\hline")
            
        print(r"\end{tabular}")
        print(r"\caption{System Performance Benchmarks (" + str(ITERATIONS) + r" iterations)}")
        print(r"\label{tab:system_benchmarks}")
        print(r"\end{table}")

if __name__ == "__main__":
    try:
        Benchmark().execute()
    except Exception as e:
        print(f"\n{RED}Benchmark Failed: {e}{RESET}")