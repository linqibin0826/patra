# Docker Compose Configuration

Modular Docker Compose setup for Papertrace development environment, organized by service lifecycle and priority.

## Structure

```
docker/
├── docker-compose.dev.yaml          # Main entry point (all services)
├── docker-compose.core.yaml         # Essential runtime services
├── docker-compose.storage.yaml      # Object storage services (MinIO)
├── docker-compose.observability.yaml # APM and monitoring stack
├── docker-compose.jobs.yaml         # Job scheduling and message queue
└── README.md                        # This file
```

---

## Volume Storage Location

All service data is stored in `~/.papertrace/docker/` directory in your home folder:

```
~/.papertrace/docker/
├── mysql/
│   ├── data/           # MySQL database files
│   ├── conf.d/         # MySQL configuration files
│   └── init/           # Database initialization scripts
├── redis/
│   ├── data/           # Redis persistent data
│   └── redis.conf      # Redis configuration
├── nacos/
│   ├── data/           # Nacos configuration data
│   └── logs/           # Nacos logs
├── minio/
│   └── data/           # MinIO object storage data
├── es/
│   └── data/           # Elasticsearch indices and data
├── xxl-job-admin/
│   └── logs/           # XXL-Job logs
└── rocketmq/
    ├── namesrv/
    │   ├── logs/       # NameServer logs
    │   └── store/      # NameServer data
    └── broker/
        ├── logs/       # Broker logs
        ├── store/      # Message store
        └── conf/       # Broker configuration
```

**Note**: You need to create the required directories and configuration files before first startup. See the "First-Time Setup" section below.

---

## Service Organization

### Core Services (`docker-compose.core.yaml`)
Essential infrastructure required for application runtime:

- **MySQL** (port 13306): Primary database
- **Redis** (port 16379): Cache and session store
- **Nacos** (ports 4000, 8848, 9848, 9849): Service discovery and configuration center

**When to use**: Always start these services first. The application cannot run without them.

### Storage Services (`docker-compose.storage.yaml`)
Object storage infrastructure for file uploads and document management:

- **MinIO** (ports 19000, 19001): S3-compatible object storage with web console

**When to use**: Required when using file upload features. Includes automatic bucket creation (`dev-ingest`) with private access policy.

### Observability Services (`docker-compose.observability.yaml`)
Optional monitoring and APM stack for distributed tracing:

- **Elasticsearch** (port 9200): Storage backend for traces
- **SkyWalking OAP** (ports 11800, 12800): APM server
- **SkyWalking UI** (port 8088): Web dashboard

**When to use**: Enable when debugging distributed traces, performance issues, or during integration testing. Can be disabled to save resources (~2GB RAM).

### Job Services (`docker-compose.jobs.yaml`)
Async workload services for batch processing and event-driven features:

- **XXL-Job Admin** (port 7070): Distributed job scheduling platform
- **RocketMQ NameServer** (port 9876): Message queue registry
- **RocketMQ Broker** (ports 10909-10912, 7071, 8081): Message broker with proxy
- **RocketMQ Dashboard** (port 4002): Web console

**When to use**: Start when working on scheduled tasks, batch jobs, or event-driven features. Not needed for synchronous API development.

---

## First-Time Setup

Before starting services for the first time, create the required directory structure and configuration files:

```bash
# Create directory structure
mkdir -p ~/.papertrace/docker/{mysql/{data,conf.d,init},redis/data,nacos/{data,logs},minio/data,es/data,xxl-job-admin/logs,rocketmq/{namesrv/{logs,store},broker/{logs,store,conf}}}

# Create Redis configuration (minimal example)
cat > ~/.papertrace/docker/redis/redis.conf << 'EOF'
bind 0.0.0.0
protected-mode no
port 6379
appendonly yes
appendfilename "appendonly.aof"
dir /data
EOF

# Create RocketMQ broker configuration (minimal example)
cat > ~/.papertrace/docker/rocketmq/broker/conf/broker.conf << 'EOF'
brokerClusterName = PapertraceCluster
brokerName = broker-a
brokerId = 0
deleteWhen = 04
fileReservedTime = 48
brokerRole = ASYNC_MASTER
flushDiskType = ASYNC_FLUSH
EOF

# Set appropriate permissions (important for Elasticsearch)
chmod -R 777 ~/.papertrace/docker/es/data
```

**Note**: You may need to copy existing configuration files from your old setup (`docker/mysql/conf.d/`, `docker/mysql/init/`, etc.) to the new location if you have custom configurations.

---

## Usage Patterns

### Quick Start (Minimal Environment)

Start only core services for fastest startup and lowest resource usage:

```bash
cd docker/compose
docker compose -f docker-compose.core.yaml up -d
```

**Use case**: API development, frontend development, quick testing

---

### File Upload Development (Core + Storage)

Add storage services for file upload features:

```bash
docker compose -f docker-compose.core.yaml \
               -f docker-compose.storage.yaml up -d
```

**Use case**: Working on file upload, document management, image processing features

---

### Standard Development (Core + Storage + Jobs)

Add both storage and job services:

```bash
docker compose -f docker-compose.core.yaml \
               -f docker-compose.storage.yaml \
               -f docker-compose.jobs.yaml up -d
```

**Use case**: Working on batch import jobs, scheduled tasks, message consumers with file uploads

---

### Full Observability (Core + Monitoring)

Enable APM for distributed tracing:

```bash
docker compose -f docker-compose.core.yaml \
               -f docker-compose.observability.yaml up -d
```

**Use case**: Debugging microservice interactions, performance analysis, latency tracing

---

### Complete Environment (All Services)

Start all services using the main file:

```bash
docker compose -f docker-compose.dev.yaml up -d
```

**Use case**: Integration testing, full-stack development, production-like environment

---

## Common Commands

### Start Services
```bash
# All services
docker compose -f docker-compose.dev.yaml up -d

# Specific stack
docker compose -f docker-compose.core.yaml up -d

# Multiple stacks
docker compose -f docker-compose.core.yaml \
               -f docker-compose.jobs.yaml up -d
```

### Check Status
```bash
docker compose -f docker-compose.dev.yaml ps
docker compose -f docker-compose.core.yaml ps
```

### View Logs
```bash
# All services
docker compose -f docker-compose.dev.yaml logs -f

# Specific service
docker compose -f docker-compose.dev.yaml logs -f mysql

# Specific stack
docker compose -f docker-compose.core.yaml logs -f
```

### Stop Services
```bash
# All services
docker compose -f docker-compose.dev.yaml down

# Specific stack
docker compose -f docker-compose.core.yaml down

# With volume cleanup
docker compose -f docker-compose.dev.yaml down -v
```

### Restart Single Service
```bash
docker compose -f docker-compose.dev.yaml restart mysql
docker compose -f docker-compose.observability.yaml restart skywalking-oap
```

---

## Service Access URLs

### Core Services
- **MySQL**: `localhost:13306` (root/123456)
- **Redis**: `localhost:16379`
- **Nacos Console**: http://localhost:8848/nacos (patra/patra)

### Storage Services
- **MinIO API**: `localhost:19000` (minioadmin/minioadmin123)
- **MinIO Console**: http://localhost:19001 (minioadmin/minioadmin123)

### Observability Services
- **Elasticsearch**: http://localhost:9200
- **SkyWalking UI**: http://localhost:8088

### Job Services
- **XXL-Job Admin**: http://localhost:7070/xxl-job-admin (admin/123456)
- **RocketMQ Dashboard**: http://localhost:4002

---

## Resource Requirements

### Minimal (Core Only)
- **CPU**: 2 cores
- **Memory**: ~1GB
- **Services**: 3

### Standard (Core + Storage)
- **CPU**: 2 cores
- **Memory**: ~1.5GB
- **Services**: 4

### Extended (Core + Storage + Jobs)
- **CPU**: 4 cores
- **Memory**: ~3.5GB
- **Services**: 8

### Full (All Services)
- **CPU**: 6+ cores
- **Memory**: ~5.5GB
- **Services**: 11

---

## Health Checks

All services include health checks. Wait for all services to be healthy:

```bash
# Check health status
docker compose -f docker-compose.dev.yaml ps

# Wait for services to be healthy (example for core services)
until docker compose -f docker-compose.core.yaml ps | grep -q '(healthy)'; do
  echo "Waiting for services to be healthy..."
  sleep 5
done
```

---

## Troubleshooting

### Service Won't Start

1. Check logs:
   ```bash
   docker compose -f docker-compose.dev.yaml logs <service-name>
   ```

2. Verify port availability:
   ```bash
   lsof -i :13306  # Example for MySQL
   ```

3. Check service health:
   ```bash
   docker compose -f docker-compose.dev.yaml ps
   ```

### Out of Memory

Reduce resource usage by starting only needed services:
```bash
# Disable observability stack
docker compose -f docker-compose.observability.yaml down

# Or start only core services
docker compose -f docker-compose.core.yaml up -d
```

### Permission Issues (macOS/Linux)

Fix volume permissions:
```bash
sudo chown -R $(id -u):$(id -g) ~/.papertrace/docker/
# Or for specific services:
sudo chown -R $(id -u):$(id -g) ~/.papertrace/docker/mysql
sudo chown -R $(id -u):$(id -g) ~/.papertrace/docker/es
```

### Reset Everything

Remove all data and restart:
```bash
# Stop all services
docker compose -f docker-compose.dev.yaml down -v

# Remove all data (WARNING: This deletes all persistent data!)
rm -rf ~/.papertrace/docker/mysql/data
rm -rf ~/.papertrace/docker/redis/data
rm -rf ~/.papertrace/docker/es/data
rm -rf ~/.papertrace/docker/nacos/data
rm -rf ~/.papertrace/docker/rocketmq/*/store

# Or remove everything at once
rm -rf ~/.papertrace/docker

# Recreate directory structure and configurations (see First-Time Setup section)
# Then restart services
docker compose -f docker-compose.dev.yaml up -d
```

---

## Migration from Monolithic Setup

The original `docker-compose.dev.yaml` has been split into three modular files, and volume mounts now use `~/.papertrace/docker/` instead of relative paths.

### What Changed
- ✅ Service definitions remain identical
- ✅ All ports and configurations preserved
- ✅ Network configuration unchanged (`patra-net`)
- ✅ Environment variables and health checks intact
- ⚠️ **Volume paths updated**: From `../service/` to `~/.papertrace/docker/service/`

### Migrating Existing Data

If you have existing data in the old location (`docker/mysql/`, `docker/redis/`, etc.), migrate it to the new location:

```bash
# Stop all services first
docker compose -f docker-compose.dev.yaml down

# Create new directory structure
mkdir -p ~/.papertrace/docker

# Copy existing data (adjust path to your project root)
cd /path/to/Papertrace-api
cp -r docker/mysql ~/.papertrace/docker/
cp -r docker/redis ~/.papertrace/docker/
cp -r docker/nacos ~/.papertrace/docker/
cp -r docker/minio ~/.papertrace/docker/  # If you have existing MinIO data
cp -r docker/es ~/.papertrace/docker/
cp -r docker/xxl-job-admin ~/.papertrace/docker/
cp -r docker/rocketmq ~/.papertrace/docker/

# Verify migration
ls -la ~/.papertrace/docker/

# Start services with new configuration
cd docker/compose
docker compose -f docker-compose.dev.yaml up -d
```

**Note**: After successful migration, you can optionally remove the old data directories to free up space:
```bash
# Optional: Remove old data after verifying new setup works
rm -rf /path/to/Papertrace-api/docker/{mysql,redis,nacos,minio,es,xxl-job-admin,rocketmq}
```

### Backward Compatibility
The main file still works as before:
```bash
docker compose -f docker-compose.dev.yaml up -d
```

### Benefits
- **Faster startup**: Start only needed services
- **Resource efficiency**: Save RAM by disabling unused stacks
- **Better organization**: Clear separation by lifecycle
- **Flexible workflows**: Mix and match stacks as needed

---

## Best Practices

1. **Start core services first**: Always begin with `docker-compose.core.yaml`
2. **Use selective startup**: Only run services you're actively working on
3. **Monitor resources**: Use `docker stats` to track memory usage
4. **Clean up regularly**: Run `docker compose down -v` to remove unused volumes
5. **Check health status**: Wait for health checks before starting application services

---

## Dependencies Between Stacks

```
┌─────────────────────────────────────┐
│ Core Services                       │  (No dependencies)
│ - MySQL, Redis, Nacos               │
└─────────────────────────────────────┘
         ▲                    ▲             ▲
         │                    │             │
         │                    │             │
┌────────┴─────────┐  ┌──────┴────────┐  ┌┴────────────────────┐
│ Jobs Services    │  │ Observability │  │ Storage Services    │
│ - XXL-Job (MySQL)│  │ - ES          │  │ - MinIO             │
│ - RocketMQ       │  │ - SkyWalking  │  │ (No dependencies)   │
└──────────────────┘  └───────────────┘  └─────────────────────┘
```

- **Jobs stack** depends on MySQL for XXL-Job
- **Observability stack** has internal dependency (SkyWalking → Elasticsearch)
- **Storage stack** is self-contained (no external dependencies)
- **RocketMQ** services are self-contained (no external dependencies)

---

## Environment Variables

Configure via `.env` file or shell environment:

```bash
# Example .env file in docker/ directory
MYSQL_ROOT_PASSWORD=your_secure_password
MINIO_ROOT_USER=your_minio_user
MINIO_ROOT_PASSWORD=your_minio_password
```

Current configurable variables:
- `MYSQL_ROOT_PASSWORD` (default: 123456)
- `MINIO_ROOT_USER` (default: minioadmin)
- `MINIO_ROOT_PASSWORD` (default: minioadmin123)

---

## MinIO Usage Guide

### Accessing MinIO Console

After starting the storage stack, access the MinIO web console at http://localhost:19001:

1. Login with credentials: `minioadmin` / `minioadmin123` (or your custom credentials from `.env`)
2. The following bucket is automatically created:
   - `dev-ingest` - Development environment storage for patra-ingest service

### Connecting from Application

Use these settings in your Spring Boot application configuration (Nacos):

```yaml
patra:
  object-storage:
    active-provider: minio
    max-file-size: 104857600  # 100MB
    providers:
      minio:
        endpoint: http://localhost:19000
        access-key: minioadmin
        secret-key: minioadmin123

patra:
  ingest:
    storage:
      bucket: dev-ingest  # Default bucket for patra-ingest
```

### Using MinIO Client (mc)

The `minio-init` container includes the `mc` command-line tool. To use it:

```bash
# Access the minio container
docker exec -it patra-minio sh

# Inside the container, mc is already configured
mc ls /data  # List all buckets
mc ls /data/dev-ingest  # List files in a bucket
```

Or use `mc` from your host machine:

```bash
# Install mc (macOS)
brew install minio/stable/mc

# Configure connection
mc alias set papertrace http://localhost:19000 minioadmin minioadmin123

# List buckets
mc ls papertrace

# Upload a file
mc cp /path/to/file.pdf papertrace/dev-ingest/

# Download a file
mc cp papertrace/dev-ingest/file.pdf ./
```

### Creating Additional Buckets

To create additional buckets:

```bash
# Using mc command
docker exec -it patra-minio sh -c \
  "mc mb /data/my-new-bucket && mc anonymous set none /data/my-new-bucket"

# Or via MinIO Console
# Navigate to http://localhost:19001 → Buckets → Create Bucket
```

### Testing Upload from Command Line

```bash
# Test file upload
curl -X PUT \
  -H "Host: dev-ingest.localhost" \
  --user minioadmin:minioadmin123 \
  --upload-file /path/to/test.txt \
  http://localhost:19000/dev-ingest/test.txt

# Verify upload
mc ls papertrace/dev-ingest/test.txt
```

---

## Next Steps

1. Start with core services: `docker compose -f docker-compose.core.yaml up -d`
2. Add storage stack for file uploads: `docker compose -f docker-compose.storage.yaml up -d`
3. Add other stacks as needed for your development task
4. Verify health status before running application services
5. Refer to service URLs section for accessing web consoles

For questions or issues, refer to the main project documentation.
