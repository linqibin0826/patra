# patra-gateway-boot

## Overview
- Spring Cloud Gateway entrypoint for Papertrace. Routes external traffic to internal services.

## Entry Point
- Application class: `patra-gateway-boot/src/main/java/com/patra/gateway/PatraGatewayApplication.java:1`

## Configuration
- Main config: `patra-gateway-boot/src/main/resources/application.yml:1`
- Highlights
  - Service discovery: Nacos (`spring.cloud.nacos.discovery.*`).
  - Routes: `patra-ingest`, `patra-registry` with `StripPrefix=1`.
  - Server port: `9528`.

### Example Routes
```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: patra-ingest
          uri: lb://patra-ingest
          predicates:
            - Path=/patra-ingest/**
          filters:
            - StripPrefix=1

        - id: patra-registry
          uri: lb://patra-registry
          predicates:
            - Path=/patra-registry/**
          filters:
            - StripPrefix=1
```

## Cross-Cutting
- CORS and rate limiting: configure via external `patra-gateway.yaml` in Nacos if needed.
- Security: front this gateway with an identity proxy if exposed publicly.

## Observability
- Propagate `traceId`; include correlation and client info in access logs.
- Logging levels: see `application.yml` (`org.springframework.cloud.gateway`, `loadbalancer`).

## Run Locally
```bash
./mvnw -pl patra-gateway-boot spring-boot:run
```

## Related Docs
- Services catalog: `docs/services/index.md:1`
- Deployment topology: `docs/architecture/Deployment-Topology.md:1`

## Open TODOs
- Add CORS, rate limiting, and authN/authZ policies via Nacos-managed config.
