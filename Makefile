.PHONY: help build start stop restart logs clean init-mongo test health

# Default target
help:
	@echo "GameSense - Available Commands:"
	@echo ""
	@echo "  make start          - Start all services"
	@echo "  make stop           - Stop all services"
	@echo "  make restart        - Restart all services"
	@echo "  make logs           - View logs (Ctrl+C to exit)"
	@echo "  make init-mongo     - Initialize MongoDB replica set"
	@echo "  make health         - Check service health"
	@echo "  make test           - Run tests"
	@echo "  make clean          - Remove all containers and volumes"
	@echo "  make build          - Build all Docker images"
	@echo ""

# Generate MongoDB keyfile if it doesn't exist
keyfile:
	@if [ ! -f mongo-keyfile ]; then \
		echo "Generating mongo-keyfile..."; \
		openssl rand -base64 756 > mongo-keyfile; \
		chmod 400 mongo-keyfile; \
		sudo chown 999:999 mongo-keyfile || true; \
		echo "Keyfile generated."; \
	else \
		echo "mongo-keyfile already exists."; \
	fi

# Build Docker images
build:
	@echo "Building Docker images..."
	docker compose build

# Start all services
start: keyfile
	@echo "Starting GameSense services..."
	docker compose up -d
	@echo "Waiting for services to be ready..."
	@sleep 10
	@echo "Services started successfully!"
	@echo ""
	@echo "Access points:"
	@echo "  API: http://localhost:8080"
	@echo "  Swagger: http://localhost:8080/swagger-ui.html"
	@echo "  Neo4j: http://localhost:7474 (neo4j/password123)"
	@echo ""
	@echo "Run 'make init-mongo' to initialize MongoDB replica set"

# Stop all services
stop:
	@echo "Stopping GameSense services..."
	docker compose down
	@echo "Services stopped successfully!"

# Restart all services
restart: stop start

# View logs
logs:
	docker compose logs -f

# Initialize MongoDB replica set
init-mongo:
	@echo "Initializing MongoDB replica set..."
	@sleep 5
	docker exec -it mongo-primary bash /scripts/init-mongo-replica.sh
	@echo "MongoDB replica set initialized!"

# Check health of all services
health:
	@echo "Checking service health..."
	@echo ""
	@echo "MongoDB Primary:"
	@docker exec mongo-primary mongosh --quiet --eval "db.adminCommand('ping')" || echo "MongoDB not responding"
	@echo ""
	@echo "Neo4j:"
	@curl -s http://localhost:7474 > /dev/null && echo "Neo4j is healthy" || echo "Neo4j not responding"
	@echo ""
	@echo "Redis:"
	@docker exec redis redis-cli ping || echo "Redis not responding"
	@echo ""
	@echo "Application:"
	@curl -s http://localhost:8080/actuator/health || echo "Application not responding"

# Run tests
test:
	@echo "Running tests..."
	cd backend && mvn test

# Clean up everything
clean:
	@echo "Removing all containers and volumes..."
	docker compose down -v
	rm -f mongo-keyfile
	@echo "Cleanup complete!"

# Quick setup (first time)
setup: build start init-mongo
	@echo ""
	@echo "GameSense setup complete!"
	@echo ""
	@echo "Next steps:"
	@echo "  1. Wait ~30 seconds for data ingestion to start"
	@echo "  2. Visit http://localhost:8080/swagger-ui.html"
	@echo "  3. Try the trending games API: curl http://localhost:8080/api/analytics/trending"

# Development mode (rebuild and restart)
dev: build restart logs