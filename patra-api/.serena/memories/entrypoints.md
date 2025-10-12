# Entrypoints & Running Locally

- Prereqs: Start Docker dependencies first: `cd docker/compose && docker compose up -d`
- Ingest Service:
  - Run: `./mvnw -pl patra-ingest/patra-ingest-boot -am spring-boot:run`
  - Jobs/Consumers: XXL-Job schedulers and RocketMQ consumers in `patra-ingest-adapter`
- Registry Service:
  - Run: `./mvnw -pl patra-registry/patra-registry-boot -am spring-boot:run`
  - RPC APIs: Feign-style internal interfaces in `patra-registry-api`
- Egress Gateway:
  - Run: `./mvnw -pl patra-egress-gateway/patra-egress-gateway-boot -am spring-boot:run`
  - Endpoint: `POST /api/egress/call`
- API Gateway (edge):
  - Run: `./mvnw -pl patra-gateway-boot spring-boot:run`
  - Port: `9528` (configurable)

Notes
- Configure `application-local.yaml` and Nacos properties for local runs; never commit secrets.
- If errors occur, check: port availability, Nacos connectivity, Flyway migration status, Docker container health.
