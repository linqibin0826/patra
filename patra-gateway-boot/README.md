# patra-gateway-boot — API Gateway

> **Spring Cloud Gateway** service providing unified entry point, routing, authentication, and rate limiting.

---

## 📌 Purpose

Serves as the **single entry point** for all Papertrace APIs:
- Route requests to microservices (registry, ingest, etc.)
- Authentication & authorization
- Rate limiting
- Request/response logging
- CORS handling
- Circuit breaker for downstream services

---

## 🏗️ Architecture

```
Client
  ↓
patra-gateway (port 8080)
  ↓
  ├─→ patra-registry (port 8081)
  ├─→ patra-ingest (port 8082)
  └─→ ... (other services)
```

---

## 🔌 Routes

### Registry Service

```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: registry-service
          uri: lb://patra-registry
          predicates:
            - Path=/registry/**
          filters:
            - StripPrefix=1
            - name: CircuitBreaker
              args:
                name: registry-cb
                fallbackUri: forward:/fallback/registry
```

**Example**:
- `GET /registry/provenance/pubmed` → `patra-registry:8081/provenance/pubmed`

### Ingest Service

```yaml
        - id: ingest-service
          uri: lb://patra-ingest
          predicates:
            - Path=/ingest/**
          filters:
            - StripPrefix=1
```

**Example**:
- `POST /ingest/plans` → `patra-ingest:8082/plans`

---

## 🛡️ Security

### Authentication

**Token-based** (JWT or API keys):
- Extract token from `Authorization` header
- Validate with auth service
- Inject user context into downstream requests

### Rate Limiting

**Redis-backed** rate limiter:
```yaml
spring:
  cloud:
    gateway:
      routes:
        - filters:
            - name: RequestRateLimiter
              args:
                redis-rate-limiter:
                  replenish-rate: 100   # tokens/second
                  burst-capacity: 200   # max burst
```

---

## 🔗 Dependencies

**Key dependencies**:
- Spring Cloud Gateway
- Spring Cloud LoadBalancer
- Spring Data Redis (rate limiting)
- Resilience4j (circuit breaker)

---

## 🚀 Running Locally

```bash
cd patra-gateway-boot
mvn spring-boot:run
```

**Default Port**: 8080

---

## ⚙️ Configuration

```yaml
server:
  port: 8080

spring:
  cloud:
    gateway:
      discovery:
        locator:
          enabled: true    # Auto-discover services from Nacos
      globalcors:
        cors-configurations:
          '[/**]':
            allowed-origins: "*"
            allowed-methods: [GET, POST, PUT, DELETE]
            allowed-headers: "*"
```

---

## 📊 Observability

- **Metrics**: Gateway metrics (requests, latency, errors)
- **Tracing**: Propagates trace IDs to downstream services
- **Logs**: Request/response logging with trace context

---

## 🪵 Logging (Starter v1.0)

This service uses Spring Boot default logging; distributed tracing is handled by SkyWalking agent.

- Dependency (already included):
  ```xml
  <dependency>
      <groupId>com.papertrace</groupId>
      <!-- logging handled by service-specific config or defaults -->
  </dependency>
  ```

- Minimal configuration (application.yml):
  ```yaml
  spring:
    application:
      name: patra-gateway

  papertrace:
    logging:
      # logging/tracing handled by SkyWalking agent + Spring Boot defaults
  ```

- Log pattern includes MDC fields: `traceId`, `correlationId`, `service`, `environment`.

- Dynamic log levels via Nacos (propagates ≤60s):
  ```yaml
  # logging-patra-gateway.yml (Nacos)
  logging:
    level:
      root: INFO
      # adjust package log levels as needed
      org.springframework.cloud.gateway: INFO
  ```

- Example output (trimmed):
  ```
  2025-10-17T10:30:45Z [traceId=...][correlationId=...] [service=patra-gateway] INFO  route=/ingest/** status=200 duration=53ms
  ```

References:
- docs/logging/operations-guide.md (how to change log levels)
- specs/001-logging-starter/quickstart.md (developer quickstart)
- specs/001-logging-starter/contracts/mdc-fields-reference.md


**Last Updated**: 2025-01-12
