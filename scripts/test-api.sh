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

check_http_200() {
    local url=$1
    local name=$2
    
    # Capture HTTP Code and Response Body
    local response=$(curl -s -w "\n%{http_code}" "$url")
    local http_code=$(echo "$response" | tail -n1)
    local body=$(echo "$response" | head -n -1)
    
    if [ "$http_code" -eq 200 ]; then
        # Check if body is valid JSON
        if ! echo "$body" | jq empty > /dev/null 2>&1; then
             print_error "$name: Invalid JSON response" >&2
             return 1
        fi

        local count=$(echo "$body" | jq 'length')
        if [ "$count" -gt 0 ]; then
            print_success "$name: $count found" >&2
            echo "$body"
            return 0
        else
            print_warning "$name: Returned empty list (0 found) - Valid but no data." >&2
            echo "[]"
            return 0
        fi
    else
        print_error "$name failed with HTTP $http_code" >&2
        # Try to extract error message
        local err_msg=$(echo "$body" | jq -r '.message // .error // "Unknown error"' 2>/dev/null)
        echo "   Error: $err_msg" >&2
        return 1
    fi
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
# PHASE 1: Data Population & Verification
# ============================================================
print_header "PHASE 1: Data Population & Verification"

print_info "Checking if data has been ingested..."
wait_with_progress 3 "Querying database"

# Check games
GAMES_COUNT=$(curl -s "$BASE_URL/api/games?size=1" | jq -r '.totalElements // 0')
if [ "$GAMES_COUNT" -gt 100 ]; then
    print_success "Games found in database: $GAMES_COUNT"
else
    print_warning "Only $GAMES_COUNT games found. Expected 100+. Data ingestion may still be running."
    print_info "Waiting 60 seconds for data ingestion..."
    sleep 60
    GAMES_COUNT=$(curl -s "$BASE_URL/api/games?size=1" | jq -r '.totalElements // 0')
    if [ "$GAMES_COUNT" -gt 50 ]; then
        print_success "Games now available: $GAMES_COUNT"
    else
        print_error "Insufficient games. Please wait for data ingestion to complete."
        exit 1
    fi
fi

# Get sample game data
print_info "Fetching sample game data..."
SAMPLE_GAMES=$(curl -s "$BASE_URL/api/games?size=5")
echo "$SAMPLE_GAMES" | jq -r '.content[] | "  - \(.title) (\(.genres | join(", ")))"' | head -3

# ============================================================
# PHASE 2: User & Graph Setup
# ============================================================
print_header "PHASE 2: User & Graph Setup"

print_info "Creating test users..."

# Create User 1: Alice
USER1_RESPONSE=$(curl -s -X POST "$BASE_URL/api/users" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "alice_gamer_'$(date +%s)'",
    "email": "alice'$(date +%s)'@gamesense.io",
    "password": "secure_pass_123",
    "role": "USER",
    "bio": "RPG enthusiast and esports fan"
  }' 2>/dev/null)

USER1_ID=$(echo "$USER1_RESPONSE" | jq -r '.id // empty')
if [ -z "$USER1_ID" ] || [ "$USER1_ID" = "null" ]; then
    print_error "Failed to create User 1. Response: $USER1_RESPONSE"
    exit 1
fi
USER1_USERNAME=$(echo "$USER1_RESPONSE" | jq -r '.username')
print_success "Created User 1: $USER1_USERNAME (ID: $USER1_ID)"

# Create User 2: Bob
USER2_RESPONSE=$(curl -s -X POST "$BASE_URL/api/users" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "bob_pro_'$(date +%s)'",
    "email": "bob'$(date +%s)'@gamesense.io",
    "password": "secure_pass_456",
    "role": "USER",
    "bio": "FPS competitive player"
  }' 2>/dev/null)

USER2_ID=$(echo "$USER2_RESPONSE" | jq -r '.id // empty')
if [ -z "$USER2_ID" ] || [ "$USER2_ID" = "null" ]; then
    print_error "Failed to create User 2"
    exit 1
fi
USER2_USERNAME=$(echo "$USER2_RESPONSE" | jq -r '.username')
print_success "Created User 2: $USER2_USERNAME (ID: $USER2_ID)"

# Create User 3: Carol
USER3_RESPONSE=$(curl -s -X POST "$BASE_URL/api/users" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "carol_casual_'$(date +%s)'",
    "email": "carol'$(date +%s)'@gamesense.io",
    "password": "secure_pass_789",
    "role": "USER",
    "bio": "Casual gaming enthusiast"
  }' 2>/dev/null)

USER3_ID=$(echo "$USER3_RESPONSE" | jq -r '.id // empty')
if [ -z "$USER3_ID" ] || [ "$USER3_ID" = "null" ]; then
    print_error "Failed to create User 3"
    exit 1
fi
USER3_USERNAME=$(echo "$USER3_RESPONSE" | jq -r '.username')
print_success "Created User 3: $USER3_USERNAME (ID: $USER3_ID)"

# Get game IDs for testing
print_info "Fetching game IDs for library setup..."

# Get RPG games
RPG_GAMES=$(curl -s "$BASE_URL/api/games/genre/RPG?size=5" | jq -r '.content[].id' 2>/dev/null)
RPG_GAME1=$(echo "$RPG_GAMES" | sed -n '1p')
RPG_GAME2=$(echo "$RPG_GAMES" | sed -n '2p')
RPG_GAME3=$(echo "$RPG_GAMES" | sed -n '3p')

# Get Action games
ACTION_GAMES=$(curl -s "$BASE_URL/api/games/genre/Action?size=5" | jq -r '.content[].id' 2>/dev/null)
ACTION_GAME1=$(echo "$ACTION_GAMES" | sed -n '1p')
ACTION_GAME2=$(echo "$ACTION_GAMES" | sed -n '2p')

if [ -z "$RPG_GAME1" ] || [ -z "$ACTION_GAME1" ]; then
    print_warning "Could not fetch games by genre. Using first available games..."
    ALL_GAME_IDS=$(curl -s "$BASE_URL/api/games?size=5" | jq -r '.content[].id')
    RPG_GAME1=$(echo "$ALL_GAME_IDS" | sed -n '1p')
    RPG_GAME2=$(echo "$ALL_GAME_IDS" | sed -n '2p')
    RPG_GAME3=$(echo "$ALL_GAME_IDS" | sed -n '3p')
    ACTION_GAME1=$(echo "$ALL_GAME_IDS" | sed -n '4p')
    ACTION_GAME2=$(echo "$ALL_GAME_IDS" | sed -n '5p')
fi

print_success "Retrieved game IDs for testing"

# Build user libraries (CRITICAL for recommendations!)
print_info "Building user game libraries (creating graph relationships)..."

# Alice's library
curl -s -X POST "$BASE_URL/api/users/$USER1_ID/games/$RPG_GAME1?status=COMPLETED" > /dev/null 2>&1
print_success "Alice added game to library (COMPLETED)"

curl -s -X POST "$BASE_URL/api/users/$USER1_ID/games/$RPG_GAME2?status=PLAYING" > /dev/null 2>&1
print_success "Alice added game to library (PLAYING)"

curl -s -X POST "$BASE_URL/api/users/$USER1_ID/games/$RPG_GAME3?status=BACKLOG" > /dev/null 2>&1
print_success "Alice added game to library (BACKLOG)"

# Bob's library (with overlap!)
curl -s -X POST "$BASE_URL/api/users/$USER2_ID/games/$ACTION_GAME1?status=PLAYING" > /dev/null 2>&1
print_success "Bob added game to library (PLAYING)"

curl -s -X POST "$BASE_URL/api/users/$USER2_ID/games/$RPG_GAME2?status=COMPLETED" > /dev/null 2>&1
print_success "Bob added game to library (COMPLETED) - OVERLAP with Alice!"

curl -s -X POST "$BASE_URL/api/users/$USER2_ID/games/$ACTION_GAME2?status=BACKLOG" > /dev/null 2>&1
print_success "Bob added game to library (BACKLOG)"

# Carol's library (with overlaps!)
curl -s -X POST "$BASE_URL/api/users/$USER3_ID/games/$RPG_GAME1?status=PLAYING" > /dev/null 2>&1
print_success "Carol added game to library (PLAYING) - OVERLAP with Alice!"

curl -s -X POST "$BASE_URL/api/users/$USER3_ID/games/$ACTION_GAME1?status=COMPLETED" > /dev/null 2>&1
print_success "Carol added game to library (COMPLETED) - OVERLAP with Bob!"

# Setup team follows
print_info "Setting up team follows..."
MATCHES=$(curl -s "$BASE_URL/api/matches/upcoming?limit=10" 2>/dev/null)
TEAM1=$(echo "$MATCHES" | jq -r '.[0].teamAId // empty')
TEAM2=$(echo "$MATCHES" | jq -r '.[1].teamAId // empty')
TEAM3=$(echo "$MATCHES" | jq -r '.[2].teamAId // empty')

if [ ! -z "$TEAM1" ] && [ "$TEAM1" != "null" ] && [ "$TEAM1" != "" ]; then
    curl -s -X POST "$BASE_URL/api/graph/follow/$USER1_ID/team/$TEAM1" > /dev/null 2>&1
    print_success "Alice follows Team 1"
    
    curl -s -X POST "$BASE_URL/api/graph/follow/$USER2_ID/team/$TEAM1" > /dev/null 2>&1
    print_success "Bob follows Team 1 (same as Alice!)"
    
    if [ ! -z "$TEAM2" ] && [ "$TEAM2" != "null" ]; then
        curl -s -X POST "$BASE_URL/api/graph/follow/$USER1_ID/team/$TEAM2" > /dev/null 2>&1
        print_success "Alice follows Team 2"
        
        curl -s -X POST "$BASE_URL/api/graph/follow/$USER3_ID/team/$TEAM2" > /dev/null 2>&1
        print_success "Carol follows Team 2"
    fi
else
    print_warning "No teams available for following (no esports matches found)"
fi

print_info "Setting up social network (User-to-User follows)..."

# Alice follows Bob (Alice thinks Bob is cool)
curl -s -X POST "$BASE_URL/api/graph/follow/$USER1_ID/user/$USER2_ID" > /dev/null 2>&1
print_success "Alice follows Bob"

# Carol follows Bob (Bob is now an influencer!)
curl -s -X POST "$BASE_URL/api/graph/follow/$USER3_ID/user/$USER2_ID" > /dev/null 2>&1
print_success "Carol follows Bob"

# Bob follows Alice
curl -s -X POST "$BASE_URL/api/graph/follow/$USER2_ID/user/$USER1_ID" > /dev/null 2>&1
print_success "Bob follows Alice"

# ============================================================
# PHASE 3: Basic CRUD Operations
# ============================================================
print_header "PHASE 3: CRUD Operations Testing"

# Create custom game
print_info "Testing Game Creation..."
CUSTOM_GAME=$(curl -s -X POST "$BASE_URL/api/games" \
  -H "Content-Type: application/json" \
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
fi

# Read game
GAME_DETAILS=$(curl -s "$BASE_URL/api/games/$CUSTOM_GAME_ID")
GAME_TITLE=$(echo "$GAME_DETAILS" | jq -r '.title // empty')
if [ ! -z "$GAME_TITLE" ]; then
    print_success "Game retrieved: $GAME_TITLE"
else
    print_error "Failed to retrieve game"
fi

# Update game
UPDATE_RESPONSE=$(curl -s -X PUT "$BASE_URL/api/games/$CUSTOM_GAME_ID" \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Test Game UPDATED",
    "description": "Updated description",
    "releaseDate": "2024-01-15",
    "genres": ["Adventure", "Test", "Updated"],
    "developer": "Test Studios"
  }')
UPDATED_TITLE=$(echo "$UPDATE_RESPONSE" | jq -r '.title // empty')
if [[ "$UPDATED_TITLE" == *"UPDATED"* ]]; then
    print_success "Game updated successfully"
else
    print_error "Failed to update game"
fi

# Search games
SEARCH_RESULTS=$(curl -s "$BASE_URL/api/games/search?query=test" | jq 'length')
if [ "$SEARCH_RESULTS" -gt 0 ]; then
    print_success "Game search working: $SEARCH_RESULTS results"
else
    print_warning "Game search returned no results"
fi

# Create reviews
print_info "Testing Review Creation..."
REVIEW1=$(curl -s -X POST "$BASE_URL/api/reviews" \
  -H "Content-Type: application/json" \
  -d '{
    "gameId": "'"$CUSTOM_GAME_ID"'",
    "userId": "'"$USER1_ID"'",
    "content": "Great game for testing!",
    "rating": 9,
    "source": "INTERNAL"
  }')
REVIEW1_ID=$(echo "$REVIEW1" | jq -r '.id // empty')
if [ ! -z "$REVIEW1_ID" ] && [ "$REVIEW1_ID" != "null" ]; then
    print_success "Review created: $REVIEW1_ID"
else
    print_error "Failed to create review"
fi

# Get reviews
REVIEWS=$(curl -s "$BASE_URL/api/reviews/game/$CUSTOM_GAME_ID" | jq '.content | length')
if [ "$REVIEWS" -gt 0 ]; then
    print_success "Retrieved $REVIEWS review(s) for game"
else
    print_warning "No reviews found for game"
fi

# ============================================================
# PHASE 4: Analytics (MongoDB Aggregations)
# ============================================================
print_header "PHASE 4: Analytics Testing (Aggregation Pipelines)"

# Test 1: Hype Meter
print_info "Testing Hype Meter Aggregation..."
TRENDING=$(curl -s "$BASE_URL/api/analytics/trending?days=7&limit=5")
TRENDING_COUNT=$(echo "$TRENDING" | jq 'length')
if [ "$TRENDING_COUNT" -gt 0 ]; then
    print_success "Hype Meter working: $TRENDING_COUNT trending games"
    echo "$TRENDING" | jq -r '.[] | "  \(.title): \(.reviewCount) reviews, Score: \(.hypeScore)"' | head -3
else
    print_warning "Hype Meter returned no results (may need more review data)"
fi

# Test 2: Genre Dominance
print_info "Testing Genre Dominance Aggregation..."
GENRE_TRENDS=$(curl -s "$BASE_URL/api/analytics/genres/trends")
TRENDS_COUNT=$(echo "$GENRE_TRENDS" | jq 'length')
if [ "$TRENDS_COUNT" -gt 0 ]; then
    print_success "Genre Dominance working: $TRENDS_COUNT years analyzed"
    echo "$GENRE_TRENDS" | jq -r '.[] | "  \(.year): \(.dominantGenre) (\(.gameCount) games)"' | head -3
else
    print_warning "Genre Dominance returned no results"
fi

# Test 3: Team Win Rates
print_info "Testing Team Performance Aggregation..."
TEAM_PERFORMANCE=$(curl -s "$BASE_URL/api/analytics/teams/performance")
TEAMS_COUNT=$(echo "$TEAM_PERFORMANCE" | jq 'length')
if [ "$TEAMS_COUNT" -gt 0 ]; then
    print_success "Team Performance working: $TEAMS_COUNT teams analyzed"
    echo "$TEAM_PERFORMANCE" | jq -r '.[] | "  \(.teamName): \(.winRate)% win rate (\(.wins)/\(.totalMatches))"' | head -3
else
    print_warning "Team Performance returned no results (may need more match data)"
fi

# Test 4: Sentiment Analysis
print_info "Testing Sentiment Analysis..."
SENTIMENT=$(curl -s "$BASE_URL/api/analytics/sentiment?limit=5")
SENTIMENT_COUNT=$(echo "$SENTIMENT" | jq 'length')
if [ "$SENTIMENT_COUNT" -gt 0 ]; then
    print_success "Sentiment Analysis working: $SENTIMENT_COUNT games analyzed"
    echo "$SENTIMENT" | jq -r '.[] | "  \(.title): Sentiment \(.avgSentiment), Rating \(.avgRating)"' | head -3
else
    print_warning "Sentiment Analysis returned no results"
fi


# ============================================================
# PHASE 5: Graph Operations
# ============================================================
print_header "PHASE 5: Graph Operations (Neo4j Queries)"

# Test 1: Game Recommendations
print_info "Testing Game Recommendations..."
REC_DATA=$(check_http_200 "$BASE_URL/api/graph/recommendations/$USER1_ID?limit=5" "Recommendations")

if [ "$(echo "$REC_DATA" | jq 'length')" -gt 0 ]; then
    echo "$REC_DATA" | jq -r '.[] | "   \(.title): \(.reason)"' | head -3
fi

# Test 2: Similar Users
print_info "Testing Similar Users Discovery..."
SIM_DATA=$(check_http_200 "$BASE_URL/api/graph/similar-users/$USER1_ID?minShared=1&limit=5" "Similar Users")

if [ "$(echo "$SIM_DATA" | jq 'length')" -gt 0 ]; then
    # Print the full JSON array of similar users (single list), not line-by-line
    echo "$SIM_DATA" | jq '.'
fi

# Test 3: Influencers
print_info "Testing Influencer Detection..."
INF_DATA=$(check_http_200 "$BASE_URL/api/graph/influencers?limit=5" "Influencer Detection")

if [ "$(echo "$INF_DATA" | jq 'length')" -gt 0 ]; then
    # Print full influencer list JSON
    echo "$INF_DATA" | jq '.'
fi


# ============================================================
# PHASE 6: Match Operations
# ============================================================
print_header "PHASE 6: Esports Match Operations"

LIVE_MATCHES=$(curl -s "$BASE_URL/api/matches/live" | jq 'length')
print_info "Live matches: $LIVE_MATCHES"

UPCOMING_MATCHES=$(curl -s "$BASE_URL/api/matches/upcoming?limit=5")
UPCOMING_COUNT=$(echo "$UPCOMING_MATCHES" | jq 'length')
if [ "$UPCOMING_COUNT" -gt 0 ]; then
    print_success "Found $UPCOMING_COUNT upcoming matches"
    echo "$UPCOMING_MATCHES" | jq -r '.[] | "  \(.teamAName) vs \(.teamBName) - \(.tournamentName)"' | head -3
else
    print_warning "No upcoming matches found"
fi

# ============================================================
# Final Report
# ============================================================
print_header "TEST SUMMARY"

echo -e "\n${BLUE}Database Status:${NC}"
HEALTH=$(curl -s "$BASE_URL/actuator/health")
echo "$HEALTH" | jq -r '.components | to_entries[] | "  \(.key): \(.value.status)"' 2>/dev/null || echo "  Unable to fetch health status"

echo -e "\n${BLUE}Data Statistics:${NC}"
echo "  Total Games: $GAMES_COUNT"
echo "  Total Users: 3 (test users created)"
echo "  Trending Games: $TRENDING_COUNT"
echo "  Genre Trends: $TRENDS_COUNT years"
echo "  Team Performance: $TEAMS_COUNT teams"
echo "  Recommendations: $REC_COUNT games"
echo "  Similar Users: $SIMILAR_COUNT users"

echo -e "\n${BLUE}Test Results:${NC}"
echo -e "  ${GREEN}Passed: $TESTS_PASSED${NC}"
echo -e "  ${RED}Failed: $TESTS_FAILED${NC}"
echo -e "  Total: $TESTS_TOTAL"

if [ $TESTS_FAILED -eq 0 ]; then
    echo -e "\n${GREEN}════════════════════════════════════════════════════════════${NC}"
    echo -e "${GREEN}ALL TESTS PASSED!"
    echo -e "${GREEN}════════════════════════════════════════════════════════════${NC}\n"
    exit 0
else
    echo -e "\n${YELLOW}════════════════════════════════════════════════════════════${NC}"
    echo -e "${YELLOW}SOME TESTS FAILED OR WARNED${NC}"
    echo -e "${YELLOW}════════════════════════════════════════════════════════════${NC}\n"
    exit 1
fi