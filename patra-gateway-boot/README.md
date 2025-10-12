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

**Last Updated**: 2025-01-12
