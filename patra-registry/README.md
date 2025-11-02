# patra-registry — SSOT 注册中心服务

> **唯一真实数据源(Single Source of Truth)**,管理数据源元数据、运营配置、表达式和字典。

---

## 📌 目标定位

`patra-registry` 作为 Papertrace 平台的**配置中枢**,提供以下能力:

1. **数据源管理**: 外部数据源目录(PubMed、EPMC、Crossref 等)
2. **运营配置**: HTTP 策略、重试策略、速率限制、分页规则
3. **表达式元数据**: API 参数映射、字段定义、渲染规则
4. **字典管理**: 系统字典用于代码和枚举
5. **时态切片**: 时间有效配置(特定时刻有效的配置快照)

所有其他服务(特别是 `patra-ingest`)通过 **Feign RPC** 查询此服务以获取配置快照。

---

## 🏗️ 模块结构

```
patra-registry/
├─ patra-registry-api/              # 外部契约(Feign 客户端、DTO)
│  └─ src/main/java/.../api/
│     ├─ rpc/client/                # Feign 客户端接口
│     │  ├─ ProvenanceClient.java   # 数据源 API 客户端
│     │  └─ ExprClient.java         # 表达式 API 客户端
│     ├─ rpc/dto/                   # 响应 DTO
│     │  ├─ provenance/             # 数据源相关 DTO
│     │  └─ expr/                   # 表达式相关 DTO
│     └─ rpc/endpoint/              # 端点契约
│
├─ patra-registry-domain/           # 纯 Java 领域模型(无框架)
│  └─ src/main/java/.../domain/
│     ├─ model/
│     │  ├─ aggregate/              # ProvenanceConfiguration(聚合根)
│     │  ├─ vo/                     # 值对象(Provenance、*Config)
│     │  │  ├─ provenance/          # 数据源值对象
│     │  │  └─ expr/                # 表达式值对象
│     │  └─ read/                   # 读模型(CQRS 查询)
│     │     ├─ provenance/          # 数据源查询
│     │     └─ expr/                # 表达式查询
│     ├─ port/                      # 仓储接口
│     │  ├─ ProvenanceConfigRepository.java
│     │  └─ ExprRepository.java
│     ├─ exception/                 # 领域异常
│     └─ support/                   # 领域支持类
│
├─ patra-registry-app/              # 应用层(编排)
│  └─ src/main/java/.../app/
│     ├─ service/                   # 应用服务
│     │  ├─ ProvenanceConfigAppService.java
│     │  └─ ExprQueryAppService.java
│     └─ converter/                 # 查询装配器(Domain → DTO)
│        ├─ ProvenanceQueryAssembler.java
│        └─ ExprQueryAssembler.java
│
├─ patra-registry-infra/            # 基础设施层(持久化)
│  └─ src/main/java/.../infra/
│     └─ persistence/
│        ├─ entity/                 # MyBatis-Plus 实体(DO)
│        │  ├─ provenance/          # RegProvenanceDO、RegProv*CfgDO
│        │  ├─ expr/                # RegProvExpr*DO
│        │  └─ dictionary/          # RegSysDict*DO
│        ├─ mapper/                 # MyBatis 映射器
│        ├─ converter/              # DO ↔ Domain 转换器
│        └─ repository/             # 仓储实现
│           ├─ ProvenanceConfigRepositoryMpImpl.java
│           └─ ExprRepositoryMpImpl.java
│
├─ patra-registry-adapter/          # 适配器层(入站/出站)
│  └─ src/main/java/.../adapter/
│     ├─ inbound/rest/feign/        # Feign 端点实现
│     │  ├─ ProvenanceClientImpl.java  # 实现 ProvenanceClient
│     │  └─ ExprClientImpl.java        # 实现 ExprClient
│     └─ config/                    # 错误映射贡献器
│
└─ patra-registry-boot/             # 可执行模块
   └─ src/main/java/.../
      └─ PatraRegistryApplication.java  # 主类
```

---

## 🔑 核心领域概念

### 1. Provenance(数据源)

**定义**: 外部数据源(例如 PubMed、EPMC、Crossref)。

**属性**:
- `code` (String): 唯一稳定标识符(例如 `"pubmed"`、`"crossref"`)
- `name` (String): 显示名称(例如 `"PubMed"`)
- `baseUrlDefault` (String): 默认 API 基础 URL
- `timezoneDefault` (String): 默认时区(IANA 格式,例如 `"UTC"`)
- `active` (boolean): 数据源是否激活
- `lifecycleStatusCode` (String): 生命周期状态的字典代码

**文件**: [`patra-registry-domain/src/main/java/.../model/vo/provenance/Provenance.java`](patra-registry-domain/src/main/java/com/patra/registry/domain/model/vo/provenance/Provenance.java)

### 2. ProvenanceConfiguration(聚合根)

**定义**: 只读聚合,将 `Provenance` 与所有运营配置组合。

**结构**:
```java
public record ProvenanceConfiguration(
    Provenance provenance,              // 核心元数据(绝不为 null)
    WindowOffsetConfig windowOffset,    // 时间窗口分段(可为 null)
    PaginationConfig pagination,        // 分页策略(可为 null)
    HttpConfig http,                    // HTTP 客户端设置(可为 null)
    BatchingConfig batching,            // 批处理规则(可为 null)
    RetryConfig retry,                  // 重试策略(可为 null)
    RateLimitConfig rateLimit           // 速率限制配置(可为 null)
) { }
```

**作用域优先级**: TASK 级配置覆盖 SOURCE 级默认值。

**文件**: [`patra-registry-domain/src/main/java/.../model/aggregate/ProvenanceConfiguration.java`](patra-registry-domain/src/main/java/com/patra/registry/domain/model/aggregate/ProvenanceConfiguration.java)

### 3. 时态配置

所有配置(除 `Provenance` 本身外)都有**有效时间范围**:

- `effective_from` (Instant): 配置生效时间
- `effective_until` (Instant): 配置失效时间

**查询模式**:
```java
// 查找特定时间有效的配置
Optional<HttpConfig> findActiveHttpConfig(
    Long provenanceId,
    String operationType,
    Instant at  // 查询时间
);
```

**优点**:
- 安全更新配置而不影响运行中的任务
- 支持 A/B 测试
- 审计轨迹

### 4. 运营配置

| 配置类型 | 用途 | 关键字段 |
|---------|------|---------|
| **WindowOffsetConfig** | 时间窗口分段 | `startOffsetDays`、`lookbackWindowDays` |
| **PaginationConfig** | 分页策略 | `pageSize`、`maxPages`、`cursorField` |
| **HttpConfig** | HTTP 客户端设置 | `baseUrl`、`connectTimeout`、`readTimeout`、`headers` |
| **BatchingConfig** | 详情获取的批处理规则 | `batchSize`、`maxConcurrentBatches` |
| **RetryConfig** | 重试策略 | `maxRetries`、`backoffMillis`、`retryableStatusCodes` |
| **RateLimitConfig** | 速率限制 | `requestsPerSecond`、`burstCapacity` |

### 5. 表达式元数据

**定义**: 动态 API 参数映射的元数据(由 `patra-expr-kernel` 使用)。

**组件**:
- **ExprCapability**: 定义操作能力(例如 `HARVEST`、`UPDATE`)
- **ApiParamMapping**: 将逻辑参数映射到 API 查询参数
- **ExprField**: 字段定义(数据类型、约束)
- **ExprRenderRule**: 将表达式渲染为 API 查询的规则

**用例示例**: 将 `dateFrom`/`dateTo` 逻辑参数映射到 PubMed 的 `mindate`/`maxdate` 查询参数。

---

## 🔌 API 契约

### 内部 RPC API(Feign)

#### ProvenanceClient

**基础路径**: `/internal/provenance`

**端点**:
```java
@FeignClient(name = "patra-registry", contextId = "provenanceClient")
public interface ProvenanceClient {

    // 列出所有数据源
    @GetMapping("/list")
    List<ProvenanceResp> listProvenances();

    // 根据代码获取单个数据源
    @GetMapping("/{code}")
    ProvenanceResp getProvenance(@PathVariable ProvenanceCode code);

    // 加载完整配置(带时态切片)
    @GetMapping("/{code}/config")
    ProvenanceConfigResp getConfiguration(
        @PathVariable ProvenanceCode code,
        @RequestParam(required = false) String operationType,
        @RequestParam(required = false) Instant at
    );
}
```

**响应 DTO**:
- `ProvenanceResp`: 基础数据源元数据
- `ProvenanceConfigResp`: 完整配置聚合(包含所有嵌套配置)

#### ExprClient

**基础路径**: `/internal/expr`

**端点**:
```java
@FeignClient(name = "patra-registry", contextId = "exprClient")
public interface ExprClient {

    // 获取数据源的表达式能力
    @GetMapping("/{provenanceCode}/capabilities")
    List<ExprCapabilityResp> getCapabilities(@PathVariable String provenanceCode);

    // 获取 API 参数映射
    @GetMapping("/{provenanceCode}/param-mappings")
    List<ApiParamMappingResp> getParamMappings(@PathVariable String provenanceCode);
}
```

---

## 🛠️ 如何扩展

### 添加新的配置类型

**示例**: 添加 `CacheConfig` 用于缓存策略。

#### 步骤 1: 定义领域值对象

```java
// patra-registry-domain/model/vo/provenance/CacheConfig.java
package com.patra.registry.domain.model.vo.provenance;

public record CacheConfig(
    Long id,
    Long provenanceId,
    String operationType,
    Integer ttlSeconds,
    Integer maxEntries,
    Instant effectiveFrom,
    Instant effectiveUntil
) { }
```

#### 步骤 2: 定义读模型

```java
// patra-registry-domain/model/read/provenance/CacheConfigQuery.java
package com.patra.registry.domain.model.read.provenance;

public record CacheConfigQuery(
    Integer ttlSeconds,
    Integer maxEntries
) { }
```

#### 步骤 3: 添加到聚合

```java
// ProvenanceConfiguration.java
public record ProvenanceConfiguration(
    Provenance provenance,
    WindowOffsetConfig windowOffset,
    PaginationConfig pagination,
    HttpConfig http,
    BatchingConfig batching,
    RetryConfig retry,
    RateLimitConfig rateLimit,
    CacheConfig cache  // 新增
) { }
```

#### 步骤 4: 添加仓储端口方法

```java
// ProvenanceConfigRepository.java
Optional<CacheConfig> findActiveCache(Long provenanceId, String operationType, Instant at);
```

#### 步骤 5: 创建 DO 和 Mapper

```java
// RegProvCacheCfgDO.java
@TableName("reg_prov_cache_cfg")
public class RegProvCacheCfgDO { ... }

// RegProvCacheCfgMapper.java
@Mapper
public interface RegProvCacheCfgMapper extends BaseMapper<RegProvCacheCfgDO> { }
```

#### 步骤 6: 实现仓储方法

```java
// ProvenanceConfigRepositoryMpImpl.java
@Override
public Optional<CacheConfig> findActiveCache(Long provenanceId, String operationType, Instant at) {
    // 使用时态过滤查询(effective_from <= at < effective_until)
    // ...
}
```

#### 步骤 7: 更新 DTO 和转换器

```java
// ProvenanceConfigResp.java (在 api 模块中)
public record ProvenanceConfigResp(
    ProvenanceResp provenance,
    WindowOffsetResp windowOffset,
    // ...
    CacheConfigResp cache  // 新增
) { }
```

---

## 🔍 示例: 配置查询流程

### 场景

`patra-ingest` 需要在 `2025-01-12T10:00:00Z` 获取 PubMed 的 HARVEST 操作配置。

### 序列

```
1. patra-ingest 调用:
   provenanceClient.getConfiguration(PUBMED, "HARVEST", Instant.parse("2025-01-12T10:00:00Z"))

   ↓

2. ProvenanceClientImpl.getConfiguration()
   - 调用 ProvenanceConfigAppService.loadConfiguration()

   ↓

3. ProvenanceConfigAppService
   - repository.findProvenanceByCode(PUBMED)
   - repository.loadConfiguration(provenanceId, "HARVEST", at)

   ↓

4. ProvenanceConfigRepositoryMpImpl.loadConfiguration()
   - 查询 provenance (reg_provenance)
   - 查询 windowOffset (reg_prov_window_offset_cfg WHERE effective_from <= at < effective_until)
   - 查询 pagination (reg_prov_pagination_cfg ...)
   - 查询 http (reg_prov_http_cfg ...)
   - 查询 batching (reg_prov_batching_cfg ...)
   - 查询 retry (reg_prov_retry_cfg ...)
   - 查询 rateLimit (reg_prov_rate_limit_cfg ...)
   - 组装 ProvenanceConfiguration 聚合

   ↓

5. ProvenanceQueryAssembler.toQuery()
   - 将领域聚合 → ProvenanceConfigQuery

   ↓

6. ProvenanceApiConverter.toResp()
   - 将查询 DTO → ProvenanceConfigResp(API 契约)

   ↓

7. 返回给 patra-ingest 作为 ProvenanceConfigResp JSON
```

---

## 🗄️ 数据库表概览

### 表

| 表名 | 用途 |
|------|------|
| `reg_provenance` | 核心数据源目录 |
| `reg_prov_window_offset_cfg` | 窗口偏移配置(时态) |
| `reg_prov_pagination_cfg` | 分页配置(时态) |
| `reg_prov_http_cfg` | HTTP 客户端配置(时态) |
| `reg_prov_batching_cfg` | 批处理配置(时态) |
| `reg_prov_retry_cfg` | 重试策略(时态) |
| `reg_prov_rate_limit_cfg` | 速率限制配置(时态) |
| `reg_prov_expr_capability` | 表达式能力 |
| `reg_prov_api_param_map` | API 参数映射 |
| `reg_expr_field_dict` | 表达式字段定义 |
| `reg_prov_expr_render_rule` | 表达式渲染规则 |
| `reg_sys_dict_type` | 字典类型 |
| `reg_sys_dict_item` | 字典项 |
| `reg_sys_dict_item_alias` | 字典项别名 |

> `reg_prov_window_offset_cfg.offset_field_key` 和 `reg_prov_window_offset_cfg.window_date_field_key` 存储统一语义键(`std_key` 匹配 `reg_expr_field_dict.field_key`),而非提供商特定的参数名。

**关键关系**:
- 所有 `reg_prov_*_cfg` 表都有 FK → `reg_provenance.id`
- 所有时态配置都有 `effective_from` 和 `effective_until` 列
- 作用域由 `operation_type` 确定(NULL = SOURCE 级默认值)

---

## 🧪 测试

### 单元测试(领域)

```bash
mvn test -pl patra-registry-domain
```

**重点**: 值对象验证、聚合不变量。

### 集成测试(仓储)

```bash
mvn verify -pl patra-registry-infra
```

**重点**: 时态查询、MyBatis 映射器、DO ↔ Domain 转换。

### API 测试(适配器)

```bash
mvn verify -pl patra-registry-adapter
```

**重点**: Feign 端点契约、错误映射。

---

## 📊 可观测性

### 日志

- **INFO**: 高级操作(例如 "Loading provenance config for PUBMED")
- **DEBUG**: 查询详情(例如 "Found 3 active configs for provenanceId=1")
- **ERROR**: 失败(例如 "Provenance not found: code=INVALID")

### 🪵 日志(Starter v1.0)

`patra-registry` 使用 Spring Boot 默认日志。分布式追踪由 SkyWalking agent 处理。
- 通过 Nacos 动态日志级别(≤60s)

最小设置(已应用):
```xml
<dependency>
  <groupId>com.papertrace</groupId>
  <!-- 日志由服务特定配置或默认值处理 -->
</dependency>
```

application.yml:
```yaml
spring:
  application.name: patra-registry

papertrace.logging.trace.enabled: true
```

适配器示例(清理 + 业务 MDC):
```java
@RestController
@Slf4j
public class ProvenanceController {
  // 日志示例已移除;使用标准日志 API
  @Autowired LogContextEnricher enricher;

  @GetMapping("/provenance/{code}")
  public ResponseEntity<?> get(@PathVariable String code) {
    enricher.enrich("operation", "GET_PROVENANCE");
    try {
      log.info("Load provenance: code={}", sanitizer.sanitize(code));
      // 委托给应用层
      return ResponseEntity.ok().build();
    } finally {
      enricher.clearEnriched();
    }
  }
}
```

动态级别(Nacos `logging-patra-registry.yml`):
```yaml
logging.level:
  root: INFO
  com.patra.registry.app: DEBUG
  com.patra.registry.infra: DEBUG
```

参考: docs/logging/operations-guide.md、specs/001-logging-starter/quickstart.md

### 指标(计划中)

- `provenance.config.query.duration` (直方图)
- `provenance.config.cache.hit_rate` (仪表)
- `provenance.config.temporal.slices_active` (仪表)

---

## 🚀 本地运行

```bash
# 启动 MySQL
docker-compose up -d mysql

# 运行迁移(如适用)
# ...

# 启动服务
cd patra-registry/patra-registry-boot
mvn spring-boot:run
```

**默认端口**: 8081

---

## 📦 表达式种子管理

所有表达式行为(字段/能力/规则/参数映射)都通过 Flyway 种子 SQL 交付。添加/更新提供商时遵循以下检查清单。

文件(示例):
- `patra-registry-infra/src/main/resources/db/migration/V1.1.1__seed_pubmed_expr_config.sql`
- `patra-registry-infra/src/main/resources/db/migration/V1.1.2__seed_epmc_expr_config.sql`
- `patra-registry-infra/src/main/resources/db/migration/V1.1.3__seed_crossref_expr_config.sql`

原则:
- 无需架构更改;种子在干净的开发数据库中可安全重写。
- 在规则中优先使用提供商无关的 std_keys;通过 `reg_prov_api_param_map` 映射到提供商参数。
- 使用一致的 `effective_from` 时间戳;保留 `effective_until` 为 NULL。

最少包含的参数映射:
- PubMed: `query→term`、`from→mindate`、`to→maxdate (TO_EXCLUSIVE_MINUS_1D)`、`datetype→datetype`、`limit→retmax`、`offset→retstart`
- EPMC: `query→query`、`limit→pageSize`
- Crossref: `query→query`、`filter→filter`、`limit→rows`、`offset→offset`

验证(手动 SQL):
```sql
-- 参数映射包含 PubMed 的查询映射
SELECT std_key, provider_param_name, transform_code
FROM   reg_prov_api_param_map
WHERE  provenance_id = (SELECT id FROM reg_provenance WHERE code='PUBMED')
  AND  std_key IN ('query','from','to','datetype');

-- PubMed 日期 PARAMS 的渲染规则
SELECT field_key, op_code, emit_type_code, params, fn_code
FROM   reg_prov_expr_render_rule
WHERE  provenance_id = (SELECT id FROM reg_provenance WHERE code='PUBMED')
  AND  field_key='entrez_date' AND op_code='RANGE';
```

严格模式准备:
- 使用 `expr.strict=true` 运行编译器测试以确保所有 fn_code/transform_code 存在。
- 保持 MULTI 重复禁用(`expr.multi.repeat-enabled=false`),除非端到端验证了重复序列化。

更多详情: `docs/expr/07-migration-plan.md` 和 `docs/expr/12-provider-checklist.md`。

---

## 🔗 相关文档

- [主 README](../README.md)
- [架构指南](../docs/ARCHITECTURE.md)
- [开发指南](../docs/DEV-GUIDE.md)
- [patra-ingest README](../patra-ingest/README.md)

---

**最后更新**: 2025-01-12
