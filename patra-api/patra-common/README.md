# patra-common

`patra-common` 是 Papertrace 平台所有微服务与基础组件共享的 **轻量级通用基础层**，聚焦“跨服务一致的领域基类、错误码抽象、JSON/哈希/归一化工具与规范化常量”。它严格保持 **零 Spring 框架依赖**，确保可在 domain 层、纯工具上下文以及早期引导阶段安全使用。

---
## ✨ 设计目标
| 目标 | 说明 |
| ---- | ---- |
| 领域纯度 | 仅依赖 JDK 与少量三方（Jackson / Hutool），domain 可直接复用；不反向依赖任何 starter。|
| 统一错误语义 | 为上层错误解析引擎提供稳定的错误码接口(`ErrorCodeLike`)与 HTTP 标准码工厂(`HttpStdErrors`)。|
| 事件驱动友好 | 提供聚合根/领域事件基类，支持 Outbox 拉取发布模式。|
| 确定性 JSON | 借助 `JsonNormalizer` 生成 canonical JSON（签名、去重、缓存键一致）。|
| 可回放/可幂等支撑 | 规范化 Hash / JSON / Key 组合，降低重复入库或比对歧义。|

---
## 📦 模块结构
```
src/main/java/com/patra/common/
  domain/          # 聚合根 / 只读聚合 / 领域事件基类
  enums/           # 跨服务复用的语义枚举（优先通用、避免业务强语义）
  error/           # 错误码接口、HTTP 标准错误码工厂、语义 Trait 接口
    codes/         # ErrorCodeLike / HttpStdErrors
    problem/       # ProblemDetail 扩展字段键（CODE / PATH / TRACE_ID ...）
    trait/         # ErrorTrait 与 HasErrorTraits（解析引擎语义输入）
  json/            # 全局 ObjectMapper 持有器 + JSON 规范化工具
  util/            # Hash 工具等纯工具集合
```

---
## 🔑 核心组成与用法
### 1. 领域基类
| 类 | 作用 | 典型用法 |
| --- | --- | --- |
| `AggregateRoot<ID>` | 写侧聚合根基类：ID/版本/事件暂存 + 不变量钩子 | 领域行为内调用 `addDomainEvent()` → 应用层 `pullDomainEvents()` 发布 |
| `ReadOnlyAggregate<ID>` | 读侧（查询/CQRS 投影）复用：无事件/版本负担 | 配置、字典、静态快照场景 |
| `DomainEvent` | 领域事件接口（`occurredAt()`） | 聚合内部构造不可变事件对象 |

示例：
```java
public final class PlanAggregate extends AggregateRoot<String> {
  public void schedule(...) {
     // 状态变更...
     addDomainEvent(new PlanScheduledEvent(nowIfNull(null)));
  }
}
```

### 2. 错误码与语义
| 组件 | 说明 |
| ---- | ---- |
| `ErrorCodeLike` | 平台错误码统一抽象：`code()` + `httpStatus()`；业务自定义枚举实现它。|
| `HttpStdErrors.of(prefix)` | 按上下文前缀（如 `ING`/`REG`）生成 0xxx HTTP 对齐段（NOT_FOUND→`ING-0404`）。|
| `ErrorTrait` + `HasErrorTraits` | 通过语义特征（NOT_FOUND / CONFLICT / RULE_VIOLATION...）辅助解析算法。|
| `ApplicationException` | 应用/编排层包装异常：携带结构化 `ErrorCodeLike`。|
| `ErrorKeys` | RFC7807 ProblemDetail 标准扩展字段 key（code / traceId / path / timestamp / errors）。|

使用建议：
```java
enum IngestErrorCode implements ErrorCodeLike {
  ING_PLAN_VALIDATE("ING-1403", 400);
  private final String code; private final int status;
  IngestErrorCode(String c, int s){ this.code=c; this.status=s; }
  public String code(){ return code; }
  public int httpStatus(){ return status; }
}

throw new ApplicationException(IngestErrorCode.ING_PLAN_VALIDATE, "窗口参数不合法");
```

使用标准 HTTP 段：
```java
HttpStdErrors.Group http = HttpStdErrors.of("ING");
throw new ApplicationException(http.NOT_FOUND(), "配置不存在");
```

### 3. JSON 能力
| 组件 | 说明 |
| ---- | ---- |
| `JsonMapperHolder` | 非 Spring 场景共享 `ObjectMapper`；Spring 启动后 Starter 会自动 `register()`。|
| `JsonNormalizer` | 将任意 POJO/JSON 输入规范化为确定性结构 & 文本：键排序、数组去重/排序、时间/数字/布尔宽松规整、空值剔除、大小写/空白规范化、深度/长度边界。|

快速示例：
```java
var result = JsonNormalizer.normalizeDefault(rawInput);
String canonical = result.getCanonicalJson();
byte[] material = result.getHashMaterial(); // 用于哈希签名/去重
```
自定义：
```java
JsonNormalizer normalizer = JsonNormalizer.withConfig(
   JsonNormalizer.Config.builder()
     .coerceTime(true)
     .coerceNumber(true)
     .arrayDeduplicate(true)
     .build());
var r = normalizer.normalize(raw);
```

### 4. Hash 工具
`HashUtils.sha256Hex()` 提供稳定十六进制输出；内部保证空/ null 安全。用于：规范化 JSON 结果签名、配置快照指纹、Outbox 去重键生成。

> 注：注册表相关的 Key 归一化与占位符常量已迁移至 `patra-registry-domain` 模块的 `com.patra.registry.domain.support` 包中，请在那里维护与扩展。

---
## 🧪 测试与集成建议
| 关注点 | 建议 |
| ------ | ---- |
| 聚合行为 | 使用内存实例断言事件拉取与不变量校验；避免依赖 Spring。|
| 错误码枚举 | 断言 `httpStatus()` 与约定一致；禁止 0xxx 在业务枚举中出现。|
| JsonNormalizer | 针对：时间多格式、数组去重、空值剔除、布尔/数字宽松转换编写参数化测试。|
| Registry 归一化（已迁移） | 对应能力已迁移至 `patra-registry-domain`，请在该模块编写大小写/空值/取反标记的单测覆盖。 |
| Hash | 断言同逻辑不同输入排列产生相同 canonical（键顺序差异）。|

---
## ⚠️ 常见误用与反例
| 场景 | 反例 | 正确方式 |
| ---- | ---- | -------- |
| 0xxx 维护 | 在业务枚举写 `ING-0404` | 使用 `HttpStdErrors.of("ING").NOT_FOUND()` |
| JSON 序列化 | 每次 `new ObjectMapper()` | 复用 Spring 注入或 `JsonMapperHolder.getObjectMapper()` |
| 领域事件发布 | 聚合外直接构造列表 | 聚合行为内 `addDomainEvent()` → 应用层统一 `pullDomainEvents()` |
| 归一化需求 | 手写 JSON 排序/去重 | 使用 `JsonNormalizer` |
| Key 组合（注册表场景） | 直接字符串拼接未统一大小写 | 使用 `patra-registry-domain` 中的 `RegistryKeyNormalizer.*` |

---
## 🔄 演进方向（规划）
| 项 | 说明 |
| --- | --- |
| ErrorTrait 扩展 | 补充 RETRYABLE / RATE_LIMIT 等语义 | 
| JsonNormalizer 插件 | 针对大字段（Blob/二进制）可插拔处理 | 
| 领域事件契约化 | 标准事件基接口扩展 traceId / eventId | 
| 多语言支持 | 错误码 → 国际化 title/detail 预留字段 | 

---
## 📥 依赖引入
`pom.xml`（由父 POM 管理版本，无需额外指定）：
```xml
<dependency>
  <groupId>com.papertrace</groupId>
  <artifactId>patra-common</artifactId>
</dependency>
```

---
## 📝 版本策略
- 与父 POM 同步版本（语义化预备阶段：0.x 迭代频繁）。
- BREAKING 变更：需在根 README & 变更日志（规划中）标记。

---
## 🤝 贡献准则（针对本模块）
| 原则 | 说明 |
| ---- | ---- |
| 保持“最小可依赖” | 不引入新的重量级库（日志、Spring、数据库驱动等）。|
| 领域友好 | 所有新增类型默认可在 domain 层使用；如不适合需加 Javadoc 警示。|
| 语义清晰 | 常量/枚举命名不含业务方言；聚焦通用抽象。|
| 行为确定性 | 影响签名/哈希/归一化的代码必须写明稳定性保证与测试。|
| 防漂移 | 公共工具新增前先全仓检索是否已有重复。|

---
## 📚 相关文档
- 平台错误处理规范：`../docs/platform-error-handling.md`
- 跨服务错误链路最佳实践：`../docs/cross-service-error-best-practices.md`
- 根级开发指引：`../AGENTS.md`

---
## © License
内部阶段，暂未开源；后续评估迁移至 Apache 2.0。
