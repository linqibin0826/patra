# patra-gateway-boot 深入指南

> API 网关（Gateway）- 统一接入网关，实现路由、安全、观测的边缘服务

---

## 1. 模块定位

### 服务/组件作用
**patra-gateway-boot** 是 Papertrace 项目的 API 网关，作为平台的统一入口，负责：
- 路由聚合：将外部请求路由到对应的微服务
- 服务发现：整合 Nacos Discovery，动态感知服务实例
- 横切关注点：鉴权、限流、熔断、日志、监控
- 错误统一：对接 ProblemDetail，提供一致的错误响应

### 主要消费者
- **外部客户端**：Web 前端、移动 App、第三方系统
- **内部前端**：管理后台、数据分析面板
- **开发者**：通过网关统一访问各微服务 API

### 架构边界
- **所属分层**：边缘层（Edge Layer）
- **架构模式**：基于 Spring Cloud Gateway（WebFlux/Netty）
- **依赖方向**：聚合下游业务服务（patra-ingest、patra-registry 等），不包含业务逻辑
- **设计原则**：无状态、高性能、可扩展

---

## 2. 核心能力

### 2.1 路由聚合
- **能力摘要**：根据请求路径、方法、Header、Query 参数将请求路由到对应的微服务
- **价值**：客户端只需知道网关地址，无需关心后端服务拓扑

### 2.2 服务发现与负载均衡
- **能力摘要**：整合 Nacos Discovery + Spring Cloud LoadBalancer，实时感知服务实例变化
- **价值**：自动故障转移、动态扩缩容、无需手动配置服务地址

### 2.3 错误模型统一
- **能力摘要**：对接 `patra-spring-boot-starter-core`，将下游服务异常统一转换为 ProblemDetail
- **价值**：提供一致的错误响应格式，方便客户端处理

### 2.4 横切扩展点（规划中）
- **能力摘要**：全局/局部过滤器支持 JWT 鉴权、限流、熔断、Trace 透传、监控指标
- **价值**：解耦业务逻辑与横切关注点，统一安全和可靠性策略

### 2.5 路由配置热更新（规划中）
- **能力摘要**：通过 Nacos Config 动态更新路由规则，无需重启服务
- **价值**：灵活调整流量分发、支持灰度发布和 A/B 测试

---

## 3. 分层结构与依赖

### 3.1 模块结构
```
patra-gateway-boot/
├── src/main/java/
│   └── com/patra/gateway/
│       └── PatraGatewayApplication.java   # 启动类
├── src/main/resources/
│   ├── application.yml                     # 基础配置
│   └── bootstrap.yml                       # Nacos 配置（可选）
└── pom.xml                                 # 依赖管理
```

**未来扩展目录**（规划中）：
```
src/main/java/com/patra/gateway/
├── config/                    # 配置 Bean（路由、过滤器）
├── filter/
│   ├── global/                # 全局过滤器（Trace、日志、指标）
│   └── route/                 # 路由过滤器（鉴权、限流）
├── predicate/                 # 自定义断言工厂
├── error/                     # 错误处理器
├── rate/                      # 限流策略
├── observability/             # 可观测性（指标、日志）
└── security/                  # 安全策略（JWT、OAuth2）
```

### 3.2 关键依赖
| 依赖 | 版本 | 作用 |
|------|------|------|
| `spring-cloud-starter-gateway` | 4.1.2 | Spring Cloud Gateway 核心 |
| `spring-cloud-starter-loadbalancer` | 4.1.1 | 客户端负载均衡 |
| `spring-cloud-starter-alibaba-nacos-discovery` | 2023.0.1.0 | Nacos 服务发现 |
| `spring-cloud-starter-alibaba-nacos-config` | 2023.0.1.0 | Nacos 配置中心 |
| `patra-spring-boot-starter-core` | 0.1.0 | 统一错误处理、Trace SPI |
| `spring-boot-starter-actuator` | 3.2.4 | 健康检查、指标导出 |
| `resilience4j-spring-boot3`（规划） | 2.2.0 | 限流、熔断、重试 |

### 3.3 禁止事项
- ❌ 不在网关层编写业务逻辑（保持纯粹的路由和横切关注点）
- ❌ 不使用阻塞 I/O（基于 Reactor/Netty，避免阻塞线程）
- ❌ 不在过滤器中进行大量计算或数据库查询（影响性能）
- ❌ 不在网关层缓存业务数据（交给下游服务或专用缓存服务）

---

## 4. 运行与配置

### 4.1 本地运行

#### 前置条件
```bash
# 1. 启动 Nacos（服务发现 + 配置中心）
docker-compose -f docker/docker-compose.yml up -d nacos

# 2. 启动下游服务（至少一个）
cd patra-registry && mvn spring-boot:run
cd patra-ingest && mvn spring-boot:run
```

#### 启动网关
```bash
# 方式1：Maven 插件
cd patra-gateway-boot
mvn spring-boot:run

# 方式2：JAR 包
mvn clean package -DskipTests
java -jar target/patra-gateway-boot-0.1.0-SNAPSHOT.jar

# 方式3：IDE 运行
# 直接运行 PatraGatewayApplication.main()
```

### 4.2 基础配置（application.yml）
```yaml
spring:
  application:
    name: patra-gateway

  cloud:
    nacos:
      server-addr: ${NACOS_ADDR:127.0.0.1:8848}
      username: ${NACOS_USERNAME:nacos}
      password: ${NACOS_PASSWORD:nacos}
      discovery:
        namespace: ${NACOS_NAMESPACE:public}
        group: ${NACOS_GROUP:DEFAULT_GROUP}

    gateway:
      routes:
        # 采集服务路由
        - id: patra-ingest
          uri: lb://patra-ingest              # lb:// 表示使用负载均衡
          predicates:
            - Path=/patra-ingest/**            # 匹配路径前缀
          filters:
            - StripPrefix=1                     # 去掉第一层路径（/patra-ingest）

        # 配置中心路由
        - id: patra-registry
          uri: lb://patra-registry
          predicates:
            - Path=/patra-registry/**
          filters:
            - StripPrefix=1

# 日志配置（开发阶段）
logging:
  level:
    org.springframework.cloud.gateway: DEBUG
    org.springframework.cloud.loadbalancer: DEBUG

# 服务端口
server:
  port: 9528
```

### 4.3 Nacos 动态路由配置（推荐）
在 Nacos 创建配置文件：
- **Data ID**: `patra-gateway.yaml`
- **Group**: `DEFAULT_GROUP`
- **配置格式**: YAML

```yaml
spring:
  cloud:
    gateway:
      routes:
        # 动态路由示例：带 Header 和 Query 断言
        - id: patra-ingest-api
          uri: lb://patra-ingest
          predicates:
            - Path=/api/ingest/**
            - Header=X-Api-Version, v1          # 要求 Header 包含 X-Api-Version: v1
          filters:
            - StripPrefix=2                      # 去掉 /api/ingest
            - AddRequestHeader=X-Gateway, patra  # 添加请求头
            - AddResponseHeader=X-Powered-By, Papertrace

        # 灰度发布示例：按权重路由
        - id: patra-registry-v2-canary
          uri: lb://patra-registry-v2
          predicates:
            - Path=/patra-registry/**
            - Weight=registry-group, 10          # 10% 流量到 v2
          filters:
            - StripPrefix=1

        - id: patra-registry-v1-stable
          uri: lb://patra-registry
          predicates:
            - Path=/patra-registry/**
            - Weight=registry-group, 90          # 90% 流量到 v1
          filters:
            - StripPrefix=1

      # 全局默认过滤器
      default-filters:
        - DedupeResponseHeader=Access-Control-Allow-Origin Access-Control-Allow-Credentials, RETAIN_UNIQUE
```

### 4.4 引入动态配置
```yaml
# application.yml
spring:
  config:
    import:
      - "optional:nacos:patra-gateway.yaml?group=DEFAULT_GROUP&namespace=public"
```

---

## 5. 路由配置详解

### 5.1 Predicates（断言）
断言用于匹配请求，支持以下类型：

| Predicate Factory | 示例 | 说明 |
|-------------------|------|------|
| `Path` | `Path=/api/**` | 匹配路径 |
| `Method` | `Method=GET,POST` | 匹配 HTTP 方法 |
| `Header` | `Header=X-Request-Id, \d+` | 匹配请求头（支持正则） |
| `Query` | `Query=token` | 匹配查询参数 |
| `Cookie` | `Cookie=session, \w+` | 匹配 Cookie |
| `Host` | `Host=**.example.com` | 匹配 Host 头 |
| `RemoteAddr` | `RemoteAddr=192.168.1.0/24` | 匹配 IP 地址段 |
| `Weight` | `Weight=group1, 8` | 权重路由（灰度发布） |
| `Before/After/Between` | `After=2025-01-01T00:00:00+08:00` | 时间窗口路由 |

### 5.2 Filters（过滤器）
过滤器用于修改请求或响应，支持以下类型：

#### 路径操作
```yaml
filters:
  - StripPrefix=1                      # 去掉前缀：/api/users → /users
  - PrefixPath=/api                    # 添加前缀：/users → /api/users
  - RewritePath=/old/(?<segment>.*), /new/$\{segment}  # 路径重写
```

#### Header 操作
```yaml
filters:
  - AddRequestHeader=X-Request-Source, gateway
  - RemoveRequestHeader=Cookie
  - AddResponseHeader=X-Response-Time, 100ms
  - SetResponseHeader=X-RateLimit-Limit, 1000
```

#### 重定向与重写
```yaml
filters:
  - RedirectTo=302, https://new.example.com
  - SetPath=/new-path
  - SetStatus=200
```

#### 限流与熔断（规划中）
```yaml
filters:
  - name: RequestRateLimiter
    args:
      redis-rate-limiter.replenishRate: 10      # 令牌桶填充速率
      redis-rate-limiter.burstCapacity: 20      # 令牌桶容量
      key-resolver: "#{@userKeyResolver}"       # 限流维度（IP、用户等）
  
  - name: CircuitBreaker
    args:
      name: myCircuitBreaker
      fallbackUri: forward:/fallback            # 熔断降级端点
```

### 5.3 路由优先级
路由按定义顺序匹配，**第一个匹配的路由生效**。

```yaml
routes:
  # 高优先级：特定路径
  - id: admin-api
    uri: lb://patra-admin
    predicates:
      - Path=/admin/**
    order: 1                          # order 越小，优先级越高

  # 低优先级：通配路径
  - id: default-api
    uri: lb://patra-default
    predicates:
      - Path=/**
    order: 100
```

---

## 6. 过滤器链（Filters）

### 6.1 请求生命周期
```mermaid
graph LR
    A[Client Request] --> B[Global Pre Filters]
    B --> C[Route Pre Filters]
    C --> D[Downstream Service]
    D --> E[Route Post Filters]
    E --> F[Global Post Filters]
    F --> G[Client Response]
```

### 6.2 全局过滤器（规划中）
```java
@Component
@Order(-1)  // 越小越先执行
public class TraceIdGlobalFilter implements GlobalFilter {
    
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // Pre Filter: 生成 TraceId
        String traceId = UUID.randomUUID().toString();
        exchange.getRequest().mutate()
                .header("X-Trace-Id", traceId)
                .build();
        
        // Post Filter: 添加响应头
        return chain.filter(exchange).then(Mono.fromRunnable(() -> {
            exchange.getResponse().getHeaders().add("X-Trace-Id", traceId);
        }));
    }
}
```

### 6.3 路由过滤器（规划中）
```java
@Component
public class JwtAuthenticationGatewayFilterFactory 
    extends AbstractGatewayFilterFactory<JwtAuthenticationGatewayFilterFactory.Config> {
    
    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            String token = exchange.getRequest().getHeaders().getFirst("Authorization");
            
            // 验证 JWT
            if (!isValidJwt(token)) {
                exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                return exchange.getResponse().setComplete();
            }
            
            // 提取用户信息并添加到请求头
            String userId = extractUserId(token);
            exchange.getRequest().mutate()
                    .header("X-User-Id", userId)
                    .build();
            
            return chain.filter(exchange);
        };
    }
    
    public static class Config {
        private String headerName = "Authorization";
        // Getters and setters
    }
}
```

---

## 7. 安全策略

### 7.1 CORS 配置
```yaml
spring:
  cloud:
    gateway:
      globalcors:
        cors-configurations:
          '[/**]':
            allowedOrigins:
              - "https://app.papertrace.com"
              - "http://localhost:3000"
            allowedMethods:
              - GET
              - POST
              - PUT
              - DELETE
              - OPTIONS
            allowedHeaders:
              - "*"
            exposedHeaders:
              - X-Trace-Id
              - X-RateLimit-Limit
            allowCredentials: true
            maxAge: 3600
```

### 7.2 JWT 鉴权（规划中）
```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: secure-api
          uri: lb://patra-admin
          predicates:
            - Path=/admin/**
          filters:
            - JwtAuthentication              # 自定义过滤器
```

### 7.3 请求头白名单（安全）
```java
@Component
public class SecureHeadersGlobalFilter implements GlobalFilter {
    
    private static final Set<String> ALLOWED_HEADERS = Set.of(
        "Content-Type", "Authorization", "X-Request-Id"
    );
    
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // 移除不安全的请求头
        HttpHeaders headers = exchange.getRequest().getHeaders();
        HttpHeaders filteredHeaders = new HttpHeaders();
        headers.forEach((key, value) -> {
            if (ALLOWED_HEADERS.contains(key)) {
                filteredHeaders.addAll(key, value);
            }
        });
        
        // 移除敏感响应头
        return chain.filter(exchange).then(Mono.fromRunnable(() -> {
            exchange.getResponse().getHeaders().remove("X-Powered-By");
            exchange.getResponse().getHeaders().remove("Server");
        }));
    }
}
```

---

## 8. 监控与日志

### 8.1 访问日志
```yaml
# application.yml
logging:
  level:
    reactor.netty.http.server.AccessLog: INFO
    
# 或通过环境变量
-Dreactor.netty.http.server.accessLogEnabled=true
```

访问日志格式：
```
2025-10-08 10:00:00.123 INFO  [reactor-http-nio-2] reactor.netty.http.server.AccessLog: 
127.0.0.1 - - [08/Oct/2025:10:00:00 +0800] "GET /patra-registry/provenance/sources HTTP/1.1" 200 1234 "-" "Mozilla/5.0"
```

### 8.2 慢请求监控（规划中）
```java
@Component
public class SlowRequestMetricsFilter implements GlobalFilter, Ordered {
    
    private final MeterRegistry meterRegistry;
    
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        long startTime = System.currentTimeMillis();
        String path = exchange.getRequest().getPath().value();
        
        return chain.filter(exchange).doFinally(signalType -> {
            long duration = System.currentTimeMillis() - startTime;
            
            // 记录慢请求（超过 1 秒）
            if (duration > 1000) {
                log.warn("Slow request detected: path={} duration={}ms", path, duration);
            }
            
            // 记录指标
            Timer.builder("gateway.request.duration")
                 .tag("path", path)
                 .tag("status", String.valueOf(exchange.getResponse().getStatusCode()))
                 .register(meterRegistry)
                 .record(Duration.ofMillis(duration));
        });
    }
    
    @Override
    public int getOrder() {
        return -100;  // 高优先级
    }
}
```

### 8.3 Prometheus 指标导出
```yaml
# application.yml
management:
  endpoints:
    web:
      exposure:
        include: health,metrics,prometheus
  metrics:
    export:
      prometheus:
        enabled: true
    tags:
      application: ${spring.application.name}
```

访问指标端点：
```bash
# Prometheus 格式指标
curl http://localhost:9528/actuator/prometheus

# 关键指标：
# - spring_cloud_gateway_requests_total: 总请求数
# - spring_cloud_gateway_requests_seconds: 请求时长分布
# - spring_cloud_gateway_active_requests: 活跃请求数
```

---

## 9. 性能优化

### 9.1 连接池配置
```yaml
spring:
  cloud:
    gateway:
      httpclient:
        connect-timeout: 5000               # 连接超时（毫秒）
        response-timeout: 30s               # 响应超时
        pool:
          type: ELASTIC                     # 连接池类型：ELASTIC/FIXED/DISABLED
          max-connections: 1000             # 最大连接数
          max-idle-time: 30s                # 最大空闲时间
          max-life-time: 60s                # 最大生命周期
          eviction-interval: 5s             # 驱逐检查间隔
```

### 9.2 WebFlux 配置
```yaml
spring:
  webflux:
    base-path: /api                         # 统一路径前缀
```

### 9.3 Netty 调优
```yaml
# application.yml
reactor:
  netty:
    pool:
      max-connections: 1000                 # 最大连接数
      max-idle-time: 30s
    ioWorkerCount: 8                        # I/O 工作线程数（默认：CPU 核数 * 2）
```

Java 启动参数：
```bash
java -Dreactor.netty.ioWorkerCount=16 \
     -Dreactor.netty.pool.maxConnections=2000 \
     -jar patra-gateway-boot.jar
```

### 9.4 背压（Backpressure）处理
```java
@Component
public class BackpressureHandlingFilter implements GlobalFilter {
    
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return chain.filter(exchange)
                .timeout(Duration.ofSeconds(30))           // 超时保护
                .onErrorResume(TimeoutException.class, e -> {
                    exchange.getResponse().setStatusCode(HttpStatus.GATEWAY_TIMEOUT);
                    return exchange.getResponse().setComplete();
                });
    }
}
```

---

## 10. 使用示例

### 10.1 通过网关访问 patra-ingest
```bash
# 直接访问 patra-ingest（8080）
curl http://localhost:8080/schedule/tasks

# 通过网关访问（9528）
curl http://localhost:9528/patra-ingest/schedule/tasks

# 响应头包含 TraceId
HTTP/1.1 200 OK
X-Trace-Id: 123e4567-e89b-12d3-a456-426614174000
Content-Type: application/json
```

### 10.2 通过网关访问 patra-registry
```bash
# 查询数据源配置
curl http://localhost:9528/patra-registry/provenance/sources/pubmed

# 查询表达式定义
curl http://localhost:9528/patra-registry/expressions?type=WINDOW
```

### 10.3 灰度发布场景
```yaml
# Nacos 配置：patra-gateway.yaml
spring:
  cloud:
    gateway:
      routes:
        # 10% 流量到新版本
        - id: ingest-v2-canary
          uri: lb://patra-ingest-v2
          predicates:
            - Path=/patra-ingest/**
            - Weight=ingest-group, 10
          filters:
            - StripPrefix=1
            - AddResponseHeader=X-Version, v2

        # 90% 流量到稳定版本
        - id: ingest-v1-stable
          uri: lb://patra-ingest
          predicates:
            - Path=/patra-ingest/**
            - Weight=ingest-group, 90
          filters:
            - StripPrefix=1
            - AddResponseHeader=X-Version, v1
```

测试灰度发布：
```bash
# 发送 100 次请求，观察 X-Version 分布
for i in {1..100}; do
  curl -s -I http://localhost:9528/patra-ingest/health | grep X-Version
done

# 预期结果：约 10 次返回 v2，90 次返回 v1
```

---

## 11. 测试策略

### 11.1 单元测试
```java
@SpringBootTest(webEnvironment = RANDOM_PORT)
@AutoConfigureWebTestClient
class GatewayRoutingTest {
    
    @Autowired
    private WebTestClient webClient;
    
    @Test
    void should_route_to_ingest_service() {
        webClient.get()
                 .uri("/patra-ingest/health")
                 .exchange()
                 .expectStatus().isOk()
                 .expectHeader().valueEquals("X-Service", "patra-ingest");
    }
    
    @Test
    void should_strip_prefix() {
        // /patra-ingest/schedule/tasks → /schedule/tasks
        webClient.get()
                 .uri("/patra-ingest/schedule/tasks")
                 .exchange()
                 .expectStatus().isOk();
        
        // 验证下游服务收到的路径是 /schedule/tasks
    }
}
```

### 11.2 集成测试
```java
@SpringBootTest
@TestPropertySource(properties = {
    "spring.cloud.gateway.routes[0].id=test-route",
    "spring.cloud.gateway.routes[0].uri=http://localhost:8080",
    "spring.cloud.gateway.routes[0].predicates[0]=Path=/test/**"
})
class GatewayIntegrationTest {
    
    @MockBean
    private LoadBalancerClient loadBalancerClient;
    
    @Test
    void should_load_balance_requests() {
        // Mock 服务实例
        when(loadBalancerClient.choose("patra-ingest"))
            .thenReturn(new DefaultServiceInstance("inst-1", "patra-ingest", "localhost", 8080, false));
        
        // 验证负载均衡
        webClient.get().uri("/patra-ingest/health").exchange().expectStatus().isOk();
    }
}
```

### 11.3 压力测试
```bash
# 使用 wrk 进行压测
wrk -t8 -c400 -d30s http://localhost:9528/patra-ingest/health

# 预期结果：
# - Latency P50 < 50ms
# - Latency P95 < 200ms
# - Latency P99 < 500ms
# - QPS > 5000

# 使用 bombardier
bombardier -c 200 -d 60s http://localhost:9528/patra-registry/provenance/sources
```

---

## 12. Roadmap 与风险

### 12.1 近期计划
| 阶段 | 能力 | 验收点 | 预期时间 |
|------|------|--------|---------|
| M1 | 最小可观测 | TraceId 生成 + 请求计数指标 | 2025-11 |
| M2 | 错误统一 | ProblemDetail 适配 + 异常分类指标 | 2025-11 |
| M3 | 安全首批 | JWT 鉴权 + 基础限流 | 2025-12 |
| M4 | 可靠性 | 熔断（Resilience4j）+ 重试策略 | 2025-12 |
| M5 | 开发者体验 | 路由热更新 + 可视化面板 | 2026-01 |

### 12.2 既有风险
| 风险 | 影响 | 缓解方案 |
|------|------|---------|
| 过滤器阻塞导致事件循环卡顿 | 请求排队，P99 延迟飙升 | 1. 禁止阻塞 I/O<br>2. 监控 Netty 事件循环<br>3. 使用 `publishOn()` 切换调度器 |
| 错误透传不一致 | 客户端难以处理错误 | 1. 统一错误格式（ProblemDetail）<br>2. 全局异常处理器<br>3. 错误码文档 |
| 安全策略缺失 | 未授权访问、敏感信息泄露 | 1. 尽快实现 JWT 鉴权<br>2. 请求头白名单<br>3. HTTPS 强制 |
| Nacos 配置变更导致路由失效 | 部分请求 404 | 1. 配置变更审批流程<br>2. 灰度变更（先测试环境）<br>3. 回滚机制 |

### 12.3 回滚策略
```bash
# 1. Nacos 配置回滚
# 在 Nacos 控制台选择历史版本并回滚

# 2. 服务回滚（Kubernetes）
kubectl rollout undo deployment/patra-gateway

# 3. 流量切换（紧急情况）
# 暂时移除网关，客户端直连服务
kubectl scale deployment/patra-gateway --replicas=0
```

---

## 13. 参考资料

### 13.1 内部文档
- [系统架构总览](../../overview/architecture-diagrams.md)
- [平台错误处理规范](../../standards/platform-error-handling.md)
- [Starter 使用指南](../../starters/README.md)

### 13.2 外部参考
- [Spring Cloud Gateway 官方文档](https://docs.spring.io/spring-cloud-gateway/reference/)
- [Reactor Netty 文档](https://projectreactor.io/docs/netty/release/reference/index.html)
- [Spring Cloud LoadBalancer 文档](https://docs.spring.io/spring-cloud-commons/reference/spring-cloud-commons/loadbalancer.html)
- [Nacos 服务发现](https://nacos.io/zh-cn/docs/v2/ecology/use-nacos-with-spring-cloud.html)

### 13.3 FAQ

| 问题 | 要点 |
|------|------|
| 为什么选 Spring Cloud Gateway? | 原生响应式、生态完备，与 Spring 体系无缝；Nginx 仍可做 L4/L7 前置 |
| 新增下游服务是否需要改代码? | 使用 Nacos 动态路由即可，仅需更新配置 |
| 如何压测/扩容? | wrk/bombardier + 关注 P95；服务无状态，支持水平扩容 |
| 如何调试路由不生效? | 1. 检查日志（DEBUG 级别）<br>2. 验证断言逻辑<br>3. 确认路由顺序 |
| 如何实现 API 版本控制? | 1. 路径前缀（/v1/、/v2/）<br>2. Header 断言（X-Api-Version）<br>3. 权重路由 |

---

**更新记录**

| 版本 | 日期 | 变更说明 | 作者 |
|-----|------|---------|------|
| 1.0 | 2025-10-08 | 初始版本：完整的 API 网关文档 | docs-engineer |

---

**许可证**

Copyright © 2025 Papertrace
