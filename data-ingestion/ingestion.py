import os
import time
import requests
import schedule
import logging
import re
from datetime import datetime
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
        self.authenticate_igdb()
    
    def authenticate_igdb(self):
        """Authenticate with IGDB API"""
        logger.info("Authenticating with IGDB API...")
        
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
    
    def ingest_games(self, limit=100):
        """Ingest games from IGDB, including Steam IDs for review fetching"""
        logger.info(f"Ingesting {limit} games from IGDB...")
        
        headers = {
            'Client-ID': self.igdb_client_id,
            'Authorization': f'Bearer {self.igdb_token}'
        }
        
        # Query for popular recent games, asking for external_games (Steam ID)
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
        """Extract Steam App ID from IGDB external_games"""
        if 'external_games' in game_data:
            for ext in game_data['external_games']:
                # Category 1 is Steam in IGDB
                if ext.get('category') == 1:
                    return ext.get('uid')
        return None

    def save_game(self, game_data):
        """Save game to MongoDB and Neo4j"""
        try:
            steam_id = self.extract_steam_id(game_data)
            
            # Prepare game document
            game_doc = {
                '_id': str(game_data['id']),
                'title': game_data.get('name', 'Unknown'),
                'description': game_data.get('summary', ''),
                'releaseDate': datetime.fromtimestamp(
                    game_data.get('first_release_date', 0)
                ) if game_data.get('first_release_date') else None,
                'genres': [g['name'] for g in game_data.get('genres', [])],
                'platforms': [p['name'] for p in game_data.get('platforms', [])],
                'developer': self.extract_developer(game_data),
                'avgScore': game_data.get('rating', 0) / 10.0 if game_data.get('rating') else None,
                'coverImageUrl': self.get_cover_url(game_data),
                'steamId': steam_id,  # Save Steam ID for review fetching
                'totalReviews': 0,
                'updatedAt': datetime.now()
            }
            
            # Upsert into MongoDB (preserve createdAt)
            self.db.games.update_one(
                {'_id': game_doc['_id']},
                {
                    '$set': game_doc,
                    '$setOnInsert': {'createdAt': datetime.now()}
                },
                upsert=True
            )
            
            # Insert into Neo4j
            with self.neo4j_driver.session() as session:
                session.run("""
                    MERGE (g:Game {gameId: $gameId})
                    SET g.title = $title,
                        g.genres = $genres,
                        g.createdAt = datetime($createdAt)
                """, {
                    'gameId': game_doc['_id'],
                    'title': game_doc['title'],
                    'genres': game_doc['genres'],
                    'createdAt': datetime.now().isoformat()
                })
            
            logger.info(f"Saved game: {game_doc['title']} (Steam ID: {steam_id})")
            
        except Exception as e:
            logger.error(f"Error saving game: {e}")
    
    def extract_developer(self, game_data):
        """Extract developer from involved companies"""
        companies = game_data.get('involved_companies', [])
        if companies and len(companies) > 0:
            company = companies[0].get('company', {})
            return company.get('name', 'Unknown')
        return 'Unknown'
    
    def get_cover_url(self, game_data):
        """Extract and format cover image URL"""
        if 'cover' in game_data and 'url' in game_data['cover']:
            url = game_data['cover']['url']
            return 'https:' + url.replace('t_thumb', 't_cover_big')
        return None
    
    def ingest_esports_data(self):
        """Ingest esports matches from PandaScore"""
        logger.info("Ingesting esports data from PandaScore...")
        headers = {'Authorization': f'Bearer {self.pandascore_api_key}'}
        self._fetch_and_save_matches(headers, '/matches/running')
        self._fetch_and_save_matches(headers, '/matches/upcoming', params={'per_page': 50, 'sort': 'begin_at'})
        self._fetch_and_save_matches(headers, '/matches/past', params={'per_page': 50})
    
    def _fetch_and_save_matches(self, headers, endpoint, params=None):
        try:
            if params is None: params = {'per_page': 50}
            response = requests.get(f'https://api.pandascore.co{endpoint}', headers=headers, params=params)
            
            if response.status_code == 200:
                matches = response.json()
                logger.info(f"Retrieved {len(matches)} matches from {endpoint}")
                for match_data in matches:
                    self.save_match(match_data)
            else:
                logger.error(f"Failed to fetch matches: {response.text}")
        except Exception as e:
            logger.error(f"Error fetching {endpoint}: {e}")
    
    def save_match(self, match_data):
        """Save match to MongoDB and Sync to Neo4j"""
        try:
            opponents = match_data.get('opponents', [])
            team_a = opponents[0]['opponent'] if len(opponents) > 0 else {}
            team_b = opponents[1]['opponent'] if len(opponents) > 1 else {}
            
            match_doc = {
                '_id': str(match_data['id']),
                'tournamentId': str(match_data['tournament']['id']) if 'tournament' in match_data else None,
                'tournamentName': match_data['tournament']['name'] if 'tournament' in match_data else 'Unknown',
                'teamAId': str(team_a.get('id', '')),
                'teamAName': team_a.get('name', 'TBD'),
                'teamBId': str(team_b.get('id', '')),
                'teamBName': team_b.get('name', 'TBD'),
                'status': self.map_status(match_data.get('status', 'not_started')),
                'winnerId': str(match_data['winner_id']) if match_data.get('winner_id') else None,
                'gameTitle': match_data['videogame']['name'] if 'videogame' in match_data else 'Unknown',                
                'scheduledAt': self.parse_datetime(match_data.get('scheduled_at')),
                'startedAt': self.parse_datetime(match_data.get('begin_at')),
                'createdAt': datetime.now(),
                'updatedAt': datetime.now()
            }
            
            self.db.matches.update_one({'_id': match_doc['_id']}, {'$set': match_doc}, upsert=True)
            logger.info(f"Saved match: {match_doc['teamAName']} vs {match_doc['teamBName']}")

            # Sync Graph
            if 'tournament' in match_data:
                self.sync_tournament_graph(match_data)
                
        except Exception as e:
            logger.error(f"Error saving match: {e}")

    def sync_tournament_graph(self, match_data):
        try:
            tourney = match_data['tournament']
            with self.neo4j_driver.session() as session:
                session.run("""
                    MERGE (t:Tournament {tournamentId: $tid})
                    SET t.name = $name, t.gameTitle = $game
                """, {
                    'tid': str(tourney['id']),
                    'name': tourney.get('name', 'Unknown'),
                    'game': match_data['videogame']['name']
                })
                
                for opp in match_data.get('opponents', []):
                    team = opp.get('opponent', {})
                    if not team.get('id'): continue
                    session.run("""
                        MERGE (t:Team {teamId: $teamId})
                        SET t.name = $name, t.gameTitle = $gameTitle
                        WITH t
                        MATCH (tour:Tournament {tournamentId: $tourId})
                        MERGE (t)-[:COMPETES_IN]->(tour)
                    """, {
                        'teamId': str(team['id']),
                        'name': team.get('name', 'Unknown'),
                        'gameTitle': match_data['videogame']['name'],
                        'tourId': str(match_data['tournament']['id'])
                    })
        except Exception as e:
            logger.error(f"Error syncing graph: {e}")

    def map_status(self, status):
        mapping = {'running': 'LIVE', 'finished': 'FINISHED', 'not_started': 'SCHEDULED', 'canceled': 'CANCELLED', 'postponed': 'POSTPONED'}
        return mapping.get(status.lower(), 'SCHEDULED')
    
    def parse_datetime(self, dt_string):
        if not dt_string: return None
        try: return datetime.fromisoformat(dt_string.replace('Z', '+00:00'))
        except: return None

    # --- REAL STEAM REVIEW INGESTION ---
    def analyze_sentiment(self, text):
        """Simple heuristic sentiment analysis"""
        positive = ['good', 'great', 'amazing', 'love', 'best', 'fun', 'excellent', 'masterpiece']
        negative = ['bad', 'worst', 'boring', 'hate', 'trash', 'broken', 'buggy', 'awful']
        text_lower = text.lower()
        score = 0
        for w in positive: 
            if w in text_lower: score += 0.2
        for w in negative: 
            if w in text_lower: score -= 0.2
        return max(min(score, 1.0), -1.0)

    def ingest_steam_reviews(self):
        """Fetch real reviews from Steam API for games with Steam IDs"""
        logger.info("Starting Steam Review Ingestion...")
        
        # Find games in MongoDB that have a steamId
        games = list(self.db.games.find({"steamId": {"$exists": True, "$ne": None}}))
        logger.info(f"Found {len(games)} games with Steam IDs.")
        
        for game in games:
            steam_id = game['steamId']
            game_id = game['_id']
            logger.info(f"Fetching reviews for {game['title']} (Steam ID: {steam_id})")
            
            try:
                # Steam Web API: Get JSON reviews
                url = f"https://store.steampowered.com/appreviews/{steam_id}?json=1&language=english&num_per_page=20"
                resp = requests.get(url)
                
                if resp.status_code == 200:
                    data = resp.json()
                    reviews = data.get('reviews', [])
                    
                    if not reviews:
                        continue
                        
                    for rev in reviews:
                        # Map Steam review to internal schema
                        sentiment = self.analyze_sentiment(rev.get('review', ''))
                        review_doc = {
                            'reviewId': str(rev.get('recommendationid')),
                            'gameId': game_id,
                            'userId': f"steam_{rev.get('author', {}).get('steamid')}",
                            'content': rev.get('review', ''),
                            'rating': 10 if rev.get('voted_up') else 0, # Steam is binary thumb up/down
                            'sentimentScore': sentiment,
                            'timestamp': datetime.fromtimestamp(rev.get('timestamp_created')),
                            'upvotes': rev.get('votes_up', 0),
                            'downvotes': 0, # Steam API doesn't expose downvotes easily in this endpoint
                            'source': 'STEAM',
                            'createdAt': datetime.now()
                        }
                        
                        # Upsert review to avoid duplicates
                        self.db.reviews.update_one(
                            {'reviewId': review_doc['reviewId']},
                            {'$set': review_doc},
                            upsert=True
                        )
                    
                    # Update Game stats
                    self.db.games.update_one(
                        {'_id': game_id},
                        {'$inc': {'totalReviews': len(reviews)}}
                    )
                    
                    time.sleep(1) # Be polite to Steam API
                    
                else:
                    logger.warning(f"Failed to fetch Steam reviews for {steam_id}: {resp.status_code}")
                    
            except Exception as e:
                logger.error(f"Error processing reviews for {game['title']}: {e}")

    def run_scheduled_tasks(self):
        """Run scheduled data ingestion tasks"""
        logger.info("Starting scheduled data ingestion service...")
        
        # Initial data load
        self.ingest_games(limit=50) # Reduced for startup speed
        self.ingest_esports_data()
        self.ingest_steam_reviews() # Fetch real reviews
        
        # Schedule periodic updates
        schedule.every(1).hours.do(self.ingest_esports_data)
        schedule.every(6).hours.do(lambda: self.ingest_games(limit=20))
        schedule.every(12).hours.do(self.ingest_steam_reviews)
        
        logger.info("Scheduled tasks configured")
        
        while True:
            schedule.run_pending()
            time.sleep(60)
    
    def close(self):
        """Close database connections"""
        self.mongo_client.close()
        self.neo4j_driver.close()

if __name__ == '__main__':
    service = DataIngestionService()
    try:
        service.run_scheduled_tasks()
    except KeyboardInterrupt:
        logger.info("Shutting down...")
        service.close()