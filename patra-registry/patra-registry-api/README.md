# patra-registry-api — 注册中心 API 契约

> **API 模块**,定义注册中心服务的公共契约 — 数据源和表达式元数据的 DTO、端点、Feign 客户端。

---

## 📌 目标定位

`patra-registry-api` 提供从其他微服务查询注册中心服务的**外部 API 契约**。包含:

1. **数据源 DTO**: 元数据和运营配置
2. **表达式 DTO**: 表达式字段、渲染规则、API 参数映射
3. **字典 DTO**: 字典项、类型、验证
4. **端点接口**: Spring MVC 端点定义
5. **Feign 客户端**: 开箱即用的 Feign RPC 集成客户端
6. **错误代码**: 标准化错误处理

**为什么要独立 API 模块?**
- **解耦**: 消费者仅依赖契约,而非实现
- **版本控制**: API 契约独立演进
- **类型安全**: RPC 调用的编译时验证

---

## 🗂️ 模块结构

```
patra-registry-api/
└─ src/main/java/.../api/
   ├─ rpc/
   │  ├─ dto/
   │  │  ├─ provenance/              # 数据源领域 DTO
   │  │  │  ├─ ProvenanceResp.java       # 数据源元数据
   │  │  │  ├─ ProvenanceConfigResp.java # 聚合配置(7 个维度)
   │  │  │  ├─ HttpConfigResp.java       # HTTP 配置
   │  │  │  ├─ RetryConfigResp.java      # 重试配置
   │  │  │  ├─ RateLimitConfigResp.java  # 速率限制配置
   │  │  │  ├─ PaginationConfigResp.java # 分页配置
   │  │  │  ├─ BatchingConfigResp.java   # 批处理配置
   │  │  │  └─ WindowOffsetResp.java     # 窗口偏移配置
   │  │  │
   │  │  ├─ expr/                    # 表达式领域 DTO
   │  │  │  ├─ ExprSnapshotResp.java     # 表达式快照
   │  │  │  ├─ ExprFieldResp.java        # 字段定义
   │  │  │  ├─ ExprRenderRuleResp.java   # 渲染规则
   │  │  │  ├─ ApiParamMappingResp.java  # API 参数映射
   │  │  │  └─ ExprCapabilityResp.java   # 表达式能力
   │  │  │
   │  │  └─ dict/                    # 字典领域 DTO
   │  │     ├─ DictionaryItemResp.java   # 字典项
   │  │     ├─ DictionaryTypeResp.java   # 字典类型
   │  │     ├─ DictionaryHealthResp.java # 健康状态
   │  │     ├─ DictionaryValidationResp.java
   │  │     └─ DictionaryReferenceReq.java
   │  │
   │  ├─ endpoint/                   # 端点接口
   │  │  ├─ ProvenanceEndpoint.java      # 数据源 API
   │  │  └─ ExprEndpoint.java            # 表达式 API
   │  │
   │  └─ client/                     # Feign 客户端
   │     ├─ ProvenanceClient.java        # 数据源 RPC 客户端
   │     └─ ExprClient.java              # 表达式 RPC 客户端
   │
   └─ error/                         # 错误代码
      └─ RegistryErrorCode.java         # 标准化错误代码
```

---

## 🔌 API 契约

### 1. 数据源 API

#### ProvenanceEndpoint

```java
public interface ProvenanceEndpoint {
    String BASE_PATH = "/_internal/provenances";

    @GetMapping(BASE_PATH)
    List<ProvenanceResp> listProvenances();

    @GetMapping(BASE_PATH + "/{code}")
    ProvenanceResp getProvenance(@PathVariable("code") ProvenanceCode code);

    @GetMapping(BASE_PATH + "/{code}/config")
    ProvenanceConfigResp getConfiguration(
        @PathVariable("code") ProvenanceCode code,
        @RequestParam(value = "operationType", required = false) String operationType,
        @RequestParam(value = "at", required = false) Instant at
    );
}
```

**3 个操作**:

1. **GET /_internal/provenances** - 列出所有数据源
2. **GET /_internal/provenances/{code}** - 根据代码获取单个数据源
3. **GET /_internal/provenances/{code}/config?operationType=&at=** - 获取聚合配置(支持**时态切片**)

---

#### ProvenanceConfigResp (7 个配置维度)

```java
public record ProvenanceConfigResp(
    ProvenanceResp provenance,           // Baseline metadata
    WindowOffsetResp windowOffset,       // Window and offset config
    PaginationConfigResp pagination,     // Pagination/cursor config
    HttpConfigResp http,                 // HTTP baseline config
    BatchingConfigResp batching,         // Batching and request shaping
    RetryConfigResp retry,               // Retry and backoff config
    RateLimitConfigResp rateLimit        // Rate limiting and concurrency
) {}
```

**时态切片**:
- `operationType`: 按操作类型过滤(HARVEST/UPDATE/COMPENSATION)
- `at`: 查询在此时刻生效的配置

**请求示例**:
```
GET /_internal/provenances/pubmed/config?operationType=HARVEST&at=2025-01-12T10:00:00Z
```

**响应示例**:
```json
{
  "provenance": {
    "code": "pubmed",
    "name": "PubMed",
    "baseUrl": "https://api.pubmed.gov",
    "status": "ACTIVE"
  },
  "windowOffset": {
    "maxWindowDays": 365,
    "offsetSeconds": 0
  },
  "pagination": {
    "type": "OFFSET",
    "defaultPageSize": 1000,
    "maxPageSize": 10000
  },
  "http": {
    "timeoutSeconds": 30,
    "connectTimeoutSeconds": 10,
    "headers": {
      "User-Agent": "Papertrace/1.0",
      "Accept": "application/json"
    }
  },
  "batching": {
    "batchSizeLimit": 1000,
    "batchTimeoutSeconds": 60
  },
  "retry": {
    "maxAttempts": 3,
    "backoffSeconds": 1,
    "maxBackoffSeconds": 10,
    "retryableCodes": [502, 503, 504]
  },
  "rateLimit": {
    "requestsPerSecond": 10,
    "burstSize": 20,
    "concurrentRequests": 5
  }
}
```

---

### 2. 表达式 API

#### ExprEndpoint

```java
public interface ExprEndpoint {
    String BASE_PATH = "/_internal/expr";

    @GetMapping(BASE_PATH + "/snapshot")
    ExprSnapshotResp getSnapshot(
        @RequestParam("provenanceCode") String provenanceCode,
        @RequestParam(value = "operationType", required = false) String operationType,
        @RequestParam(value = "endpointName", required = false) String endpointName,
        @RequestParam(value = "at", required = false) Instant at
    );
}
```

**1 个操作**:

**GET /_internal/expr/snapshot?provenanceCode=&operationType=&endpointName=&at=** - 获取表达式快照(支持时态切片)

**请求示例**:
```
GET /_internal/expr/snapshot?provenanceCode=pubmed&operationType=HARVEST&endpointName=search&at=2025-01-12T10:00:00Z
```

**响应示例**:
```json
{
  "provenanceCode": "pubmed",
  "operationType": "HARVEST",
  "endpointName": "search",
  "fields": [
    {
      "fieldCode": "query",
      "fieldType": "STRING",
      "required": true,
      "defaultValue": null,
      "validation": {
        "pattern": ".*",
        "minLength": 1,
        "maxLength": 500
      }
    },
    {
      "fieldCode": "pageSize",
      "fieldType": "INTEGER",
      "required": false,
      "defaultValue": 1000
    }
  ],
  "apiParamMappings": [
    {
      "apiParamName": "q",
      "exprFieldCode": "query",
      "transformRule": "URL_ENCODE"
    },
    {
      "apiParamName": "pagesize",
      "exprFieldCode": "pageSize",
      "transformRule": "TO_STRING"
    }
  ],
  "renderRules": [
    {
      "templateName": "query_url",
      "template": "{baseUrl}/search?q={query}&pagesize={pageSize}",
      "outputFormat": "URL"
    }
  ],
  "capabilities": {
    "supportsCursor": false,
    "supportsOffset": true,
    "supportsBatching": true
  }
}
```

---

### 3. Feign 客户端

#### ProvenanceClient

```java
@FeignClient(name = "patra-registry", contextId = "provenanceClient")
public interface ProvenanceClient extends ProvenanceEndpoint {
}
```

#### ExprClient

```java
@FeignClient(name = "patra-registry", contextId = "exprClient")
public interface ExprClient extends ExprEndpoint {
}
```

---

## 🚀 使用指南

### 步骤 1: 添加依赖

```xml
<dependency>
    <groupId>com.papertrace</groupId>
    <artifactId>patra-registry-api</artifactId>
    <version>${project.version}</version>
</dependency>
```

### 步骤 2: 启用 Feign 客户端

```java
@SpringBootApplication
@EnableFeignClients(clients = {ProvenanceClient.class, ExprClient.class})
public class YourApplication {
    public static void main(String[] args) {
        SpringApplication.run(YourApplication.class, args);
    }
}
```

### 步骤 3: 使用数据源客户端

```java
@Component
@RequiredArgsConstructor
public class PatraRegistryPortImpl implements PatraRegistryPort {

    private final ProvenanceClient provenanceClient;

    @Override
    public ProvenanceConfigSnapshot fetchConfig(ProvenanceCode code, OperationCode operation) {
        // 使用时态切片查询
        ProvenanceConfigResp resp = provenanceClient.getConfiguration(
            code,
            operation.getCode(),
            Instant.now()  // 获取当前生效的配置
        );

        // 转换为领域快照
        return convertToSnapshot(resp);
    }

    @Override
    public List<Provenance> listAllProvenances() {
        List<ProvenanceResp> respList = provenanceClient.listProvenances();
        return respList.stream()
            .map(this::convertToDomain)
            .toList();
    }
}
```

### 步骤 4: 使用表达式客户端

```java
@Component
@RequiredArgsConstructor
public class PlanExpressionBuilder {

    private final ExprClient exprClient;

    public PlanExpressionDescriptor build(PlanTriggerNorm norm, ProvenanceConfigSnapshot config) {
        // 查询表达式快照
        ExprSnapshotResp exprSnapshot = exprClient.getSnapshot(
            norm.provenanceCode(),
            norm.operationType(),
            "search",
            Instant.now()
        );

        // 从快照构建表达式
        Expr expr = buildExprFromSnapshot(exprSnapshot, norm);

        // 规范化
        ExprCanonicalSnapshot canonical = ExprCanonicalizer.canonicalize(expr);

        return new PlanExpressionDescriptor(
            canonical.hash(),
            canonical.canonicalJson()
        );
    }
}
```

---

## 📊 核心概念

### 时态配置切片

**问题**: 配置在任务执行期间变更会破坏正在运行的任务。

**解决方案**: 在计划创建时捕获配置快照。

```java
// 在计划创建时(2025-01-10)
ProvenanceConfigResp snapshot = provenanceClient.getConfiguration(
    ProvenanceCode.PUBMED,
    "HARVEST",
    Instant.parse("2025-01-10T00:00:00Z")
);

// 捕获 2025-01-10 生效的配置
// 即使配置在 2025-01-11 更改,计划仍使用 2025-01-10 的快照
```

**优势**:
- **不可变性**: 配置更改不会破坏计划
- **审计轨迹**: 知道使用了哪个配置
- **渐进式发布**: 使用时间切片进行 A/B 测试

### 配置维度优先级

**作用域优先级**(从高到低):
1. **TASK 级**: 最具体(操作 + 端点 + 任务特定)
2. **OPERATION 级**: 操作特定(HARVEST vs UPDATE)
3. **SOURCE 级**: 所有操作的默认值

**示例**:
- SOURCE 级: `rateLimit = 10`
- HARVEST 级: `rateLimit = 5`
- UPDATE 级: `rateLimit = 20`

```
HARVEST 操作 → 使用 5 req/s
UPDATE 操作 → 使用 20 req/s
其他操作 → 使用 10 req/s(SOURCE 默认值)
```

---

## ⚠️ 错误代码

### RegistryErrorCode

**标准化错误代码**,遵循模式: `REG-{segment}{number}`

| 错误代码 | HTTP 状态 | 描述 |
|------------|-------------|-------------|
| `REG-0400` | 400 | 错误请求 |
| `REG-0404` | 404 | 数据源/配置未找到 |
| `REG-0422` | 422 | 无法处理的实体(验证失败) |
| `REG-0500` | 500 | 内部服务器错误 |
| `REG-1001` | 404 | 数据源未找到 |
| `REG-1002` | 404 | 配置未找到 |
| `REG-1003` | 422 | 配置验证失败 |
| `REG-1004` | 409 | 配置冲突(重复) |

---

## 📋 最佳实践

### 1. 始终提供时态上下文

```java
// ✅ 好: 指定时态上下文
ProvenanceConfigResp config = provenanceClient.getConfiguration(
    code,
    operationType,
    Instant.now()  // 或 plan.getCreatedAt()
);
```

```java
// ❌ 不好: 依赖默认值(当前时间)
ProvenanceConfigResp config = provenanceClient.getConfiguration(
    code,
    operationType,
    null  // 默认为 "now",但不明确
);
```

### 2. 缓存配置快照

```java
// ✅ 好: 在计划创建时捕获快照一次
ProvenanceConfigSnapshot snapshot = fetchConfig(code, operation);
plan.setConfigSnapshotJson(toJson(snapshot));

// 之后,任务使用捕获的快照
ProvenanceConfigSnapshot config = fromJson(plan.getConfigSnapshotJson());
```

```java
// ❌ 不好: 为每个任务获取配置
for (Task task : tasks) {
    ProvenanceConfigResp config = provenanceClient.getConfiguration(...);  // 网络调用!
}
```

### 3. 优雅地处理未找到的情况

```java
// ✅ 好: 处理 404
try {
    ProvenanceConfigResp config = provenanceClient.getConfiguration(...);
} catch (FeignException.NotFound ex) {
    log.warn("Configuration not found for {} at {}", code, at);
    // 回退到默认值或优雅失败
}
```

### 4. 使用操作类型进行过滤

```java
// ✅ 好: 按操作类型过滤
ProvenanceConfigResp harvestConfig = provenanceClient.getConfiguration(
    ProvenanceCode.PUBMED,
    "HARVEST",  // 获取 HARVEST 特定配置
    Instant.now()
);

ProvenanceConfigResp updateConfig = provenanceClient.getConfiguration(
    ProvenanceCode.PUBMED,
    "UPDATE",  // 获取 UPDATE 特定配置
    Instant.now()
);
```

---

## 🔗 相关文档

- [patra-registry 服务 README](../README.md)
- [主 README](../../README.md)
- [架构指南](../../docs/ARCHITECTURE.md)
- [时态配置模式](../../docs/ARCHITECTURE.md#43-temporal-configuration)

---

**最后更新**: 2025-01-12
