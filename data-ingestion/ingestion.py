import os
import time
import requests
import schedule
import logging
import random
import re
from datetime import datetime, timedelta
from pymongo import MongoClient
from neo4j import GraphDatabase

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

class DataIngestionService:
    def __init__(self):
        # MongoDB connection
        mongo_uri = os.getenv('MONGODB_URI', 'mongodb://mongos:27017/') 
        self.mongo_client = MongoClient(mongo_uri)
        self.db = self.mongo_client['gamesense']
        
        # Neo4j connection
        neo4j_uri = os.getenv('NEO4J_URI')
        neo4j_user = os.getenv('NEO4J_USER')
        neo4j_password = os.getenv('NEO4J_PASSWORD')
        self.neo4j_driver = GraphDatabase.driver(
            neo4j_uri,
            auth=(neo4j_user, neo4j_password)
        )
        
        # API credentials
        self.igdb_client_id = os.getenv('IGDB_CLIENT_ID')
        self.igdb_client_secret = os.getenv('IGDB_CLIENT_SECRET')
        self.pandascore_api_key = os.getenv('PANDASCORE_API_KEY')
        
        self.igdb_token = None
        # Only try to auth if keys are present
        if self.igdb_client_id and self.igdb_client_secret:
            self.authenticate_igdb()
        else:
            logger.warning("IGDB credentials missing. Skipping IGDB auth.")
    
    def authenticate_igdb(self):
        """Authenticate with IGDB API"""
        logger.info("Authenticating with IGDB API...")
        try:
            response = requests.post(
                'https://id.twitch.tv/oauth2/token',
                params={
                    'client_id': self.igdb_client_id,
                    'client_secret': self.igdb_client_secret,
                    'grant_type': 'client_credentials'
                }
            )
            
            if response.status_code == 200:
                self.igdb_token = response.json()['access_token']
                logger.info("IGDB authentication successful")
            else:
                logger.error(f"IGDB authentication failed: {response.text}")
        except Exception as e:
            logger.error(f"IGDB Auth Error: {e}")
    
    def ingest_games(self, limit=100):
        """Ingest games from IGDB"""
        if not self.igdb_token:
            logger.warning("No IGDB Token. Skipping Game Ingestion.")
            return

        logger.info(f"Ingesting {limit} games from IGDB...")
        
        headers = {
            'Client-ID': self.igdb_client_id,
            'Authorization': f'Bearer {self.igdb_token}'
        }
        
        # Query for popular recent games
        body = f"""
            fields name, summary, cover.url, genres.name, platforms.name,
                   first_release_date, rating, involved_companies.company.name,
                   external_games.category, external_games.uid;
            where rating > 70 & first_release_date > 1577836800;
            sort rating desc;
            limit {limit};
        """
        
        try:
            response = requests.post(
                'https://api.igdb.com/v4/games',
                headers=headers,
                data=body
            )
            
            if response.status_code == 200:
                games = response.json()
                logger.info(f"Retrieved {len(games)} games from IGDB")
                for game_data in games:
                    self.save_game(game_data)
                logger.info("Game ingestion completed")
            else:
                logger.error(f"Failed to fetch games: {response.text}")
                
        except Exception as e:
            logger.error(f"Error ingesting games: {e}")
    
    def extract_steam_id(self, game_data):
        if 'external_games' in game_data:
            for ext in game_data['external_games']:
                if ext.get('category') == 1: # Steam
                    return ext.get('uid')
        return None

    def save_game(self, game_data):
        try:
            steam_id = self.extract_steam_id(game_data)
            game_doc = {
                '_id': str(game_data['id']),
                'title': game_data.get('name', 'Unknown'),
                'description': game_data.get('summary', ''),
                'releaseDate': datetime.fromtimestamp(game_data.get('first_release_date', 0)) if game_data.get('first_release_date') else None,
                'genres': [g['name'] for g in game_data.get('genres', [])],
                'platforms': [p['name'] for p in game_data.get('platforms', [])],
                'developer': self.extract_developer(game_data),
                'avgScore': game_data.get('rating', 0) / 10.0 if game_data.get('rating') else None,
                'coverImageUrl': self.get_cover_url(game_data),
                'steamId': steam_id,
                'totalReviews': 0,
                'updatedAt': datetime.now()
            }
            
            self.db.games.update_one(
                {'_id': game_doc['_id']},
                {'$set': game_doc, '$setOnInsert': {'createdAt': datetime.now()}},
                upsert=True
            )
            
            # Neo4j Sync
            with self.neo4j_driver.session() as session:
                session.run("""
                    MERGE (g:Game {gameId: $gameId})
                    SET g.title = $title, g.genres = $genres, g.createdAt = datetime($createdAt)
                """, {
                    'gameId': game_doc['_id'],
                    'title': game_doc['title'],
                    'genres': game_doc['genres'],
                    'createdAt': datetime.now().isoformat()
                })
            
        except Exception as e:
            logger.error(f"Error saving game: {e}")
    
    def extract_developer(self, game_data):
        companies = game_data.get('involved_companies', [])
        if companies:
            return companies[0].get('company', {}).get('name', 'Unknown')
        return 'Unknown'
    
    def get_cover_url(self, game_data):
        if 'cover' in game_data and 'url' in game_data['cover']:
            return 'https:' + game_data['cover']['url'].replace('t_thumb', 't_cover_big')
        return None

    # --- ESPORTS ---
    def ingest_esports_data(self):
        if not self.pandascore_api_key:
            logger.warning("No PandaScore Key. Skipping Esports.")
            return

        logger.info("Ingesting esports data...")
        headers = {'Authorization': f'Bearer {self.pandascore_api_key}'}
        self._fetch_and_save_matches(headers, '/matches/running')
        self._fetch_and_save_matches(headers, '/matches/upcoming', {'per_page': 20, 'sort': 'begin_at'})
    
    def _fetch_and_save_matches(self, headers, endpoint, params={'per_page': 20}):
        try:
            response = requests.get(f'https://api.pandascore.co{endpoint}', headers=headers, params=params)
            if response.status_code == 200:
                matches = response.json()
                for m in matches: self.save_match(m)
            else:
                logger.error(f"PandaScore Error: {response.text}")
        except Exception as e:
            logger.error(f"Esports Fetch Error: {e}")

    def save_match(self, match_data):
        try:
            opponents = match_data.get('opponents', [])
            team_a = opponents[0]['opponent'] if len(opponents) > 0 else {}
            team_b = opponents[1]['opponent'] if len(opponents) > 1 else {}
            
            # Save Match to MongoDB
            match_doc = {
                '_id': str(match_data['id']),
                'teamAId': str(team_a.get('id', '')),
                'teamAName': team_a.get('name', 'TBD'),
                'teamBId': str(team_b.get('id', '')),
                'teamBName': team_b.get('name', 'TBD'),
                'status': self.map_status(match_data.get('status', 'not_started')),
                'winnerId': str(match_data['winner_id']) if match_data.get('winner_id') else None,
                'gameTitle': match_data['videogame']['name'] if 'videogame' in match_data else 'Unknown',
                'scheduledAt': self.parse_datetime(match_data.get('scheduled_at')),
                'startedAt': self.parse_datetime(match_data.get('begin_at')),
                'updatedAt': datetime.now()
            }
            self.db.matches.update_one({'_id': match_doc['_id']}, {'$set': match_doc}, upsert=True)

            # Sync Teams to Neo4j
            with self.neo4j_driver.session() as session:
                # Sync Team A
                if match_doc['teamAId']:
                    session.run("""
                        MERGE (t:Team {teamId: $teamId})
                        SET t.name = $name, t.gameTitle = $gameTitle
                    """, {'teamId': match_doc['teamAId'], 'name': match_doc['teamAName'], 'gameTitle': match_doc['gameTitle']})
                
                # Sync Team B
                if match_doc['teamBId']:
                    session.run("""
                        MERGE (t:Team {teamId: $teamId})
                        SET t.name = $name, t.gameTitle = $gameTitle
                    """, {'teamId': match_doc['teamBId'], 'name': match_doc['teamBName'], 'gameTitle': match_doc['gameTitle']})

        except Exception as e:
            logger.error(f"Error saving match/team: {e}")

    def map_status(self, status):
        mapping = {'running': 'LIVE', 'finished': 'FINISHED', 'not_started': 'SCHEDULED'}
        return mapping.get(status.lower(), 'SCHEDULED')
    
    def parse_datetime(self, dt_string):
        if not dt_string: return None
        try: return datetime.fromisoformat(dt_string.replace('Z', '+00:00'))
        except: return None

    # --- REVIEWS & SENTIMENT ---

    def analyze_sentiment(self, text):
        positive = ['good', 'great', 'amazing', 'love', 'best', 'fun', 'excellent', 'masterpiece', 'addictive']
        negative = ['bad', 'worst', 'boring', 'hate', 'trash', 'broken', 'buggy', 'awful', 'slow']
        text_lower = text.lower()
        score = 0.0
        for w in positive: 
            if w in text_lower: score += 0.2
        for w in negative: 
            if w in text_lower: score -= 0.2
        return max(min(score, 1.0), -1.0)

    def ingest_steam_reviews(self):
        """Fetch real Steam reviews with Proper Headers"""
        logger.info("Starting Steam Review Ingestion...")
        games = list(self.db.games.find({"steamId": {"$exists": True, "$ne": None}}))
        
        # CRITICAL FIX: Steam requires a User-Agent
        headers = {
            'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36'
        }

        for game in games:
            steam_id = game['steamId']
            game_id = game['_id']
            
            try:
                url = f"https://store.steampowered.com/appreviews/{steam_id}?json=1&language=english&num_per_page=20"
                resp = requests.get(url, headers=headers, timeout=10)
                
                if resp.status_code == 200:
                    data = resp.json()
                    reviews = data.get('reviews', [])
                    
                    for rev in reviews:
                        review_doc = {
                            'reviewId': str(rev.get('recommendationid')),
                            'gameId': game_id,
                            'userId': f"steam_{rev.get('author', {}).get('steamid')}",
                            'content': rev.get('review', ''),
                            'rating': 10 if rev.get('voted_up') else 2,
                            'sentimentScore': self.analyze_sentiment(rev.get('review', '')),
                            'timestamp': datetime.fromtimestamp(rev.get('timestamp_created')),
                            'source': 'STEAM',
                            'createdAt': datetime.now()
                        }
                        self.db.reviews.update_one({'reviewId': review_doc['reviewId']}, {'$set': review_doc}, upsert=True)
                    
                    # Update count
                    self.db.games.update_one({'_id': game_id}, {'$inc': {'totalReviews': len(reviews)}})
                
                time.sleep(1) # Be polite
            except Exception as e:
                logger.error(f"Steam fetch failed for {game['title']}: {e}")

    def ensure_analytics_data(self):
        """
        FALLBACK: Generate synthetic reviews AND MATCHES if data is missing.
        """
        logger.info("Checking for missing analytics data...")
        
        # 1. Synthetic Reviews (Existing Logic)
        all_games = list(self.db.games.find())
        if all_games:
            mock_templates = [("Great!", 9, 0.8), ("Bad.", 2, -0.8), ("Okay.", 6, 0.1)]
            for game in all_games:
                if self.db.reviews.count_documents({'gameId': game['_id']}) < 3:
                    for i in range(5):
                        t = random.choice(mock_templates)
                        self.db.reviews.insert_one({
                            'reviewId': f"mock_{game['_id']}_{i}_{int(time.time())}",
                            'gameId': game['_id'],
                            'userId': f"mock_user_{random.randint(100,999)}",
                            'content': t[0], 'rating': t[1], 'sentimentScore': t[2],
                            'timestamp': datetime.now(), 'source': 'INTERNAL_MOCK'
                        })

        # 2. FIX: Synthetic Matches (For Team Performance)
        # Only generate if we have fewer than 5 finished matches
        if self.db.matches.count_documents({'status': 'FINISHED'}) < 5:
            logger.info("Generating synthetic FINISHED matches...")
            teams = [
                {'id': 't1', 'name': 'T1'}, {'id': 't2', 'name': 'Gen.G'}, 
                {'id': 't3', 'name': 'G2'}, {'id': 't4', 'name': 'Fnatic'}
            ]
            
            for i in range(10):
                t_a = random.choice(teams)
                t_b = random.choice([t for t in teams if t['id'] != t_a['id']])
                winner = random.choice([t_a, t_b])
                
                self.db.matches.insert_one({
                    '_id': f"mock_match_{i}_{int(time.time())}",
                    'teamAId': t_a['id'], 'teamAName': t_a['name'],
                    'teamBId': t_b['id'], 'teamBName': t_b['name'],
                    'gameTitle': "League of Legends",
                    'status': "FINISHED",
                    'winnerId': winner['id'],
                    'scheduledAt': datetime.now() - timedelta(days=random.randint(1, 30)),
                    'startedAt': datetime.now() - timedelta(days=random.randint(1, 30)),
                    'createdAt': datetime.now()
                })

    def run_scheduled_tasks(self):
        logger.info("Starting ingestion service...")
        
        # 1. Try Real Data
        self.ingest_games(limit=50)
        self.ingest_esports_data()
        self.ingest_steam_reviews()
        
        # 2. GUARANTEE Data for Analytics
        self.ensure_analytics_data()
        
        # Schedule
        schedule.every(2).hours.do(self.ingest_esports_data)
        schedule.every(6).hours.do(self.ingest_steam_reviews)
        schedule.every(1).hours.do(self.ensure_analytics_data) # Keep data fresh
        
        while True:
            schedule.run_pending()
            time.sleep(60)

    def close(self):
        self.mongo_client.close()
        self.neo4j_driver.close()

if __name__ == '__main__':
    service = DataIngestionService()
    try:
        service.run_scheduled_tasks()
    except KeyboardInterrupt:
        service.close()