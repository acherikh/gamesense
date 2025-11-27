#!/usr/bin/env bash

# MongoDB Replica Set Initialization Script

echo "Waiting for MongoDB primary to be ready..."
sleep 10

echo "Initializing MongoDB Replica Set..."

mongosh --host mongo-primary:27017 -u admin -p admin123 --authenticationDatabase admin <<EOF

rs.initiate({
  _id: "rs0",
  members: [
    { _id: 0, host: "mongo-primary:27017", priority: 5 },
    { _id: 1, host: "mongo-secondary-1:27018", priority: 2 },
    { _id: 2, host: "mongo-secondary-2:27019", priority: 1 }
  ]
});

EOF

echo "Waiting for replica set to initialize..."
sleep 15

echo "Creating indexes..."

mongosh --host mongo-primary:27017 -u admin -p admin123 --authenticationDatabase admin <<EOF

use gamesense;

// Text index for game search
db.games.createIndex(
  { title: "text", description: "text" },
  { weights: { title: 3, description: 1 } }
);

// Compound index for reviews aggregation
db.reviews.createIndex({ gameId: 1, timestamp: -1 });

// Index for trending games query
db.reviews.createIndex({ timestamp: -1, rating: 1 });

// Indexes for match queries
db.matches.createIndex({ status: 1, scheduledAt: -1 });
db.matches.createIndex({ tournamentId: 1 });
db.matches.createIndex({ teamAId: 1, teamBId: 1 });
db.matches.createIndex({ winnerId: 1 });

// User indexes
db.users.createIndex({ username: 1 }, { unique: true });
db.users.createIndex({ email: 1 }, { unique: true });

// Genre and release date for genre analysis
db.games.createIndex({ genres: 1, releaseDate: 1 });

print("Indexes created successfully");

EOF

echo "MongoDB replica set initialized successfully!"