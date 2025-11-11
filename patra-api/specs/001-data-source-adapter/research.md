# Phase 0: 技术调研报告

**特性分支**: `001-data-source-adapter`
**调研日期**: 2025-11-11
**调研目的**: 验证 spec.md 中提出的多数据源多类型数据适配器架构改造方案的可行性

---

## 执行摘要

本调研完成了三个关键技术问题的深度分析，验证了 spec.md 中提出的架构改造方案的可行性。**核心结论**：推荐的技术方案完全满足需求，现有代码库已具备良好的基础。

---

## 调研问题与结论

### 1. Java 泛型适配器设计模式

**问题**: 如何设计 `DataSourceAdapter<T extends CanonicalData>` 泛型接口并在运行时动态查找适配器实例？

**调研结果**:

#### 推荐方案: Spring 泛型自动注入 + HashMap 注册表

**方案A**: Spring 原生支持泛型类型作为隐式限定符
```java
// 定义泛型适配器接口
public interface DataSourceAdapter<T extends CanonicalData> {
    AdapterResult<T> fetchData(AdapterRequest request);
    boolean supports(Class<?> dataType);
}

// 具体实现
@Component
public class PubMedLiteratureAdapter
    implements DataSourceAdapter<CanonicalLiterature> {
    // Spring 自动识别泛型类型
}
```

**优势**:
- ✅ Spring 4.0+ 原生支持,无需额外配置
- ✅ 编译时类型安全
- ✅ 适用于实现数量较少的场景(2-10个)

**方案B**: 使用 HashMap 注册表实现 O(1) 查询

```java
@Component
public class AdapterRegistry {
    // Key: "provenanceCode:dataType", Value: Adapter
    private final Map<String, DataSourceAdapter<?>> adapters =
        new ConcurrentHashMap<>();

    @SuppressWarnings("unchecked")
    public <T extends CanonicalData> DataSourceAdapter<T> getAdapter(
            String provenanceCode, Class<T> dataType) {
        String key = provenanceCode + ":" + dataType.getSimpleName();
        DataSourceAdapter<?> adapter = adapters.get(key);
        if (adapter == null) {
            throw new AdapterNotFoundException(
                "未找到适配器: " + provenanceCode + " -> " + dataType.getName()
            );
        }
        return (DataSourceAdapter<T>) adapter;
    }
}
```

**优势**:
- ✅ O(1) 时间复杂度
- ✅ 支持按数据源+数据类型组合查找
- ✅ 线程安全(ConcurrentHashMap)

**决策**: 采用 **方案B (HashMap 注册表)**,因为:
1. spec.md 明确要求"AdapterRegistry支持按数据源代码和数据类型查找适配器"
2. 需要支持多种数据类型(LITERATURE、AUTHOR、CITATION、JOURNAL、FULLTEXT)
3. O(1) 查询性能满足"每秒100+并发请求"的性能需求

#### 类型擦除问题的解决方案

**问题**: 运行时无法获取泛型参数类型

**解决方案**: 使用 TypeToken 模式保存泛型信息

```java
// 适配器基类
public abstract class BaseDataSourceAdapter<T extends CanonicalData>
    implements DataSourceAdapter<T> {

    private final Class<T> dataType;

    @SuppressWarnings("unchecked")
    protected BaseDataSourceAdapter() {
        // 通过反射获取泛型参数类型
        Type superClass = getClass().getGenericSuperclass();
        this.dataType = (Class<T>) ((ParameterizedType) superClass)
            .getActualTypeArguments()[0];
    }

    public final Class<T> getDataType() {
        return dataType;
    }
}
```

**参考资源**:
- Spring Framework Documentation - Generics as Qualifiers
- Neal Gafter's Blog - Super Type Tokens
- Guava TypeToken 实现

---

### 2. 策略模式的注册和查找机制

**问题**: `StrategyRegistry` 如何管理 `DataTransformStrategy<S, T>` 策略并实现 O(1) 查找？

**调研结果**:

#### 推荐方案: HashMap + BeanPostProcessor 自动注册

```java
// 策略接口
public interface DataTransformStrategy<S, T> {
    T transform(S source);
    Class<?> getSourceType();
    Class<?> getTargetType();
}

// 注册表实现
@Component
public class StrategyRegistry {
    // Key: "SourceType->TargetType"
    private final Map<String, DataTransformStrategy<?, ?>> strategies =
        new ConcurrentHashMap<>();

    // 自动注册所有策略Bean
    @Component
    public static class StrategyRegistrar implements BeanPostProcessor {
        @Autowired
        private StrategyRegistry registry;

        @Override
        public Object postProcessBeforeInitialization(Object bean,
                String beanName) {
            if (bean instanceof DataTransformStrategy<?, ?> strategy) {
                String key = strategy.getSourceType().getSimpleName() +
                            "->" + strategy.getTargetType().getSimpleName();
                registry.register(key, strategy);
            }
            return bean;
        }
    }

    // O(1) 查询
    @SuppressWarnings("unchecked")
    public <S, T> DataTransformStrategy<S, T> getStrategy(
            Class<S> sourceType, Class<T> targetType) {
        String key = sourceType.getSimpleName() + "->" +
                    targetType.getSimpleName();
        DataTransformStrategy<?, ?> strategy = strategies.get(key);
        if (strategy == null) {
            throw new StrategyNotFoundException(
                "未找到转换策略: " + key
            );
        }
        return (DataTransformStrategy<S, T>) strategy;
    }
}
```

**性能分析**:
- **查询时间复杂度**: O(1) 平均情况
- **注册时间**: O(1)
- **内存占用**: O(n),n为策略数

**决策**: 采用 HashMap + BeanPostProcessor 方案,因为:
1. 满足 spec.md 的 O(1) 查询要求(SC-001)
2. Spring 自动发现和注册,无需手动配置
3. 支持转换策略的可插拔设计(FR-009)

**替代方案**: Spring Plugin 框架
- 适用于策略数量>100的大规模场景
- 提供优先级和条件匹配能力
- **暂不采用**: 当前预期策略数量<50,HashMap 方案足够

**参考资源**:
- spring-plugin (GitHub)
- Spring BeanPostProcessor 文档

---

### 3. 部分成功处理模式

**问题**: 批量处理时如何设计结果对象记录成功和失败详情,并根据成功率阈值决策？

**调研结果**:

#### 推荐方案: BatchTransformResult + HTTP 207 Multi-Status

```java
// 部分成功结果对象
public class BatchTransformResult<T> {
    private final List<T> successes;
    private final List<TransformError> failures;
    private final long startTime;
    private final long endTime;

    // 核心指标
    public int getTotalCount() {
        return successes.size() + failures.size();
    }

    public double getSuccessRate() {
        int total = getTotalCount();
        return total == 0 ? 0 :
            (getSuccessCount() * 100.0) / total;
    }

    // 成功率阈值判断
    public boolean meetsSuccessThreshold(double threshold) {
        return getSuccessRate() >= threshold;
    }

    // 状态判断
    public boolean isFullSuccess() {
        return failures.isEmpty();
    }

    public boolean isPartialSuccess() {
        return !failures.isEmpty() && !successes.isEmpty();
    }
}

// 错误信息快照
public class TransformError {
    private final String sourceId;      // 失败源数据ID
    private final String reason;         // 失败原因
    private final String errorMessage;   // 错误消息
    private final String stackTrace;     // 堆栈跟踪(截断至1KB)
    private final long timestamp;        // 发生时间
}
```

**批量处理实现**:

```java
@Service
public class BatchDataTransformService {

    public <S, T> BatchTransformResult<T> transformBatch(
            List<S> sources, Class<T> targetType) {

        BatchTransformResult.Builder<T> resultBuilder =
            BatchTransformResult.builder();

        DataTransformStrategy<S, T> strategy =
            strategyRegistry.getStrategy(sourceType, targetType);

        // 逐个转换,捕获异常,继续处理下一个
        for (S source : sources) {
            try {
                T result = strategy.transform(source);
                resultBuilder.addSuccess(result);
            } catch (Exception e) {
                resultBuilder.addFailure(
                    getId(source),
                    e.getClass().getSimpleName(),
                    e
                );
            }
        }

        return resultBuilder.build();
    }
}
```

**HTTP API 响应设计** (REST 控制器):

```java
@PostMapping("/transform/batch")
public ResponseEntity<BatchTransformDto> transformBatch(
        @RequestBody BatchTransformRequest request) {

    BatchTransformResult<?> result =
        transformService.transformBatch(
            request.getSources(),
            request.getTargetType()
        );

    // 根据成功情况返回不同的HTTP状态码
    HttpStatus status = result.isFullSuccess() ?
        HttpStatus.OK :             // 200: 全部成功
        result.isPartialSuccess() ?
        HttpStatus.MULTI_STATUS :   // 207: 部分成功
        HttpStatus.BAD_REQUEST;     // 400: 全部失败

    return new ResponseEntity<>(
        new BatchTransformDto(result), status
    );
}
```

**决策**: 采用 BatchTransformResult + 207 方案,因为:
1. 满足 spec.md 的部分成功机制要求(FR-004、SC-005)
2. HTTP 207 是标准的多状态响应码(WebDAV RFC 4918)
3. 结构化错误信息便于根因分析(NFR-016)

**性能考量**:
- 单个转换失败不影响其他数据
- 失败堆栈信息截断至1KB避免内存占用
- 支持并行转换(使用线程池)

**参考资源**:
- RFC 4918 - HTTP/1.1 Extensions for Web Distributed Authoring and Versioning (WebDAV)
- Spring Integration Samples - 批量处理模式

---

### 4. HTTP 错误分类机制

**问题**: 如何根据HTTP状态码和异常类型标识错误的可重试性？

**调研结果**:

#### 推荐方案: ErrorType 枚举 + 错误分类规则

**HTTP 状态码分类**:

```
标识为 RETRIABLE (临时性错误):
  ✓ 429 (限流) - 临时限流,稍后可重试
  ✓ 503 (服务不可用) - 服务临时不可用
  ✓ 504 (网关超时) - 网关超时
  ✓ 408 (请求超时) - 请求超时
  ✓ 500, 502 (视具体场景) - 服务器内部错误

标识为 NON_RETRIABLE (永久性错误):
  ✗ 4xx (除429外) - 客户端错误,重试无意义
  ✗ 401, 403 - 认证/授权错误
  ✗ 404 - 资源不存在
  ✗ 400 - 请求格式错误
```

**Java 异常分类**:

```
标识为 RETRIABLE (临时性):
  ✓ SocketTimeoutException / TimeoutException - 网络超时
  ✓ ConnectException / ResourceAccessException - 连接失败
  ✓ HttpServerErrorException (5xx) - 服务器错误
  ✓ HttpClientErrorException.TooManyRequests (429) - 限流

标识为 NON_RETRIABLE (永久性):
  ✗ HttpClientErrorException (其他4xx) - 客户端错误
  ✗ IllegalArgumentException - 参数错误
  ✗ BusinessException - 业务异常
  ✗ HttpMessageNotReadableException - 反序列化错误
```

**实现方案**:

```java
@Service
public class PubMedAdapter implements DataSourceAdapter<CanonicalLiterature> {

    public AdapterResult<CanonicalLiterature> fetchData(AdapterRequest request) {
        try {
            // 调用外部API
            List<CanonicalLiterature> data = pubMedClient.fetchArticles(request);
            return AdapterResult.success(data, nextCursor);

        } catch (HttpClientErrorException.TooManyRequests e) {
            // 解析 Retry-After 响应头
            long retryAfterSeconds = parseRetryAfter(e.getResponseHeaders());
            return AdapterResult.retriableFailure(
                "API限流,建议" + retryAfterSeconds + "秒后重试"
            ).withMetadata("retryAfterSeconds", retryAfterSeconds);

        } catch (SocketTimeoutException | TimeoutException e) {
            return AdapterResult.retriableFailure("网络超时: " + e.getMessage());

        } catch (HttpServerErrorException e) {
            return AdapterResult.retriableFailure("服务器错误: " + e.getMessage());

        } catch (HttpClientErrorException e) {
            return AdapterResult.nonRetriableFailure(
                "客户端错误(" + e.getStatusCode() + "): " + e.getMessage()
            );

        } catch (IllegalArgumentException e) {
            return AdapterResult.nonRetriableFailure("参数错误: " + e.getMessage());
        }
    }
}
```

**Retry-After 响应头解析**:

```java
private long parseRetryAfter(HttpHeaders headers) {
    String retryAfter = headers.getFirst("Retry-After");
    if (retryAfter == null) {
        return 60L; // 默认60秒
    }

    try {
        // 尝试解析为秒数
        return Long.parseLong(retryAfter);
    } catch (NumberFormatException e) {
        // 尝试解析为HTTP日期
        try {
            Instant retryTime = HttpHeaders.parseRetryAfter(retryAfter);
            long seconds = Instant.now().until(retryTime, ChronoUnit.SECONDS);
            return Math.max(1, seconds); // 至少1秒
        } catch (Exception ex) {
            return 60L;
        }
    }
}
```

**决策**: 采用错误分类标识方案,因为:
1. 适配器职责单一,只负责识别错误类型
2. 将重试决策权交给上层调度器(GenericBatchExecutor或其他)
3. 满足 spec.md 的错误分类要求(FR-005、SC-006)
4. 提供清晰的错误信息供运维分析

**关键设计原则**:
- ✅ 适配器只标识错误类型,不实施重试
- ✅ 保留 Retry-After 等服务器建议信息
- ✅ 提供详细的错误消息便于排查
- ✅ 错误分类准确率 ≥ 95%(NFR-004)

**参考资源**:
- RFC 7231 - HTTP/1.1 Semantics and Content (状态码定义)
- Spring Framework Exception Hierarchy

---

### 5. 现有代码库分析

**调研对象**: patra-ingest 微服务的现有适配器实现

**关键发现**:

#### 5.1 现有 DataSourceAdapter 接口

**位置**: `patra-spring-boot-starter-provenance/src/main/java/com/patra/starter/provenance/common/adapter/DataSourceAdapter.java`

```java
public interface DataSourceAdapter {
  String getProvenanceCode();
  AdapterResult fetchData(AdapterRequest request);
}
```

**优势**:
- ✅ 接口设计简洁,职责清晰
- ✅ 通过 `AdapterRegistry` 实现注册和发现
- ✅ 支持错误分类(RETRIABLE、NON_RETRIABLE、PARTIAL_SUCCESS)

**待改造**:
- ❌ 未支持泛型化(返回固定类型 `List<StandardLiterature>`)
- ❌ 未支持按数据类型查找适配器
- ❌ 未分离数据转换策略

#### 5.2 现有 PubMed 适配器实现

**位置**: `patra-spring-boot-starter-provenance/src/main/java/com/patra/starter/provenance/pubmed/PubmedDataSourceAdapter.java`

**核心流程**:
1. ESEARCH - 执行搜索获取PMID列表
2. 根据ID数量选择 EFETCH 或 EPOST
3. 批量获取文章详情
4. 转换为 `StandardLiterature`
5. 返回 `AdapterResult`

**优势**:
- ✅ 完善的错误处理(HTTP状态码分类)
- ✅ 错误类型标识机制
- ✅ 支持部分成功(PARTIAL_SUCCESS)
- ✅ EPOST 优化(ID数量>200时使用WebEnv)

**待改造**:
- ❌ 仅支持文献类型(LITERATURE)
- ❌ 转换逻辑硬编码在适配器内部
- ❌ 无法扩展支持作者、引用等其他类型

#### 5.3 现有批量处理逻辑

**位置**: `patra-ingest/patra-ingest-app/src/main/java/com/patra/ingest/app/usecase/execution/coordination/GenericBatchExecutor.java`

**核心职责**:
- 通过 `AdapterRegistry` 解析适配器
- 调用适配器获取数据
- 发布文献到下游(通过 `LiteraturePublisherOrchestrator`)
- 返回 `BatchResult`

**优势**:
- ✅ 清晰的事务边界(应用层)
- ✅ 完善的错误处理逻辑
- ✅ 配置合并机制(运行时>数据源>全局)

**待改造**:
- ❌ 硬编码调用 `LiteraturePublisherOrchestrator`
- ❌ 未支持动态选择发布器(按数据类型)
- ❌ 未支持成功率阈值配置(从 patra-registry 获取)

#### 5.4 现有配置管理

**位置**: `patra-spring-boot-starter-provenance/src/main/java/com/patra/starter/provenance/common/config/ProvenanceConfig.java`

**配置优先级**: 运行时快照 > 数据源覆盖 > 共享默认值

**优势**:
- ✅ 多层配置合并机制成熟
- ✅ 支持HTTP、超时、限流等全面配置
- ✅ 类型安全的 Record 结构

**待增强**:
- ➕ 添加成功率阈值配置(批量处理)
- ➕ 添加并发度配置(线程池大小)

---

## 技术决策汇总

| 技术问题 | 推荐方案 | 理由 |
|---------|---------|------|
| **泛型适配器设计** | HashMap注册表 + TypeToken | O(1)查询,类型安全 |
| **策略注册管理** | BeanPostProcessor自动注册 | Spring原生,零配置 |
| **部分成功处理** | BatchTransformResult + 207 | 标准化,结构化错误 |
| **错误分类机制** | ErrorType枚举 + 分类规则 | 职责分离,易于监控 |
| **Retry-After解析** | 保存到metadata供上层使用 | 保留服务器建议信息 |
| **线程池并发** | 固定大小线程池(从patra-registry配置) | 控制资源消耗 |

---

## 实施风险与缓解措施

| 风险 | 影响 | 概率 | 缓解措施 |
|------|------|------|----------|
| **泛型类型擦除导致运行时错误** | 高 | 中 | 使用TypeToken模式保存类型信息,充分单元测试 |
| **策略注册冲突(相同源和目标类型)** | 中 | 低 | 注册时检查重复,抛出明确异常 |
| **批量转换内存占用过大** | 高 | 中 | 分页处理(单批最多1000条),使用迭代器而非全量加载 |
| **部分成功机制增加复杂度** | 低 | 高 | Builder模式简化构建,充分文档和示例 |
| **错误分类不准确影响监控** | 中 | 中 | 基于标准规则分类,充分单元测试覆盖各种错误场景 |
| **HTTP 207支持问题(某些客户端不支持)** | 低 | 低 | 提供降级方案(200+自定义status字段) |

---

## 后续实施建议

### 第一阶段(第1周): 核心接口改造
1. **重构 `DataSourceAdapter` 接口**
   - 泛型化: `DataSourceAdapter<T extends CanonicalData>`
   - 增加 `getSupportedDataTypes()` 方法
   - 修改 `fetchData()` 返回 `AdapterResult<T>`

2. **创建 `StrategyRegistry`**
   - HashMap + BeanPostProcessor 自动注册
   - O(1) 查询实现

3. **定义 `CanonicalData` 接口体系**
   - 5种数据类型实现(LITERATURE、AUTHOR、JOURNAL、CITATION、FULLTEXT)
   - 验证规则和不变性约束

### 第二阶段(第2周): PubMed 适配器重构
1. **分离转换策略**
   - `PubMedToLiteratureStrategy`
   - `PubMedToAuthorStrategy`
   - `PubMedToCitationStrategy`

2. **重构 `PubmedDataSourceAdapter`**
   - 支持3种数据类型
   - 根据 `requestedDataType` 调用不同策略
   - 声明支持的数据类型能力

### 第三阶段(第3周): 批量处理和错误分类增强
1. **实现 `BatchTransformResult`**
   - 成功/失败列表
   - 成功率计算
   - 阈值判断

2. **修改 `GenericBatchExecutor`**
   - 支持成功率阈值配置(从patra-registry)
   - 部分成功处理逻辑
   - 线程池并行转换

3. **实现错误分类机制**
   - 在适配器中标识错误类型(RETRIABLE/NON_RETRIABLE)
   - 解析和保存 Retry-After 信息
   - 添加错误分类单元测试

---

## 参考资源

### 技术文档
- [Spring Framework - Generics as Qualifiers](https://docs.spring.io/spring-framework/reference/core/beans/annotation-config/generics-as-qualifiers.html)
- [RFC 4918 - HTTP/1.1 Extensions for WebDAV](https://tools.ietf.org/html/rfc4918#section-11.1)
- [RFC 7231 - HTTP/1.1 Semantics and Content](https://tools.ietf.org/html/rfc7231) - HTTP状态码定义

### 博客文章
- [Neal Gafter's Blog - Super Type Tokens](https://gafter.blogspot.com/2006/12/super-type-tokens.html)

### 开源项目
- [spring-plugin](https://github.com/spring-projects/spring-plugin) - 策略注册框架
- [Apache Camel](https://camel.apache.org) - 企业集成模式

### 书籍
- Joshua Bloch《Effective Java》Item 29 - 优先使用泛型
- Martin Fowler《Enterprise Integration Patterns》- 批量处理模式

---

## 结论

通过四个并行调研任务,验证了 spec.md 中提出的多数据源多类型数据适配器架构改造方案的技术可行性。推荐的技术方案(HashMap注册表、ErrorType枚举、BatchTransformResult)完全满足功能需求和性能指标,且与现有代码库良好兼容。

**关键成功因素**:
1. ✅ 充分利用 Spring 框架的泛型支持和自动装配
2. ✅ 职责分离设计:适配器负责错误分类,上层负责重试决策
3. ✅ 遵循现有架构模式(六边形架构、DDD)
4. ✅ 保持向后兼容(渐进式重构现有 PubMed 适配器)

**下一步行动**: 进入 Phase 1 架构设计阶段,生成详细的数据模型、API契约和项目结构设计。
