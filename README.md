┌─────────────────────────────────────────────────────────────┐
│                    Spring Boot API (8080)                   │
│  • REST Endpoints (Create, Search, Retrieve, Delete)        │
│  • Business Logic & Validation                              │
│  • Exception Handling & Logging                             │
└─────────────────────────────────────────────────────────────┘
                    ↓                    ↓
        ┌──────────────────┐   ┌─────────────────┐
        │  MySQL (3306)    │   │ Logstash (9600) │
        │  • Primary DB    │   │ • JDBC Input    │
        │  • Schema & Data │   │ • Transforms    │
        └──────────────────┘   └─────────────────┘
                    ↓                    ↓
        ┌────────────────────────────────────────┐
        │  Elasticsearch (9200)                  │
        │  • Full-text Index                     │
        │  • Fuzzy Search Support                │
        │  • Fast Retrieval                      │
        └────────────────────────────────────────┘
Data Flow

Create/Update: API → MySQL
Sync: Logstash polls MySQL every 10 seconds → Elasticsearch
Search: API → Elasticsearch (with MySQL fallback)
Retrieve: API → MySQL
Delete: API → MySQL (soft delete)

# Logstash Sync Strategy (MySQL → Elasticsearch)
Logstash runs as a background synchronization service to keep Elasticsearch updated with the latest product records from MySQL.

# How the Sync Works
Logstash uses a JDBC input plugin to connect to MySQL.
It runs a query every 10 seconds (configurable using schedule => "*/10 * * * * *").
The query fetches all active products (is_deleted = 0).
Each row is converted into a JSON document and pushed into Elasticsearch.JDBC Input: Runs every 10 seconds using a scheduler.

##  Prerequisites

You only need:

✅ Docker Desktop (Windows / Mac / Linux)

Download: https://www.docker.com/products/docker-desktop/

❌ You DO NOT need to install:
- Java
- Maven
- MySQL
- Elasticsearch

Everything runs inside Docker containers.


# Clean previous setup (first time only)
docker-compose down 
docker system prune -f

# Build and start
docker-compose up --build

# Verify all containers
docker ps



