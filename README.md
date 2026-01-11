Here is a simple `README.md` for your project.


# GameSense

Advanced Analytics & Social Platform for Esports.

## Getting Started

Follow these steps to build, run, and verify the platform.

### 1. Setup & Installation
Build the containers and initialize the database replica sets.

```bash
make setup
```

*Note: This process may take a few minutes as it downloads dependencies and sets up the MongoDB cluster.*

### 2. Verify System Health

Check if all services (Backend, MongoDB, Neo4j, Redis) are running correctly.

```bash
make health
```

### 3. Run Automated Tests

Execute the end-to-end integration test suite to validate API functionality and performance.

```bash
./scripts/test-api.sh
```

---

## Accessible Services

Once the system is up, you can access the following services in your browser:

| Service | URL | Description |
| --- | --- | --- |
| **Web Application** | [http://localhost](https://www.google.com/search?q=http://localhost) | Main User Interface (Nginx) |
| **Backend API** | [http://localhost:8080/swagger-ui/index.html](https://www.google.com/search?q=http://localhost:8080/swagger-ui/index.html) | API Documentation (Swagger) |
| **Neo4j Browser** | [http://localhost:7474](https://www.google.com/search?q=http://localhost:7474) | Graph Database Interface |
| **System Health** | [http://localhost:8080/actuator/health](https://www.google.com/search?q=http://localhost:8080/actuator/health) | Backend Status Check |

---

## Key Analytics Endpoints (Aggregation Pipelines)

These endpoints trigger the MongoDB aggregation pipelines and return JSON results:

    Hype Meter: http://localhost:8080/api/analytics/trending

        Calculates trending games based on review velocity and rating.

    Genre Trends: http://localhost:8080/api/analytics/genres/trends

        Analyzes dominant genres over time.

    Team Win Rates: http://localhost:8080/api/analytics/teams/performance

        Computes esports team performance across all matches.

    Sentiment Analysis: http://localhost:8080/api/analytics/sentiment

        Aggregates sentiment scores from user reviews.

### Credentials

* **Neo4j:** `neo4j` / `password123`
* **API:** Register a new user via the Swagger UI or use the test script credentials.