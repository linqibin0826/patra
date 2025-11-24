# patra-spring-boot-starter-observability

## 概述

可观测性 Starter，统一集成 **Metrics（指标）**、**Tracing（追踪）**、**Logging（日志）** 三大支柱，提供生产级的、开箱即用的可观测性解决方案。

本 Starter 基于 **Micrometer Observation API**，采用**插件式架构**设计，通过实现现有 Starter 的扩展点接口（如 `ResolutionInterceptor`、`ClientHttpRequestInterceptor`、`JobExecutionListener`）来集成可观测性功能，完全解耦，符合依赖倒置原则（DIP）。

**技术选型**：
- **Micrometer Observation API**：Spring Boot 3.x 官方推荐，统一抽象
- **SkyWalking Agent**：APM 完整性、性能优秀、中文友好
- **Prometheus**：备份方案、生态丰富
- **Caffeine Cache**：状态管理，自动过期防止内存泄漏

---

## 核心功能

### 三大支柱

#### Metrics（指标）
- 集成 Micrometer MeterRegistry
- 支持 Prometheus 导出（可选）
- 支持 SkyWalking Meter（可选）
- 自动收集业务指标、错误计数、性能指标
- 指标命名规范化（`patra.{module}.{metric}`）
- 高基数标签过滤（防止时序爆炸）

#### Tracing（追踪）
- 集成 SkyWalking APM Toolkit
- 自动传播 TraceID 到下游服务
- 支持分布式追踪链路
- 日志中自动显示 TraceID

#### Logging（日志）
- 集成 Logback SkyWalking 插件
- 自动记录操作日志
- 性能监控和慢操作检测
- 支持可配置的日志级别

---

### 插件式架构

采用插件式架构，通过实现现有扩展点接口来集成可观测性：

| 扩展点接口 | 实现类 | 功能 | 所属 Starter |
|-----------|--------|------|------------|
| `ResolutionInterceptor` | `ObservationResolutionInterceptor` | 错误解析管道的可观测性 | patra-starter-core |
| `ClientHttpRequestInterceptor` | `RestClientObservationInterceptor` | REST 客户端的可观测性 | patra-starter-rest-client |
| `JobExecutionListener` | `BatchObservationJobListener` | Batch 任务的可观测性 | patra-starter-batch |

**依赖方向**：
```
observability → core（单向依赖）
core ❌→ observability（无依赖）
```

**架构优势**：
- ✅ 核心 Starter 保持纯净，无 Micrometer 依赖
- ✅ 业务服务可随时移除 `observability` 依赖，应用仍能正常启动
- ✅ 符合开闭原则（OCP）和依赖倒置原则（DIP）
- ✅ 易于测试和维护

---

### 安全特性

#### 敏感数据检测（开发中）

**当前实现**：
- 检测 Observation 标签中的敏感数据（密码、Token、API Key、身份证号、手机号等）
- 记录 **ERROR 级别**警告日志
- 支持自定义敏感数据模式

**技术限制**：
- Micrometer 的 Context KeyValues 是不可变的，ObservationFilter 无法直接修改
- 因此当前只能检测和告警，无法主动脱敏

**多层防护策略**（推荐）：
1. **开发阶段**：在添加 Observation 标签时避免包含敏感信息
2. **日志层面**：配置 Logback `TurboFilter` 过滤敏感数据
3. **APM 层面**：SkyWalking Agent 配置禁用参数收集（见下文）
4. **Prometheus 层面**：使用 Relabeling 过滤敏感标签

**配置示例**：
```yaml
patra:
  observability:
    security:
      mask-sensitive-data: true  # 启用敏感数据检测
      sensitive-patterns:        # 自定义敏感模式
        - "creditCard"
        - "ssn"
```

---

## 自动配置内容

### ObservabilityAutoConfiguration

主配置类，自动配置以下内容：

| Bean 名称 | 类型 | 描述 |
|-----------|------|------|
| `observabilityProperties` | `ObservabilityProperties` | 可观测性配置属性 |
| `observationRegistry` | `ObservationRegistry` | Observation API 核心 |
| `observedAspect` | `ObservedAspect` | @Observed 注解支持 |

### MicrometerAutoConfiguration

Micrometer 配置类，自动配置：

**ObservationFilter**（按执行顺序）：
1. `SensitiveDataObservationFilter` - 敏感数据检测（最先执行）
2. `CommonTagsObservationFilter` - 公共标签添加（最后执行）

**ObservationHandler**：
- `LoggingObservationHandler` - 日志记录
- `PerformanceObservationHandler` - 性能监控（使用 Caffeine Cache 防止内存泄漏）

**MeterFilter**（按执行顺序）：
1. `HighCardinalityMeterFilter` - 高基数标签过滤（最先执行）
2. `MetricNamingMeterFilter` - 指标命名规范
3. `CommonTagsMeterFilter` - 公共标签添加（最后执行）

### ObservationInterceptorsAutoConfiguration

拦截器配置类，根据类路径自动注册：
- `ObservationResolutionInterceptor` - 错误解析可观测性
- `RestClientObservationInterceptor` - HTTP 客户端可观测性
- `BatchObservationJobListener` - Batch 任务可观测性（使用 Caffeine Cache 防止内存泄漏）

---

## 快速开始

### 1. 添加依赖

在业务服务的 `pom.xml` 中添加：

```xml
<dependency>
    <groupId>com.patra</groupId>
    <artifactId>patra-spring-boot-starter-observability</artifactId>
</dependency>
```

### 2. 配置（可选）

在 `application.yml` 中配置：

```yaml
patra:
  observability:
    enabled: true  # 默认启用
    application-name: ${spring.application.name}
    environment: dev
    region: cn-east-1
    cluster: default

    # Metrics 配置
    metrics:
      enabled: true
      prefix: ""  # 可选的指标前缀
      common-tags:
        team: backend
        version: 1.0.0
      step: 60s  # 导出间隔

      # SkyWalking Meter 配置
      skywalking:
        enabled: true
        oap-address: skywalking-oap:11800

      # Prometheus 配置
      prometheus:
        enabled: true
        enable-exemplars: true

    # Tracing 配置
    tracing:
      enabled: true
      sampling-rate: 1.0  # 采样率（0.0-1.0）

    # Logging 配置
    logging:
      enabled: true
      include-trace-id: true

    # ObservationHandler 配置
    handlers:
      logging:
        enabled: true
        log-level: DEBUG
      performance:
        enabled: true
        slow-threshold: 3s  # 慢操作阈值

    # 安全配置
    security:
      mask-sensitive-data: true  # 启用敏感数据检测
      sensitive-patterns:        # 自定义敏感模式
        - "creditCard"
        - "ssn"
```

### 3. 启动 SkyWalking Agent

在启动参数中添加：

```bash
java -javaagent:/path/to/skywalking-agent.jar \
     -Dskywalking.agent.service_name=patra-ingest \
     -Dskywalking.collector.backend_service=localhost:11800 \
     -jar patra-ingest-boot.jar
```

### 4. 访问 Actuator 端点

- **健康检查**：`http://localhost:8080/actuator/health`
- **Prometheus 指标**：`http://localhost:8080/actuator/prometheus`
- **SkyWalking UI**：`http://localhost:8080` （SkyWalking 服务）

---

## 主要组件

### SensitiveDataObservationFilter

**功能**：检测 Observation 标签中的敏感数据并记录 ERROR 级别告警。

**检测规则**：
- **密码**：`password`、`passwd`、`pwd`
- **Token**：`token`、`access_token`、`refresh_token`
- **API Key**：`apiKey`、`api_key`、`secret`
- **身份证号**：正则匹配 15-18 位身份证
- **手机号**：正则匹配 11 位手机号
- **邮箱**：正则匹配邮箱地址
- **银行卡号**：正则匹配 16-19 位卡号
- **Bearer Token**：正则匹配 `Bearer xxx`
- **Basic Auth**：正则匹配 `Basic xxx`

**配置示例**：
```yaml
patra:
  observability:
    security:
      mask-sensitive-data: true
      sensitive-patterns:
        - "creditCard"
        - "ssn"
        - "bankAccount"
```

**日志示例**：
```
ERROR [com.patra.starter.observability.filter.SensitiveDataObservationFilter]
🚨 检测到敏感数据: observation=http.client.requests,
   lowCardinality包含敏感数据=true, highCardinality包含敏感数据=false,
   请在数据源头移除敏感标签！敏感数据可能已泄漏到日志/指标/APM
```

---

### PerformanceObservationHandler

**功能**：
- 记录 Observation 执行时间
- 检测慢操作并记录警告日志
- 使用 Caffeine Cache 自动过期防止内存泄漏

**技术细节**：
- **缓存配置**：5 分钟自动过期 + 10000 条目限制
- **Key 生成**：使用 `System.identityHashCode(context)` 确保唯一性
- **监控方法**：
  - `getActiveObservationCount()` - 获取活跃 Observation 数量
  - `getCacheStats()` - 获取缓存统计信息（命中率、驱逐数等）

**配置示例**：
```yaml
patra:
  observability:
    handlers:
      performance:
        enabled: true
        slow-threshold: 3s  # 慢操作阈值
```

**日志示例**：
```
WARN [com.patra.starter.observability.handler.PerformanceObservationHandler]
慢操作检测: http.client.requests 耗时 3500ms，超过阈值 3000ms
```

---

### RestClientObservationInterceptor

**功能**：
- 为 HTTP 请求创建 Observation
- 自动记录请求方法、URI、状态码
- 与 Micrometer Observation 集成

**Observation 标签**：
- `http.method` - HTTP 请求方法（GET、POST 等）
- `http.uri` - 请求路径（不含查询参数，避免高基数）
- `http.status_code` - HTTP 响应状态码
- `http.outcome` - 请求结果（SUCCESS、CLIENT_ERROR、SERVER_ERROR、UNKNOWN）

**URI 解析**：
- 使用 Spring 的 `UriComponentsBuilder` 进行健壮解析
- 支持标准 HTTP/HTTPS URL、相对路径、IPv6 地址、特殊字符

**使用场景**：
- 监控 REST 客户端请求的性能和成功率
- 自动集成分布式追踪（与 Sleuth/Zipkin 集成）
- 生成 HTTP 客户端指标

---

### BatchObservationJobListener

**功能**：
- 为批处理任务创建 Observation
- 自动记录任务名称、状态、执行时间
- 使用 Caffeine Cache 自动过期防止内存泄漏

**技术细节**：
- **缓存配置**：24 小时自动过期 + 1000 条目限制
- **RemovalListener**：过期时自动停止未关闭的 Observation
- **监控方法**：
  - `getActiveObservationCount()` - 获取活跃 Observation 数量
  - `getCacheStats()` - 获取缓存统计信息

**Observation 标签**：
- `job.name` - 任务名称
- `job.execution.id` - 任务执行 ID
- `job.status` - 任务最终状态（COMPLETED、FAILED、STOPPED 等）
- `job.exit.code` - 任务退出码

**使用场景**：
- 监控批处理任务的执行时间和成功率
- 自动集成分布式追踪（跨任务步骤追踪）
- 生成批处理任务指标

---

## 架构设计

### 依赖关系图

```
patra-starter-core（纯净）
├─ ErrorResolutionPipeline（核心功能）
└─ ResolutionInterceptor 接口（扩展点）✅

patra-starter-rest-client（纯净）
├─ RestClient（核心功能）
└─ ClientHttpRequestInterceptor（Spring 标准扩展点）✅

patra-starter-batch（纯净）
├─ Batch 配置（核心功能）
└─ JobExecutionListener（Spring Batch 原生扩展点）✅

patra-spring-boot-starter-observability（插件）
├─ ObservationResolutionInterceptor（实现 ResolutionInterceptor）
├─ RestClientObservationInterceptor（实现 ClientHttpRequestInterceptor）
├─ BatchObservationJobListener（实现 JobExecutionListener）
├─ SensitiveDataObservationFilter（敏感数据检测）⚠️
├─ PerformanceObservationHandler（性能监控 + Caffeine Cache）✅
└─ 所有 ObservationHandlers、MeterFilters
```

### Filter/Handler 执行顺序

**ObservationFilter**（使用 `@Order` 控制）：
1. `SensitiveDataObservationFilter` - `HIGHEST_PRECEDENCE` - 最先检测敏感数据
2. `CommonTagsObservationFilter` - `LOWEST_PRECEDENCE` - 最后添加公共标签

**MeterFilter**（使用 `@Order` 控制）：
1. `HighCardinalityMeterFilter` - `HIGHEST_PRECEDENCE` - 最先过滤高基数标签
2. `MetricNamingMeterFilter` - `HIGHEST_PRECEDENCE + 1` - 规范指标命名
3. `CommonTagsMeterFilter` - `LOWEST_PRECEDENCE` - 最后添加公共标签

---

## 安全配置

### SkyWalking Agent 安全配置

**生产环境配置**（`agent.config`）：

```properties
# 禁用 SQL 参数收集（防止敏感数据泄露）
plugin.mysql.trace_sql_parameters=false

# 禁用 HTTP Body 收集（防止敏感数据泄露）
plugin.http.collect_http_params=false
plugin.http.http_body_max_length=0

# 禁用 HTTP Headers 收集
plugin.http.include_http_headers=false
```

### Actuator 访问控制

**生产环境配置**（`application-prod.yml`）：

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,prometheus  # 仅暴露必要端点
  endpoint:
    health:
      show-details: when-authorized  # 健康详情需认证

spring:
  security:
    user:
      name: actuator
      password: ${ACTUATOR_PASSWORD}  # 环境变量注入，禁止硬编码
```

---

## 故障排查

### 常见问题

#### Q1: 可观测性功能未生效？

**检查配置**：
```yaml
patra:
  observability:
    enabled: true  # 确保启用
```

**检查依赖**：
```bash
mvn dependency:tree | grep observability
```

**检查日志**：
```
INFO [com.patra.starter.observability.autoconfigure.ObservabilityAutoConfiguration]
初始化 Patra 可观测性自动配置 [环境: dev, 应用: patra-ingest]
```

---

#### Q2: 检测到敏感数据告警？

**原因**：Observation 标签中包含了敏感信息。

**解决方案**：
1. 在代码中移除敏感标签：
   ```java
   // ❌ 错误：将密码作为标签
   observation.lowCardinalityKeyValue("password", "secret123");

   // ✅ 正确：不包含敏感信息
   observation.lowCardinalityKeyValue("auth.method", "password");
   ```

2. 配置 SkyWalking Agent 禁用参数收集（见上文）

3. 禁用敏感数据检测（不推荐）：
   ```yaml
   patra:
     observability:
       security:
         mask-sensitive-data: false
   ```

---

#### Q3: SkyWalking UI 看不到追踪数据？

**检查 SkyWalking Agent 启动参数**：
```bash
java -javaagent:/path/to/skywalking-agent.jar \
     -Dskywalking.agent.service_name=patra-xxx \
     -Dskywalking.collector.backend_service=localhost:11800 \
     -jar app.jar
```

**检查 SkyWalking 服务是否运行**：
```bash
docker-compose -f docker/docker-compose.dev.yaml ps | grep skywalking
```

**检查应用日志**：
```
INFO [org.apache.skywalking.apm.agent.core.boot.ServiceManager]
All the service starting...
```

---

#### Q4: 内存占用持续增长？

**原因**：可能是 Observation 未正常停止导致 Cache 积累。

**排查步骤**：

1. 检查活跃 Observation 数量：
   ```java
   @Autowired
   private PerformanceObservationHandler performanceHandler;

   @GetMapping("/debug/observations")
   public Map<String, Object> debugObservations() {
       return Map.of(
           "activeCount", performanceHandler.getActiveObservationCount(),
           "cacheStats", performanceHandler.getCacheStats()
       );
   }
   ```

2. 检查 Caffeine Cache 统计：
   ```
   {
     "activeCount": 10,
     "cacheStats": {
       "hitCount": 1000,
       "missCount": 50,
       "evictionCount": 5,
       "hitRate": 0.95
     }
   }
   ```

3. 如果 `activeCount` 持续增长超过 100，可能存在问题：
   - 检查是否有 Observation 未正常停止
   - 检查日志中是否有"超时清理"的警告

---

#### Q5: 高基数标签导致 Prometheus 性能问题？

**现象**：Prometheus 查询慢、内存占用高。

**原因**：指标包含高基数标签（如 userId、requestId、traceId）。

**解决方案**：

1. `HighCardinalityMeterFilter` 会自动过滤以下标签：
   - `userId`
   - `customerId`
   - `requestId`
   - `traceId`
   - `spanId`
   - `sessionId`

2. 如需添加自定义过滤，修改 `HighCardinalityMeterFilter`：
   ```java
   private static final Set<String> HIGH_CARDINALITY_KEYS = Set.of(
       "userId", "customerId", "requestId", "traceId", "spanId", "sessionId",
       "orderId"  // 添加自定义
   );
   ```

---

## 开发指南

### 添加自定义 ObservationHandler

```java
@Component
public class CustomObservationHandler implements ObservationHandler<Observation.Context> {

    @Override
    public void onStart(Observation.Context context) {
        // 观察开始时的逻辑
    }

    @Override
    public void onStop(Observation.Context context) {
        // 观察结束时的逻辑
    }

    @Override
    public void onError(Observation.Context context) {
        // 观察错误时的逻辑
    }

    @Override
    public boolean supportsContext(Observation.Context context) {
        // 支持的上下文类型
        return true;
    }
}
```

### 添加自定义 MeterFilter

```java
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 2)  // 控制执行顺序
public class CustomMeterFilter implements MeterFilter {

    @Override
    public Meter.Id map(Meter.Id id) {
        // 自定义指标命名或标签
        return id.withTag("custom", "value");
    }

    @Override
    public DistributionStatisticConfig configure(Meter.Id id, DistributionStatisticConfig config) {
        // 自定义分布统计配置（如百分位数）
        return config;
    }
}
```

### 使用 @Observed 注解

```java
@Service
public class OrderService {

    /// 自动创建 Observation，名称为 "order.create"
    @Observed(name = "order.create", contextualName = "创建订单")
    public Order createOrder(CreateOrderRequest request) {
        // 业务逻辑
        return order;
    }
}
```

---

## 测试覆盖

**单元测试**：
- ✅ `SensitiveDataObservationFilterTest` - 5 个测试用例
- ✅ `PerformanceObservationHandlerTest` - 8 个测试用例
- ✅ `ObservabilityAutoConfigurationTest` - 8 个测试用例

**集成测试**：
- ✅ `TestObservabilityApplication` - 10 个测试用例

**总计**：31 个测试用例，0 失败，覆盖率 > 80%

---

## 参考文档

### 官方文档
- [Micrometer Observation](https://micrometer.io/docs/observation)
- [SkyWalking Documentation](https://skywalking.apache.org/docs/)
- [Spring Boot Actuator](https://docs.spring.io/spring-boot/reference/actuator/)
- [Caffeine Cache](https://github.com/ben-manes/caffeine)

### 内部文档
- [可观测性设计文档](../../docs/observability/observability-starter-design.md)
- [实施计划](../../docs/observability/implementation-plan.md)

---

## 更新日志

### v0.1.0-SNAPSHOT (2025-11-24)

#### 新增功能
- ✅ 创建模块骨架
- ✅ 实现 ObservabilityAutoConfiguration
- ✅ 实现 MicrometerAutoConfiguration
- ✅ 实现 SensitiveDataObservationFilter（检测 + 告警）
- ✅ 实现 PerformanceObservationHandler（使用 Caffeine Cache）
- ✅ 实现 RestClientObservationInterceptor（健壮 URI 解析）
- ✅ 实现 BatchObservationJobListener（使用 Caffeine Cache）
- ✅ 添加配置属性验证（samplingRate 范围检查）

#### 架构改进
- ✅ 使用 Caffeine Cache 防止内存泄漏
  - `PerformanceObservationHandler`: 5 分钟自动过期
  - `BatchObservationJobListener`: 24 小时自动过期 + RemovalListener
- ✅ 使用 `@Order` 注解明确 Filter 执行顺序
  - `ObservationFilter`: 敏感数据检测 → 公共标签
  - `MeterFilter`: 高基数过滤 → 命名规范 → 公共标签
- ✅ 移除手动注册，依赖 Spring Boot 自动收集机制
- ✅ 使用 `UriComponentsBuilder` 健壮解析 URI
- ✅ 使用 `identityHashCode(context)` 生成唯一 Key

#### 已知限制
- ⚠️ 敏感数据只能检测告警，无法主动脱敏（Micrometer Context KeyValues 不可变）
- ⚠️ Duration 类型配置属性暂不支持 `@Positive` 验证

#### 后续计划
- [ ] 实现自定义 ObservationHandler 来在输出前过滤敏感数据
- [ ] 添加 Meta-Observability 监控（Cache 大小、Filter 执行次数）
- [ ] 为 Duration 类型实现自定义验证器
- [ ] 完善性能测试，提供实际的性能开销数据

---

**注意**：本模块目前处于开发阶段，敏感数据保护功能仅支持检测告警，建议配合 SkyWalking Agent 配置、Logback Filter 等多层防护措施使用。
