#!/usr/bin/env bash

# 1. Initialize Config Server Replica Set
echo "Initializing Config Server..."
mongosh --host configsvr01:27019 <<EOF
rs.initiate(
  {
    _id: "rs-config-server",
    configsvr: true,
    members: [
      { _id : 0, host : "configsvr01:27019" },
      { _id : 1, host : "configsvr02:27019" },
      { _id : 2, host : "configsvr03:27019" }
    ]
  }
)
EOF

# 2. Initialize Shard 01 Replica Set
echo "Initializing Shard 01..."
mongosh --host shard01-a:27018 <<EOF
rs.initiate(
  {
    _id: "rs-shard-01",
    members: [
      { _id : 0, host : "shard01-a:27018" },
      { _id : 1, host : "shard01-b:27018" },
      { _id : 2, host : "shard01-c:27018" }
    ]
  }
)
EOF

# 3. Initialize Shard 02 Replica Set
echo "Initializing Shard 02..."
mongosh --host shard02-a:27018 <<EOF
rs.initiate(
  {
    _id: "rs-shard-02",
    members: [
      { _id : 0, host : "shard02-a:27018" },
      { _id : 1, host : "shard02-b:27018" },
      { _id : 2, host : "shard02-c:27018" }
    ]
  }
)
EOF

# Wait for replica sets to elect primaries
sleep 20

# 4. Connect to Mongos and Add Shards
echo "Configuring Sharding on Mongos..."
mongosh --host mongos:27017 <<EOF
sh.addShard("rs-shard-01/shard01-a:27018,shard01-b:27018,shard01-c:27018")
sh.addShard("rs-shard-02/shard02-a:27018,shard02-b:27018,shard02-c:27018")

# 5. Enable Sharding for Database
sh.enableSharding("gamesense")

# 6. Define Shard Keys and Shard Collections

# Games Collection: Shard by 'releaseDate' (Range based) 
# Justification: Queries often filter by date range (e.g., "popular games in 2024")
db.adminCommand( { shardCollection: "gamesense.games", key: { releaseDate: 1 } } )

# Reviews Collection: Shard by 'gameId' (Hashed)
# Justification: Reviews are almost always accessed by Game ID. Hashing ensures even distribution.
db.adminCommand( { shardCollection: "gamesense.reviews", key: { gameId: "hashed" } } )

# Matches Collection: Shard by 'tournamentId' (Hashed)
# Justification: Matches are accessed by Tournament.
db.adminCommand( { shardCollection: "gamesense.matches", key: { tournamentId: "hashed" } } )

print("Sharding configuration completed successfully.")
EOF