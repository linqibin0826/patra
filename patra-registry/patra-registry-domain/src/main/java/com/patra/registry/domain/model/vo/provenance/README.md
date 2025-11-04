# 数据源配置值对象

本包包含表示数据源元数据和运营配置的**不可变值对象**。

---

## 包内容

### 核心实体

**Provenance.java** — 根数据源实体
- 表示外部数据源目录条目
- 被所有 `reg_prov_*` 配置表引用

### 运营配置(时态)

以下所有配置都支持**时间有效切片**(`effective_from`, `effective_until`):

| 配置 | 用途 | 关键字段 |
|--------|---------|------------|
| **WindowOffsetConfig** | 时间窗口分段 | `startOffsetDays`, `lookbackWindowDays` |
| **PaginationConfig** | 分页策略 | `pageSize`, `maxPages`, `cursorField` |
| **HttpConfig** | HTTP 客户端设置 | `baseUrl`, `connectTimeout`, `readTimeout`, `headers` |
| **BatchingConfig** | 批处理规则 | `batchSize`, `maxConcurrentBatches` |
| **RetryConfig** | 重试策略 | `maxRetries`, `backoffMillis`, `retryableStatusCodes` |
| **RateLimitConfig** | 速率限制 | `requestsPerSecond`, `burstCapacity` |

---

## 时态配置模式

### 概念

**时态配置** = 配置具有时间有效范围,查询检索在特定时刻生效的配置。

**为什么?**
- 安全更新配置而不影响正在运行的任务
- 配置更改的审计轨迹
- 支持渐进式发布 / A/B 测试

### 模式架构

所有 `reg_prov_*_cfg` 表包含:
```sql
effective_from   DATETIME NOT NULL,
effective_until  DATETIME NULL,
INDEX idx_temporal (provenance_id, operation_type, effective_from, effective_until)
```

### 查询模式

```java
Optional<HttpConfig> findActiveHttpConfig(
    Long provenanceId,
    String operationType,  // "HARVEST", "UPDATE", null (=ALL)
    Instant at             // 查询时刻
);

// SQL:
// WHERE provenance_id = ?
//   AND (operation_type = ? OR operation_type IS NULL)
//   AND effective_from <= ?
//   AND (effective_until IS NULL OR effective_until > ?)
// ORDER BY effective_from DESC
// LIMIT 1
```

**结果**: 在时刻 `at` 有效的最新配置。

---

## 值对象详情

### 1. Provenance

**用途**: 核心数据源目录条目(外部数据源)。

**属性**:
```java
public record Provenance(
    Long id,                     // PK
    String code,                 // 唯一稳定代码(例如 "pubmed")
    String name,                 // 显示名称(例如 "PubMed")
    String baseUrlDefault,       // 默认 API 基础 URL
    String timezoneDefault,      // 默认时区(IANA,例如 "UTC")
    String docsUrl,              // 官方文档 URL
    boolean active,              // 数据源是否激活?
    String lifecycleStatusCode   // 字典代码(lifecycle_status)
) { }
```

**验证**:
- `code`, `name`, `timezoneDefault`, `lifecycleStatusCode`: 非空
- `id`: 正数

**文件**: [`Provenance.java`](Provenance.java:1)

---

### 2. WindowOffsetConfig

**用途**: 定义采集的时间窗口分段。

**属性**:
```java
public record WindowOffsetConfig(
    Long id,
    Long provenanceId,
    String operationType,        // "HARVEST", "UPDATE", null (=SOURCE 级默认值)
    Integer startOffsetDays,     // 从今天往回多少天开始
    Integer lookbackWindowDays,  // 采集窗口大小
    Instant effectiveFrom,
    Instant effectiveUntil
) { }
```

**示例**:
```
startOffsetDays = 7        → 从 7 天前开始
lookbackWindowDays = 3     → 采集 3 天的数据
窗口 = [今天-7天, 今天-4天)
```

**作用域优先级**: TASK 级(operationType != null)覆盖 SOURCE 级(operationType = null)。

**文件**: [`WindowOffsetConfig.java`](WindowOffsetConfig.java)

---

### 3. PaginationConfig

**用途**: 分页策略(基于偏移、基于游标或基于页面)。

**属性**:
```java
public record PaginationConfig(
    Long id,
    Long provenanceId,
    String operationType,
    String paginationType,       // "OFFSET", "CURSOR", "PAGE"
    Integer pageSize,            // 每页记录数
    Integer maxPages,            // 最大获取页数(安全限制)
    String cursorField,          // 游标字段名(CURSOR 类型使用)
    Instant effectiveFrom,
    Instant effectiveUntil
) { }
```

**分页类型**:
- **OFFSET**: `?offset=0&limit=100`, `?offset=100&limit=100`, ...
- **CURSOR**: `?cursor=abc123`, `?cursor=xyz789`, ...
- **PAGE**: `?page=1`, `?page=2`, ...

**文件**: [`PaginationConfig.java`](PaginationConfig.java)

---

### 4. HttpConfig

**用途**: HTTP 客户端设置(超时、请求头、基础 URL 覆盖)。

**属性**:
```java
public record HttpConfig(
    Long id,
    Long provenanceId,
    String operationType,
    String baseUrl,              // 覆盖 provenance.baseUrlDefault
    Integer connectTimeoutMs,    // 连接超时
    Integer readTimeoutMs,       // 读取超时
    String headersJson,          // 自定义请求头(JSON 映射)
    Instant effectiveFrom,
    Instant effectiveUntil
) { }
```

**请求头格式**(JSON):
```json
{
  "User-Agent": "Patra/1.0",
  "Accept": "application/json",
  "X-API-Key": "${env.PUBMED_API_KEY}"
}
```

**文件**: [`HttpConfig.java`](HttpConfig.java)

---

### 5. BatchingConfig

**用途**: 详情获取的批处理规则(例如,将 1000 个 ID 分批为 100 个)。

**属性**:
```java
public record BatchingConfig(
    Long id,
    Long provenanceId,
    String operationType,
    Integer batchSize,           // 每批项数
    Integer maxConcurrentBatches,  // 并行批次执行限制
    Instant effectiveFrom,
    Instant effectiveUntil
) { }
```

**用例**:
- 列表 API 返回 ID: `[id1, id2, ..., id10000]`
- 详情 API 获取完整记录: 分批为 `[id1..id100]`, `[id101..id200]`, ...
- 控制并发以遵守速率限制

**文件**: [`BatchingConfig.java`](BatchingConfig.java)

---

### 6. RetryConfig

**用途**: 重试策略(最大重试次数、退避、可重试错误)。

**属性**:
```java
public record RetryConfig(
    Long id,
    Long provenanceId,
    String operationType,
    Integer maxRetries,          // 最大重试次数
    Long backoffMillis,          // 固定退避延迟(毫秒)
    String retryableStatusCodes, // 逗号分隔的 HTTP 代码(例如 "429,502,503")
    Instant effectiveFrom,
    Instant effectiveUntil
) { }
```

**退避策略**: 固定延迟(未来: 指数退避)。

**文件**: [`RetryConfig.java`](RetryConfig.java)

---

### 7. RateLimitConfig

**用途**: 速率限制(每秒请求数、突发容量)。

**属性**:
```java
public record RateLimitConfig(
    Long id,
    Long provenanceId,
    String operationType,
    Double requestsPerSecond,    // 持续速率
    Integer burstCapacity,       // 最大突发大小
    Instant effectiveFrom,
    Instant effectiveUntil
) { }
```

**算法**: 令牌桶(未来实现)。

**文件**: [`RateLimitConfig.java`](RateLimitConfig.java)

---

## 作用域优先级

### 概念

配置可以存在于两个级别:
1. **SOURCE 级**(`operation_type = NULL`): 适用于所有操作
2. **TASK 级**(`operation_type = 'HARVEST'`): 仅适用于特定操作

**优先级**: TASK 级**覆盖** SOURCE 级。

### 示例

**场景**: PubMed 有两个重试配置:

| ID | provenance_id | operation_type | max_retries | effective_from |
|----|---------------|----------------|-------------|----------------|
| 1  | 1 (PubMed)    | NULL           | 3           | 2025-01-01     |
| 2  | 1 (PubMed)    | HARVEST        | 5           | 2025-01-10     |

**查询 1**: `findActiveRetry(provenanceId=1, operationType=UPDATE, at=2025-01-15)`
- 结果: 配置 #1 (SOURCE 级, max_retries=3)

**查询 2**: `findActiveRetry(provenanceId=1, operationType=HARVEST, at=2025-01-15)`
- 结果: 配置 #2 (TASK 级, max_retries=5) — **覆盖** SOURCE 级

---

## 设计模式

### 1. 使用 Record 实现不可变性

**所有 VO 都是 `record` 类型** → 默认不可变。

```java
public record HttpConfig(...) { }

// 无 setter — 通过构造新实例来"更新"
HttpConfig updated = new HttpConfig(
    config.id(),
    config.provenanceId(),
    config.operationType(),
    "https://new-base-url.com",  // 已更改
    config.connectTimeoutMs(),
    // ...
);
```

### 2. 规范构造器验证

**在紧凑构造器中验证**:
```java
public record Provenance(String code, String name, ...) {
    public Provenance {
        Objects.requireNonNull(code, "code required");
        if (code.isBlank()) {
            throw new IllegalArgumentException("code cannot be blank");
        }
    }
}
```

### 3. 可空的可选字段

**对可选字段使用 `null`**(不使用 `Optional<T>` — record 更偏好 null)。

```java
public record WindowOffsetConfig(
    Long id,
    Long provenanceId,
    String operationType,        // 可空(null = SOURCE 级)
    Integer startOffsetDays,     // 可空(使用数据源默认值)
    // ...
) { }
```

---

## 测试指南

### 单元测试

```java
@Test
void testProvenanceValidation() {
    // Given
    String blankCode = "   ";

    // When/Then
    assertThrows(IllegalArgumentException.class, () ->
        new Provenance(1L, blankCode, "name", null, "UTC", null, true, "ACTIVE")
    );
}
```

### 集成测试(仓储)

```java
@Test
void testTemporalQuery() {
    // Given: 同一数据源的两个配置
    HttpConfig config1 = new HttpConfig(..., effectiveFrom=Jan1, effectiveUntil=Jan10);
    HttpConfig config2 = new HttpConfig(..., effectiveFrom=Jan10, effectiveUntil=null);

    repository.save(config1);
    repository.save(config2);

    // When: 在 Jan5 查询
    Optional<HttpConfig> result = repository.findActiveHttpConfig(
        provenanceId, operationType, Instant.parse("2025-01-05T00:00:00Z")
    );

    // Then: 应返回 config1
    assertTrue(result.isPresent());
    assertEquals(config1.id(), result.get().id());
}
```

---

## 常见陷阱

### ❌ 不要: 在没有时态过滤的情况下查询

```java
// 不好: 返回所有配置(忽略时间有效性)
List<HttpConfig> findByProvenanceId(Long provenanceId);
```

### ✅ 应该: 始终使用 `at` 参数查询

```java
// 好: 返回在特定时刻有效的配置
Optional<HttpConfig> findActiveHttpConfig(Long provenanceId, String operationType, Instant at);
```

### ❌ 不要: 修改 record 字段(不可能,但不要尝试变通方法)

```java
// 不好: Record 是不可变的
config.setBaseUrl("new-url");  // ❌ 编译错误
```

### ✅ 应该: 创建新实例

```java
// 好: 构造新 record
HttpConfig updated = new HttpConfig(
    config.id(),
    config.provenanceId(),
    // ... 复制字段,仅更改需要的
);
```

---

**另见**:
- [patra-registry README](../../../../../README.md)
- [架构指南](../../../../../../../../docs/ARCHITECTURE.md)
- [ProvenanceConfigRepository](../../port/ProvenanceConfigRepository.java) — 查询的端口接口
