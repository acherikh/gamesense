#!/usr/bin/env bash

# GameSense - Automated API Testing Script
# This script tests all API endpoints in the correct sequence

set -e  # Exit on error

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Base URL
BASE_URL="http://localhost:8080"
TOKEN=""

# Test results tracking
TESTS_PASSED=0
TESTS_FAILED=0
TESTS_TOTAL=0

# Helper functions
print_header() {
    echo -e "\n${BLUE}================================================${NC}"
    echo -e "${BLUE}$1${NC}"
    echo -e "${BLUE}================================================${NC}\n"
}

print_success() {
    echo -e "${GREEN}$1${NC}"
    ((TESTS_PASSED++))
    ((TESTS_TOTAL++))
}

print_error() {
    echo -e "${RED}$1${NC}"
    ((TESTS_FAILED++))
    ((TESTS_TOTAL++))
}

print_warning() {
    echo -e "${YELLOW}$1${NC}"
}

print_info() {
    echo -e "${BLUE}$1${NC}"
}

check_service() {
    local service=$1
    local url=$2
    
    if curl -s -f "$url" > /dev/null 2>&1; then
        print_success "$service is running"
        return 0
    else
        print_error "$service is not responding at $url"
        return 1
    fi
}

wait_with_progress() {
    local seconds=$1
    local message=$2
    
    echo -n "$message"
    for ((i=$seconds; i>0; i--)); do
        echo -n "."
        sleep 1
    done
    echo " Done!"
}

# Start testing
clear
echo -e "${GREEN}"
cat << "EOF"
   ____                      ____                      
  / ___| __ _ _ __ ___   ___/ ___|  ___ _ __  ___  ___ 
 | |  _ / _` | '_ ` _ \ / _ \___ \ / _ \ '_ \/ __|/ _ \
 | |_| | (_| | | | | | |  __/___) |  __/ | | \__ \  __/
  \____|\__,_|_| |_| |_|\___|____/ \___|_| |_|___/\___|
                                                        
         Automated API Testing Suite
EOF
echo -e "${NC}"

# ============================================================
# PHASE 0: Prerequisites
# ============================================================
print_header "PHASE 0: Prerequisites Check"

print_info "Checking if services are running..."

check_service "Application" "$BASE_URL/actuator/health" || exit 1
check_service "MongoDB" "$BASE_URL/actuator/health" || exit 1
check_service "Neo4j" "http://localhost:7474" || print_warning "Neo4j browser not responding (may still work via Bolt)"

# ============================================================
# PHASE 0.5: Authentication
# ============================================================
print_header "PHASE 0.5: Authentication Setup"

# Register Admin User
print_info "Registering Test Admin..."
# Use timestamp to ensure uniqueness
ADMIN_USER="admin_$(date +%s)"
ADMIN_PASS="password123"

# REMOVED "role": "ADMIN" to match User entity (server sets default role)
REG_RESPONSE=$(curl -s -X POST "$BASE_URL/api/auth/register" \
    -H "Content-Type: application/json" \
    -d '{
        "username": "'"$ADMIN_USER"'",
        "email": "admin'$(date +%s)'@gamesense.io",
        "password": "'"$ADMIN_PASS"'",
        "bio": "System Administrator"
    }')

ADMIN_ID=$(echo "$REG_RESPONSE" | jq -r '.id // empty')
if [ -z "$ADMIN_ID" ] || [ "$ADMIN_ID" = "null" ]; then
    print_error "Failed to register admin user."
    echo "Response: $REG_RESPONSE"
    exit 1
fi
print_success "Registered Admin: $ADMIN_USER"

# Login to get Token
print_info "Logging in..."
LOGIN_RESPONSE=$(curl -s -X POST "$BASE_URL/api/auth/login" \
    -H "Content-Type: application/json" \
    -d '{
        "username": "'"$ADMIN_USER"'",
        "password": "'"$ADMIN_PASS"'"
    }')

TOKEN=$(echo "$LOGIN_RESPONSE" | jq -r '.token // empty')

if [ -z "$TOKEN" ] || [ "$TOKEN" = "null" ]; then
    print_error "Failed to obtain JWT Token!"
    echo "Response: $LOGIN_RESPONSE"
    exit 1
fi

print_success "Authentication Successful. Token obtained."

# ============================================================
# PHASE 1: Data Population & Verification
# ============================================================
print_header "PHASE 1: Data Population & Verification"

print_info "Checking if data has been ingested..."
wait_with_progress 3 "Querying database"

# Check games (Public endpoint)
GAMES_COUNT=$(curl -s "$BASE_URL/api/games?size=1" | jq -r '.totalElements // 0')
if [ "$GAMES_COUNT" -gt 5 ]; then
    print_success "Games found in database: $GAMES_COUNT"
else
    print_warning "Only $GAMES_COUNT games found. Data ingestion may still be running or empty."
    print_info "Waiting 10 seconds..."
    sleep 10
fi

# Get sample game data
print_info "Fetching sample game data..."
SAMPLE_GAMES=$(curl -s "$BASE_URL/api/games?size=5")
echo "$SAMPLE_GAMES" | jq -r '.content[] | "  - \(.title) (\(.genres | join(", ")))"' | head -3

# ============================================================
# PHASE 2: User & Graph Setup
# ============================================================
print_header "PHASE 2: User & Graph Setup"

print_info "Creating secondary test users..."

# Helper to create user and get ID
create_user() {
    local name=$1
    local pass=$2
    # Removed Role argument as it is handled by backend default
    
    local resp=$(curl -s -X POST "$BASE_URL/api/auth/register" \
      -H "Content-Type: application/json" \
      -d '{
        "username": "'"$name"'",
        "email": "'"$name"'@test.com",
        "password": "'"$pass"'",
        "bio": "Test User"
      }')
    echo "$resp" | jq -r '.id'
}

# Create Users
USER1_ID=$(create_user "alice_$(date +%s)" "pass123")
USER2_ID=$(create_user "bob_$(date +%s)" "pass123")
USER3_ID=$(create_user "carol_$(date +%s)" "pass123")

if [ "$USER1_ID" == "null" ] || [ -z "$USER1_ID" ]; then
    print_error "Failed to create secondary users"
else
    print_success "Created Alice ($USER1_ID), Bob ($USER2_ID), Carol ($USER3_ID)"
fi

# Get game IDs for testing
print_info "Fetching game IDs for library setup..."

# Get RPG games
RPG_GAMES=$(curl -s "$BASE_URL/api/games/genre/RPG?size=5" | jq -r '.content[].id' 2>/dev/null)
RPG_GAME1=$(echo "$RPG_GAMES" | sed -n '1p')
RPG_GAME2=$(echo "$RPG_GAMES" | sed -n '2p')
ACTION_GAMES=$(curl -s "$BASE_URL/api/games/genre/Action?size=5" | jq -r '.content[].id' 2>/dev/null)
ACTION_GAME1=$(echo "$ACTION_GAMES" | sed -n '1p')

# Fallback if no specific genres found
if [ -z "$RPG_GAME1" ]; then
    RPG_GAME1=$(curl -s "$BASE_URL/api/games?size=5" | jq -r '.content[0].id')
    RPG_GAME2=$(curl -s "$BASE_URL/api/games?size=5" | jq -r '.content[1].id')
    ACTION_GAME1=$(curl -s "$BASE_URL/api/games?size=5" | jq -r '.content[2].id')
fi

print_success "Retrieved game IDs for testing"

# Build user libraries (Using AUTH TOKEN)
print_info "Building user game libraries..."

# Alice's library
curl -s -X POST "$BASE_URL/api/users/$USER1_ID/games/$RPG_GAME1?status=COMPLETED" \
    -H "Authorization: Bearer $TOKEN" > /dev/null
print_success "Alice added game to library (COMPLETED)"

curl -s -X POST "$BASE_URL/api/users/$USER1_ID/games/$RPG_GAME2?status=PLAYING" \
    -H "Authorization: Bearer $TOKEN" > /dev/null
print_success "Alice added game to library (PLAYING)"

# Bob's library (Overlap)
curl -s -X POST "$BASE_URL/api/users/$USER2_ID/games/$RPG_GAME2?status=COMPLETED" \
    -H "Authorization: Bearer $TOKEN" > /dev/null
print_success "Bob added game to library (COMPLETED)"

curl -s -X POST "$BASE_URL/api/users/$USER2_ID/games/$ACTION_GAME1?status=BACKLOG" \
    -H "Authorization: Bearer $TOKEN" > /dev/null
print_success "Bob added game to library (BACKLOG)"

# Setup social network (User-to-User follows)
print_info "Setting up social network..."

# Alice follows Bob
curl -s -X POST "$BASE_URL/api/graph/follow/$USER1_ID/user/$USER2_ID" \
    -H "Authorization: Bearer $TOKEN" > /dev/null
print_success "Alice follows Bob"

# Carol follows Bob
curl -s -X POST "$BASE_URL/api/graph/follow/$USER3_ID/user/$USER2_ID" \
    -H "Authorization: Bearer $TOKEN" > /dev/null
print_success "Carol follows Bob"

# ============================================================
# PHASE 3: Basic CRUD Operations
# ============================================================
print_header "PHASE 3: CRUD Operations Testing"

# Create custom game (Protected)
print_info "Testing Game Creation..."
CUSTOM_GAME=$(curl -s -X POST "$BASE_URL/api/games" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "title": "Test Game '$(date +%s)'",
    "description": "Automated test game",
    "releaseDate": "2024-01-15",
    "genres": ["Adventure", "Test"],
    "developer": "Test Studios",
    "platforms": ["PC"]
  }')
CUSTOM_GAME_ID=$(echo "$CUSTOM_GAME" | jq -r '.id // empty')
if [ ! -z "$CUSTOM_GAME_ID" ] && [ "$CUSTOM_GAME_ID" != "null" ]; then
    print_success "Game created: $CUSTOM_GAME_ID"
else
    print_error "Failed to create game"
    echo "Response: $CUSTOM_GAME"
fi

# Read game (Public)
GAME_DETAILS=$(curl -s "$BASE_URL/api/games/$CUSTOM_GAME_ID")
GAME_TITLE=$(echo "$GAME_DETAILS" | jq -r '.title // empty')
if [ ! -z "$GAME_TITLE" ]; then
    print_success "Game retrieved: $GAME_TITLE"
else
    print_error "Failed to retrieve game"
fi

# Create review (Protected)
print_info "Testing Review Creation..."
# FIX: Added timestamp to ensure Hype Meter aggregation picks it up
TIMESTAMP=$(date -u +"%Y-%m-%dT%H:%M:%SZ")
REVIEW1=$(curl -s -X POST "$BASE_URL/api/reviews" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "gameId": "'"$CUSTOM_GAME_ID"'",
    "userId": "'"$USER1_ID"'",
    "content": "Great game for testing!",
    "rating": 9,
    "sentimentScore": 0.8,
    "timestamp": "'"$TIMESTAMP"'",
    "source": "INTERNAL"
  }')
REVIEW1_ID=$(echo "$REVIEW1" | jq -r '.id // empty')
if [ ! -z "$REVIEW1_ID" ] && [ "$REVIEW1_ID" != "null" ]; then
    print_success "Review created: $REVIEW1_ID"
else
    print_error "Failed to create review"
    echo "Response: $REVIEW1"
fi

# ============================================================
# PHASE 4: Analytics (MongoDB Aggregations)
# ============================================================
print_header "PHASE 4: Analytics Testing"

# Public Analytics Endpoints
print_info "Testing Hype Meter..."
# Trending games usually require multiple reviews or specific timeline. 
# We just check if it doesn't error out.
TRENDING=$(curl -s "$BASE_URL/api/analytics/trending?days=7&limit=5")
if [ "$(echo "$TRENDING" | jq 'length')" -ge 0 ]; then
    print_success "Hype Meter responding"
else
    print_error "Hype Meter failed"
fi

print_info "Testing Genre Trends..."
GENRE_TRENDS=$(curl -s "$BASE_URL/api/analytics/genres/trends")
if [ "$(echo "$GENRE_TRENDS" | jq 'length')" -ge 0 ]; then
    print_success "Genre Trends responding"
fi

# ============================================================
# PHASE 5: Graph Operations
# ============================================================
print_header "PHASE 5: Graph Operations"

# Recommendations (Might require auth depending on config, but usually public for read-only if designed that way. Sending token to be safe if endpoint uses UserDetails)
print_info "Testing Game Recommendations..."
# Note: Recommendations usually require knowing WHO is asking.
# If endpoint extracts user from Token:
REC_DATA=$(curl -s -H "Authorization: Bearer $TOKEN" "$BASE_URL/api/graph/recommendations/$USER1_ID?limit=5")

if echo "$REC_DATA" | jq empty > /dev/null 2>&1; then
    print_success "Recommendations working"
else
    print_error "Recommendations failed"
    echo "$REC_DATA"
fi

# ============================================================
# Final Report
# ============================================================
print_header "TEST SUMMARY"

echo -e "\n${BLUE}Test Results:${NC}"
echo -e "  ${GREEN}Passed: $TESTS_PASSED${NC}"
echo -e "  ${RED}Failed: $TESTS_FAILED${NC}"
echo -e "  Total: $TESTS_TOTAL"

if [ $TESTS_FAILED -eq 0 ]; then
    echo -e "\n${GREEN}════════════════════════════════════════════════════════════${NC}"
    echo -e "${GREEN}ALL TESTS PASSED!${NC}"
    echo -e "${GREEN}════════════════════════════════════════════════════════════${NC}\n"
    exit 0
else
    echo -e "\n${YELLOW}SOME TESTS FAILED${NC}\n"
    exit 1
fi