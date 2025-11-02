# patra-gateway-boot — API 网关

> **Spring Cloud Gateway** 服务,提供统一入口、路由、认证和速率限制。

---

## 📌 目的

作为所有 Papertrace API 的**单一入口点**:
- 将请求路由到微服务(registry、ingest 等)
- 认证与授权
- 速率限制
- 请求/响应日志记录
- CORS 处理
- 下游服务的熔断器

---

## 🏗️ 架构

```
客户端
  ↓
patra-gateway (端口 8080)
  ↓
  ├─→ patra-registry (端口 8081)
  ├─→ patra-ingest (端口 8082)
  └─→ ... (其他服务)
```

---

## 🔌 路由

### Registry 服务

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

**示例**:
- `GET /registry/provenance/pubmed` → `patra-registry:8081/provenance/pubmed`

### Ingest 服务

```yaml
        - id: ingest-service
          uri: lb://patra-ingest
          predicates:
            - Path=/ingest/**
          filters:
            - StripPrefix=1
```

**示例**:
- `POST /ingest/plans` → `patra-ingest:8082/plans`

---

## 🛡️ 安全

### 认证

**基于令牌**(JWT 或 API 密钥):
- 从 `Authorization` 头提取令牌
- 与认证服务验证
- 将用户上下文注入到下游请求

### 速率限制

**基于 Redis** 的速率限制器:
```yaml
spring:
  cloud:
    gateway:
      routes:
        - filters:
            - name: RequestRateLimiter
              args:
                redis-rate-limiter:
                  replenish-rate: 100   # 令牌/秒
                  burst-capacity: 200   # 最大突发量
```

---

## 🔗 依赖

**核心依赖**:
- Spring Cloud Gateway
- Spring Cloud LoadBalancer
- Spring Data Redis (速率限制)
- Resilience4j (熔断器)

---

## 🚀 本地运行

```bash
cd patra-gateway-boot
mvn spring-boot:run
```

**默认端口**: 8080

---

## ⚙️ 配置

```yaml
server:
  port: 8080

spring:
  cloud:
    gateway:
      discovery:
        locator:
          enabled: true    # 从 Nacos 自动发现服务
      globalcors:
        cors-configurations:
          '[/**]':
            allowed-origins: "*"
            allowed-methods: [GET, POST, PUT, DELETE]
            allowed-headers: "*"
```

---

## 📊 可观测性

- **指标**: 网关指标(请求、延迟、错误)
- **追踪**: 将追踪 ID 传播到下游服务
- **日志**: 带追踪上下文的请求/响应日志记录

---

## 🪵 日志记录(Starter v1.0)

本服务使用 Spring Boot 默认日志记录;分布式追踪由 SkyWalking agent 处理。

- 依赖(已包含):
  ```xml
  <dependency>
      <groupId>com.papertrace</groupId>
      <!-- 日志记录由服务特定配置或默认值处理 -->
  </dependency>
  ```

- 最小配置(application.yml):
  ```yaml
  spring:
    application:
      name: patra-gateway

  papertrace:
    logging:
      # 日志记录/追踪由 SkyWalking agent + Spring Boot 默认值处理
  ```

- 日志模式包含 MDC 字段: `traceId`、`correlationId`、`service`、`environment`。

- 通过 Nacos 动态调整日志级别(传播 ≤60s):
  ```yaml
  # logging-patra-gateway.yml (Nacos)
  logging:
    level:
      root: INFO
      # 根据需要调整包日志级别
      org.springframework.cloud.gateway: INFO
  ```

- 示例输出(简化):
  ```
  2025-10-17T10:30:45Z [traceId=...][correlationId=...] [service=patra-gateway] INFO  route=/ingest/** status=200 duration=53ms
  ```

参考:
- docs/logging/operations-guide.md (如何更改日志级别)
- specs/001-logging-starter/quickstart.md (开发者快速入门)
- specs/001-logging-starter/contracts/mdc-fields-reference.md


**最后更新**: 2025-01-12
