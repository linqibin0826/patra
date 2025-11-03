# patra-registry-api

## 概述

`patra-registry-api` 是 patra-registry 服务的**外部契约模块**,定义了供其他微服务调用的 Feign 客户端接口、数据传输对象(DTO)和错误码。本模块遵循"契约优先"设计原则,确保 API 接口的稳定性和向后兼容性。

在六边形架构中,本模块作为独立的契约层,由 `patra-registry-adapter` 模块实现,被下游微服务(如 `patra-ingest`)作为 Feign 客户端依赖引入。

## 核心职责

- **接口契约定义**: 声明内部 RPC 端点接口(`ProvenanceEndpoint`、`ExprEndpoint`)
- **DTO 定义**: 提供请求和响应数据传输对象,隔离内部领域模型与外部表示
- **Feign 客户端**: 为消费者提供开箱即用的 Feign RPC 客户端(`ProvenanceClient`、`ExprClient`)
- **错误码管理**: 定义统一的错误代码和异常响应结构
- **验证注解**: 使用 Jakarta Validation 注解标注参数约束

## 模块结构

```
patra-registry-api/
└── src/main/java/com/patra/registry/api/
    ├── endpoint/                    # 端点接口契约
    │   ├── ProvenanceEndpoint.java  # 数据源 API
    │   └── ExprEndpoint.java        # 表达式 API
    ├── client/                      # Feign 客户端
    │   ├── ProvenanceClient.java    # 数据源 RPC 客户端
    │   └── ExprClient.java          # 表达式 RPC 客户端
    ├── dto/                         # 数据传输对象
    │   ├── provenance/              # 数据源相关 DTO
    │   │   ├── ProvenanceResp.java
    │   │   ├── ProvenanceConfigResp.java
    │   │   ├── HttpConfigResp.java
    │   │   ├── RetryConfigResp.java
    │   │   ├── RateLimitConfigResp.java
    │   │   ├── PaginationConfigResp.java
    │   │   ├── BatchingConfigResp.java
    │   │   └── WindowOffsetResp.java
    │   ├── expr/                    # 表达式相关 DTO
    │   │   ├── ExprSnapshotResp.java
    │   │   ├── ExprFieldResp.java
    │   │   ├── ExprRenderRuleResp.java
    │   │   ├── ApiParamMappingResp.java
    │   │   └── ExprCapabilityResp.java
    │   └── dict/                    # 字典相关 DTO
    │       ├── DictionaryItemResp.java
    │       ├── DictionaryTypeResp.java
    │       └── DictionaryReferenceReq.java
    └── error/                       # 错误代码
        └── RegistryErrorCode.java
```

## 主要组件

### ProvenanceEndpoint

数据源 API 端点契约,定义三个核心操作:

**端点**:
- `GET /_internal/provenances` - 列出所有数据源
- `GET /_internal/provenances/{code}` - 根据代码获取单个数据源
- `GET /_internal/provenances/{code}/config` - 加载完整配置聚合(支持时态切片)

**时态切片参数**:
- `operationType`: 按操作类型过滤(HARVEST/UPDATE 等),可选
- `at`: 查询在指定时刻生效的配置,可选,默认为当前时间

### ExprEndpoint

表达式 API 端点契约,提供表达式元数据查询:

**端点**:
- `GET /_internal/expr/snapshot` - 获取表达式快照(字段定义、渲染规则、参数映射、能力)

**参数**:
- `provenanceCode`: 数据源代码
- `operationType`: 操作类型,可选
- `endpointName`: 端点名称,可选
- `at`: 时态切片时间点,可选

### ProvenanceClient / ExprClient

Feign 客户端接口,扩展对应的端点接口,通过 `@FeignClient` 注解声明服务名称和上下文 ID。本模块已提供开箱即用的客户端实现,下游服务可直接注入使用:

```java
@FeignClient(name = "patra-registry", contextId = "provenanceClient")
public interface ProvenanceClient extends ProvenanceEndpoint {}

@FeignClient(name = "patra-registry", contextId = "exprClient")
public interface ExprClient extends ExprEndpoint {}
```

**使用示例**:
```java
@Autowired
private ProvenanceClient provenanceClient;

ProvenanceConfigResp config = provenanceClient.getConfiguration(
    ProvenanceCode.PUBMED,
    "HARVEST",
    Instant.now()
);
```

### ProvenanceConfigResp

配置聚合响应 DTO,包含 7 个配置维度:

```java
public record ProvenanceConfigResp(
    ProvenanceResp provenance,           // 基础元数据
    WindowOffsetResp windowOffset,       // 时间窗口偏移配置
    PaginationConfigResp pagination,     // 分页策略
    HttpConfigResp http,                 // HTTP 客户端配置
    BatchingConfigResp batching,         // 批处理配置
    RetryConfigResp retry,               // 重试策略
    RateLimitConfigResp rateLimit        // 速率限制配置
) {}
```

## 依赖关系

**上游依赖**:
- `patra-common-core`: 共享枚举和工具类
- `jakarta.validation-api`: DTO 验证注解
- `spring-web`: `@RequestMapping` 等注解(provided)
- `spring-cloud-openfeign-core`: `@FeignClient` 注解(provided)

**下游消费者**:
- `patra-registry-adapter`: 实现端点接口
- `patra-ingest`: 通过 Feign 客户端调用 registry 服务
- 其他微服务: 引入本模块以访问 registry 服务

## 使用示例

### 添加依赖

```xml
<dependency>
    <groupId>com.papertrace</groupId>
    <artifactId>patra-registry-api</artifactId>
    <version>${project.version}</version>
</dependency>
```

### 启用 Feign 客户端

```java
@SpringBootApplication
@EnableFeignClients(clients = {ProvenanceClient.class, ExprClient.class})
public class PatraIngestApplication {
    public static void main(String[] args) {
        SpringApplication.run(PatraIngestApplication.class, args);
    }
}
```

### 查询数据源配置

```java
@Component
@RequiredArgsConstructor
public class ProvenanceConfigService {

    private final ProvenanceClient provenanceClient;

    public ProvenanceConfigResp fetchConfig(ProvenanceCode code, String operationType, Instant at) {
        // 使用时态切片查询特定时刻的有效配置
        return provenanceClient.getConfiguration(code, operationType, at);
    }

    public List<ProvenanceResp> listAll() {
        return provenanceClient.listProvenances();
    }
}
```

### 查询表达式快照

```java
@Component
@RequiredArgsConstructor
public class ExprSnapshotService {

    private final ExprClient exprClient;

    public ExprSnapshotResp fetchSnapshot(String provenanceCode, String operationType, Instant at) {
        return exprClient.getSnapshot(provenanceCode, operationType, null, at);
    }
}
```

## 核心概念

### 时态配置切片

所有配置都具有时间有效性范围(`effectiveFrom` 和 `effectiveUntil`)。通过 `at` 参数查询特定时刻的有效配置,确保:
- **配置不可变性**: 配置更改不会影响正在运行的任务
- **审计追溯**: 明确知道使用了哪个版本的配置
- **渐进式发布**: 支持基于时间的 A/B 测试

### 配置作用域优先级

配置按作用域分为三级(优先级从高到低):
1. **TASK 级**: 任务特定配置
2. **OPERATION 级**: 操作类型特定配置(HARVEST、UPDATE)
3. **SOURCE 级**: 数据源默认配置

查询时,高优先级配置覆盖低优先级配置。

## 设计原则

### 1. 契约稳定性

- API 接口变更必须遵循语义化版本控制
- 避免破坏性变更,通过扩展而非修改来演进接口
- DTO 字段添加必须保持向后兼容

### 2. 纯契约模块

- 仅包含接口定义、DTO 和注解,不包含业务逻辑
- 依赖最小化,避免引入重量级框架
- `spring-web` 和 `spring-cloud-openfeign-core` 使用 `provided` 作用域

### 3. DTO 设计

- 使用不可变对象(`record` 或 `@Value`)
- 包含必要的验证注解(`@NotNull`、`@Valid` 等)
- 字段命名清晰,避免缩写

## 相关文档

- [patra-registry 顶层文档](../README.md)
- [patra-registry-adapter 模块](../patra-registry-adapter/README.md) - API 接口的实现方
- [patra-ingest 模块](../../patra-ingest/README.md) - API 接口的主要消费方

---

**最后更新**: 2025-01-12
