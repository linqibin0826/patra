# Local Development Runbook

## Dependencies
- Docker: MySQL, Redis, RocketMQ, Nacos, Elasticsearch (optional)
- Start:
```bash
cd docker/compose && docker compose up -d
```

## Build & Test
```bash
# Full build
./mvnw -T 1C clean verify

# Fast compile
./mvnw -q -DskipTests compile

# Module tests
./mvnw -q -pl <module> test
```

## Run Services
```bash
# Ingest
cd patra-ingest/patra-ingest-boot && ../../mvnw spring-boot:run

# Registry
./mvnw -pl patra-registry/patra-registry-boot -am spring-boot:run

# Egress Gateway
./mvnw -pl patra-egress-gateway/patra-egress-gateway-boot -am spring-boot:run
```

## Troubleshooting
- Verify ports and health; check application logs; ensure Nacos config is reachable; check DB migrations (Flyway) status.
