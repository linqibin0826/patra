# patra-common 模块详解

> 内容迁移自历史 README，保留原有深度说明，便于在新模板下查阅。

## 1. 模块定位与设计原则

patra-common 处于整个六边形 / DDD 分层体系的最底座，目标：

1. 提供 *纯 JDK* 依赖的领域建模基类（无 Spring、无基础设施耦合）
2. 提供统一、可演进的错误码 & 语义分类契约，支撑平台级 RFC7807 风格响应
3. 提供 JSON 规范化（Canonicalization）能力，用于签名、去重、缓存键、幂等与规则固化
4. 提供跨服务通用的核心业务枚举（来源、日期类型、优先级、配置作用域等）
5. 提供少量通用算法类（Hash 等），避免各模块重复实现或引起循环依赖

严格边界：

- 不引入 Spring / MyBatis / 任何框架依赖；仅允许 Jackson、Hutool（已在父 POM 管控）
- 不出现“当前具体服务”语义（如 ingest 特有 DTO）；放入此处意味着 **平台级复用价值**
- 领域层（各微服务 *-domain*）优先复用此处抽象，禁止在 domain 里重复实现同类基类

## 2. 目录结构

```
patra-common/
  └─ src/main/java/com/patra/common/
       domain/        # 聚合根与领域事件抽象
       error/         # 错误码、异常模型、语义特征
         ├─ codes/
         ├─ problem/  # 与 ProblemDetail 扩展字段 key
         └─ trait/
       json/          # JsonMapperHolder + JsonNormalizer
       enums/         # 业务跨域核心枚举
       util/          # 通用工具（HashUtils）
```

## 3. 核心组件详解

### 3.1 领域建模抽象（domain）

| 类 | 说明 | 使用要点 |
|----|------|----------|
| `AggregateRoot<ID>` | 可变聚合根基类；支持领域事件暂存、版本、ID 延迟赋值 | 聚合内部状态变更后 `addDomainEvent()`；应用层在事务内 `pullDomainEvents()` 并转发至 Outbox/MQ |
| `ReadOnlyAggregate<ID>` | 读模型 / 配置 / 字典类聚合基类 | 无事件 / 版本；CQRS 查询侧或静态配置适用 |
| `DomainEvent` | 领域事件标记接口 | 建议事件实现类不可变，并携带 `occurredAt()` |

设计决策：**聚合根不直接依赖框架**，保证纯内存重建/测试友好；事件由应用层发布，避免领域层耦合消息中间件。

### 3.2 错误体系（error）

目标：实现“统一结构 + 可演进 + 语义分层”的平台错误处理基座。

组成：

- `ErrorCodeLike`：错误码契约（`code()` / `httpStatus()`）
- `HttpStdErrors`：按服务前缀（ING / REG / ...）生成 0xxx 对齐 HTTP 的标准错误（如 `of("ING").NOT_FOUND()` → `ING-0404`）
- `ApplicationException`：应用层包装异常，携带 `ErrorCodeLike`（编排 / 用例层）
- `DomainException`：领域层异常基类（不携带 HTTP 语义）
- `ErrorTrait` + `HasErrorTraits`：异常语义标签（NOT_FOUND / CONFLICT / QUOTA_EXCEEDED ...）供解析算法做 *降级 / 映射*；扩展比写死 `instanceof` 更灵活
- `problem/ErrorKeys`：RFC7807 ProblemDetail 扩展字段 key 常量（保证前端/日志一致性）

推荐映射顺序（伪算法）：

```
if (ex instanceof ApplicationException ae) {
   use ae.getErrorCode().httpStatus();
} else if (ex implements HasErrorTraits) {
   map traits → httpStatus (NOT_FOUND→404, CONFLICT→409 ...)
} else if (ex instanceof DomainException) {
   422 / 400 （按具体策略）
} else {
   500
}
```

错误码定义建议：

- 业务自定义段：`<SERVICE>-1xxx` / `<SERVICE>-2xxx`（内部约定）
- 标准 HTTP 映射段：`<SERVICE>-04xx` / `-05xx` 使用 `HttpStdErrors`
- **禁止** 直接硬编码数字常量散落代码；集中在本服务 `xxxErrorCodes` 枚举 / 类里

### 3.3 JSON 能力（json）

#### 3.3.1 JsonMapperHolder

场景：在 **非 Spring 环境**（工具类、表达式引擎、SDK）获取一个统一配置的 Jackson `ObjectMapper`。

机制：

- Spring 环境下，`patra-spring-boot-starter-core` 会在启动后 `register()` 容器内 `ObjectMapper`
- 无 Spring 时首次访问懒加载一个 `JsonMapper`（自动发现模块）
- 避免在低层多处 `new ObjectMapper()` 造成配置漂移

使用：

```java
ObjectMapper om = JsonMapperHolder.getObjectMapper();
String json = om.writeValueAsString(obj);
```

#### 3.3.2 JsonNormalizer

用途：生成 **确定性（deterministic / canonical）** JSON 表示，服务于：

- 签名（hash material）
- 幂等键 / 去重（排序 + 去噪）
- 缓存键（结构一致 → 命中率稳定）
- 规则 / 表达式版本快照（可持久化对比）

特性摘要：

| 能力 | 描述 | 示例 |
|------|------|------|
| 键排序 | ASCII / UNICODE 排序 | `{"b":1,"a":2}` → `{"a":2,"b":1}` |
| 数组策略 | 默认去重 + 按类型+序列化值排序；白名单字段保持原序 | `sequenceFieldWhitelist` |
| 空值处理 | `removeEmpty` + `keepEmptyWhitelist` | 删除空对象 / 空数组 / 空串 |
| 类型规整 | 宽松布尔、数字、时间解析 | `"TRUE"`→`true`; 时间戳/多格式 → UTC 毫秒格式 |
| 字符串规整 | trim / 折叠空白 / 指定字段小写 | 配置化 |
| 限制 | 最大深度 / 最大字符串字节 / 禁用键 | 防御性编程 |
| 结果 | `Result`：canonicalValue + canonicalJson + hashMaterial(bytes) | 下游直接取 bytes 做 SHA-256 |

快速使用：

```java
// 1) 单次便捷调用（默认配置）
JsonNormalizer.Result r = JsonNormalizer.normalizeDefault(input);
String canonicalJson = r.getCanonicalJson();
byte[] material = r.getHashMaterial();

// 2) 复用实例 + 自定义配置
JsonNormalizer normalizer = JsonNormalizer.withConfig(
    JsonNormalizer.Config.builder()
        .coerceBoolean(JsonNormalizer.Config.CoerceBoolean.LOOSE)
        .coerceTime(true)
        .arrayDeduplicate(true)
        .sequenceFieldWhitelist(Set.of("items", "path.to.sequence[]"))
        .lowercaseFields(Set.of("source", "$.meta.channel"))
        .build()
);
JsonNormalizer.Result r2 = normalizer.normalize(input);
```

失败场景会抛出 `JsonNormalizer.JsonNormalizationException`（需在调用边界捕获并转换为业务异常或日志告警）。

### 3.4 枚举（enums）

| 枚举 | 说明 | 典型用途 |
|------|------|---------|
| `IngestDateType` | 文献抓取/元数据重要日期类别 | 检索过滤、任务增量范围计算 |
| `ProvenanceCode` | 上游数据来源统一编码（含别名解析） | Registry 配置、采集调度、统计聚合 |
| `Priority` | 调度 / 排队优先级（数值越小优先级越高） | MQ、异步任务、限流队列 |
| `RegistryConfigScope` | Registry 配置作用域（SOURCE / TASK） | 配置覆盖关系判断 |
| `SortDirection` | 排序方向 | API 列表查询 |

统一规则：

1. 若需 JSON 交互（外部 API / 存储），请加 `@JsonCreator` / `@JsonValue`（参考已有实现）
2. 解析支持宽松输入（大小写 / 分隔符），内部一律存储标准 code
3. 不放入强服务耦合（如 ingest 的某个内部状态机枚举）——这类枚举应停留在各自 `*-domain`

### 3.5 通用工具（util）

`HashUtils`：封装 SHA-256：`sha256(byte[]/String)` 与 `sha256Hex(...)`。用于：内容指纹、去重、幂等键。

## 4. 使用与扩展规范

### 4.1 新增错误码

1. 在对应微服务自建 `XXXErrorCodes`（enum / final class）实现 `ErrorCodeLike`
2. 标准 HTTP 同义错误使用 `HttpStdErrors.of("<PREFIX>")` 的工厂方法；避免重复手写 `ING-0404`
3. 模块内只暴露 `public static final` 或枚举常量，不在业务方法里内联字符串

### 4.2 新增领域基类/工具是否进入 patra-common 的判断清单

| 评估项 | 是 | 否 |
|--------|----|----|
| 是否跨 2+ 微服务可复用 | ✅ | ❌（留在本模块内部） |
| 是否仅依赖 JDK 或已存在依赖 | ✅ | ❌（引入额外框架） |
| 是否与具体业务语义强绑定 | ❌ | ✅ |
| 是否可能被表达式引擎 / SDK 使用 | ✅ | ❌ |

满足前两项 + 不违反后两项 → 可以放入；否则留在各自模块。

### 4.3 JsonNormalizer 配置推荐场景

| 场景 | 推荐配置片段 |
|------|--------------|
| 幂等请求体签名 | `removeEmpty(true)`, `arrayDeduplicate(true)`, `coerceBoolean(LOOSE)`, `coerceTime(true)` |
| 精准审计（保留格式差异） | `trimStrings(false)`, `collapseSpaces(false)`, `coerceBoolean(NONE)`, `coerceNumber(false)` |
| 规则/表达式入库标准化 | 默认 + `lowercaseFields(Set.of("fieldA"))` |

### 4.4 与 Spring 的协同

在引入 `patra-spring-boot-starter-core` 后：

1. Starter 在上下文刷新后 `JsonMapperHolder.register(objectMapper)`
2. Web 层 / Feign / MQ 序列化配置保持一致，Normalzier 获得同源行为
3. 业务层优先依赖注入；仅在静态上下文或非 Spring 代码段调用 Holder

## 5. 常见问题 (FAQ)

| 问题 | 解答 |
|------|------|
| 为什么不用直接在每个地方 new ObjectMapper? | 配置漂移 & 性能 & 无法与 Spring 统一模块化设置 |
| JsonNormalizer 与 Jackson 自带序列化差异? | 它额外做键排序/数组去重/类型规整/空值策略，实现 canonical JSON |
| 领域事件为什么不直接发布 MQ? | 保持领域层纯净 + 统一由应用层控制事务一致性与 Outbox 策略 |
| DomainException 是否需要错误码? | 不需要；它表达纯领域不变量违反。出形时由应用层映射错误码/状态 |

## 6. 版本与演进计划（Roadmap）

| 项 | 状态 | 说明 |
|----|------|------|
| 错误解析统一组件（Adapter/Web Starter） | 规划中 | 将集中放置 trait → HTTP/status 映射表 |
| JsonNormalizer 自定义 Hook | 规划中 | 提供 SPI 允许注册字段级策略（如精度裁剪） |
| 更丰富 Hash 算法 | 规划中 | MD5（兼容旧系统）、SHA-1（必要兼容）、Blake3（高性能场景） |
| Canonical JSON Benchmark | 规划中 | JMH 基准对比不同配置下性能 |

## 7. 单元测试建议

- 聚合根：测试事件收集与 invariants 断言
- JsonNormalizer：构造多格式时间 / 大数 / 空值 / 数组去重场景
- 错误码：断言 `HttpStdErrors.of("ING").NOT_FOUND().httpStatus()==404`
- ProvenanceCode：各别名解析 + 失败分支

## 8. 快速代码片段

```java
// 定义业务错误码
public enum IngestErrorCodes implements ErrorCodeLike {
  DUP_REQ("ING-1001", 409),
  INVALID_PAYLOAD("ING-1400", 400);
  private final String code; private final int status;
  IngestErrorCodes(String c, int s){this.code=c;this.status=s;}
  public String code(){return code;} public int httpStatus(){return status;}
}

// 应用层抛出
throw new ApplicationException(IngestErrorCodes.DUP_REQ, "重复提交");

// 使用标准 HTTP 错误码
var std = HttpStdErrors.of("ING").NOT_FOUND(); // 404 → ING-0404

// 领域事件发布（应用层）
List<DomainEvent> events = aggregate.pullDomainEvents();
outboxPublisher.saveAndPublish(events);

// JSON canonical + Hash
JsonNormalizer.Result r = JsonNormalizer.normalizeDefault(payload);
String sig = HashUtils.sha256Hex(r.getHashMaterial());
```

## 9. 贡献指引（Contribution Guidelines）

1. 新增/修改需附带最小单元测试（Normalzier 配置、错误码、解析逻辑等）
2. 引入第三方依赖需评估：a) 传递依赖污染风险 b) 许可证 c) 是否可放入上层 starter
3. 严格保持 **向后兼容**：公共枚举新增值可以；不要随意重命名/删除现有 code
4. README 同步：新增公共能力后更新本文档相关段落

## 10. 变更记录 (Changelog 摘要)

- 0.1.0：初始版本：领域基类 / 错误契约 / JsonNormalizer / 枚举 / HashUtils

---

如需新增能力或对设计有疑问，请在根仓库开 Issue（标签：`module:patra-common`）。

> “Keep the domain pure, make infrastructure pluggable.”
