import os
import time
import requests
import schedule
import logging
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
        """Ingest games from IGDB"""
        logger.info(f"Ingesting {limit} games from IGDB...")
        
        headers = {
            'Client-ID': self.igdb_client_id,
            'Authorization': f'Bearer {self.igdb_token}'
        }
        
        # Query for popular recent games
        body = f"""
            fields name, summary, cover.url, genres.name, platforms.name,
                   first_release_date, rating, involved_companies.company.name;
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
    
    def save_game(self, game_data):
        """Save game to MongoDB and Neo4j"""
        try:
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
                'totalReviews': 0,
                'createdAt': datetime.now(),
                'updatedAt': datetime.now()
            }
            
            # Insert into MongoDB
            created_at_val = game_doc['createdAt'] 
            
            # 2. Remove it for the MongoDB $set operation
            game_doc.pop('createdAt') 

            # 3. Update MongoDB
            self.db.games.update_one(
                {'_id': game_doc['_id']},
                {
                    '$set': game_doc,
                    '$setOnInsert': {'createdAt': created_at_val} # Use the variable
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
                    'createdAt': created_at_val.isoformat()
                })
            
            logger.info(f"Saved game: {game_doc['title']}")
            
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
        
        # 1. Fetch Running Matches
        self._fetch_and_save_matches(headers, '/matches/running')
        
        # 2. Fetch Upcoming Matches (CRITICAL FIX: Added this line)
        self._fetch_and_save_matches(headers, '/matches/upcoming', params={'per_page': 50, 'sort': 'begin_at'})

        # 3. Fetch Past Matches
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
        """Save match to MongoDB"""
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
            
            self.db.matches.update_one(
                {'_id': match_doc['_id']},
                {'$set': match_doc},
                upsert=True
            )
            
            logger.info(f"Saved match: {match_doc['teamAName']} vs {match_doc['teamBName']}")
            
        except Exception as e:
            logger.error(f"Error saving match: {e}")

        try:
            # 1. Sync Tournament
            if 'tournament' in match_data:
                tourney = match_data['tournament']

                league = match_data.get('league', {})
                series = match_data.get('series', {})
                
                league_name = league.get('name', '')
                series_name = series.get('full_name', '') or series.get('name', '')
                tourney_stage = tourney.get('name', '')

                # Construct a descriptive name: "League Series - Stage"
                # Example: "ESL Pro League Season 18 - Playoffs"
                parts = [p for p in [league_name, series_name, tourney_stage] if p]
                full_tournament_name = " - ".join(parts)

                with self.neo4j_driver.session() as session:
                    session.run("""
                        MERGE (t:Tournament {tournamentId: $tid})
                        SET t.name = $name,
                            t.gameTitle = $game,
                            t.league = $league,  // Optional: Save distinct properties
                            t.series = $series   // Optional: Save distinct properties
                    """, {
                        'tid': str(tourney['id']),
                        'name': full_tournament_name,
                        'game': match_data['videogame']['name'],
                        'league': league_name,
                        'series': series_name
                    })

            # 2. Sync Teams and Relationships
            opponents = match_data.get('opponents', [])
            for opp in opponents:
                team = opp.get('opponent', {})
                if not team.get('id'): continue
                
                with self.neo4j_driver.session() as session:
                    # Sync Team and create COMPETES_IN relationship
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
            logger.info(f"Synced graph data for match {match_data['id']}")
        
        except Exception as e:
            logger.error(f"Error syncing graph data: {e}")
    
    def map_status(self, status):
        """Map PandaScore status to internal status"""
        mapping = {
            'running': 'LIVE',
            'finished': 'FINISHED',
            'not_started': 'SCHEDULED',
            'canceled': 'CANCELLED',
            'postponed': 'POSTPONED'
        }
        return mapping.get(status.lower(), 'SCHEDULED')
    
    def parse_datetime(self, dt_string):
        """Parse datetime string"""
        if not dt_string:
            return None
        try:
            return datetime.fromisoformat(dt_string.replace('Z', '+00:00'))
        except:
            return None
    
    def generate_sample_reviews(self, count=1000):
        """Generate sample reviews for testing"""
        logger.info(f"Generating {count} sample reviews...")
        
        games = list(self.db.games.find().limit(50))
        
        if not games:
            logger.warning("No games found for review generation")
            return
        
        import random
        from datetime import timedelta
        
        sentiments = [
            ("Amazing game! Love it!", 9, 0.9),
            ("Pretty good, worth playing", 8, 0.7),
            ("Decent game, has potential", 7, 0.5),
            ("Average experience", 6, 0.3),
            ("Disappointed with this one", 4, -0.5),
            ("Not great, many issues", 3, -0.7),
        ]
        
        for i in range(count):
            game = random.choice(games)
            sentiment = random.choice(sentiments)
            
            review_doc = {
                'gameId': game['_id'],
                'userId': f"user_{random.randint(1, 100)}",
                'content': sentiment[0],
                'rating': sentiment[1],
                'sentimentScore': sentiment[2],
                'timestamp': datetime.now() - timedelta(days=random.randint(0, 30)),
                'upvotes': random.randint(0, 50),
                'downvotes': random.randint(0, 10),
                'source': 'INTERNAL',
                'createdAt': datetime.now()
            }
            
            self.db.reviews.insert_one(review_doc)
            
            if i % 100 == 0:
                logger.info(f"Generated {i} reviews...")
        
        logger.info("Sample review generation completed")
    
    def run_scheduled_tasks(self):
        """Run scheduled data ingestion tasks"""
        logger.info("Starting scheduled data ingestion service...")
        
        # Initial data load
        self.ingest_games(limit=200)
        self.ingest_esports_data()
        self.generate_sample_reviews(count=5000)
        
        # Schedule periodic updates
        schedule.every(1).hours.do(self.ingest_esports_data)
        schedule.every(6).hours.do(lambda: self.ingest_games(limit=50))
        schedule.every(30).minutes.do(self.ingest_esports_data)
        
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