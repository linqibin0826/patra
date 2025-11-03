# patra-common-core — 核心基础设施

> **所有 Papertrace 服务必需的核心基础设施模块**,提供 DDD 领域层基类、异常处理框架、共享枚举、JSON 工具和通用工具类。

---

## 概述

`patra-common-core` 是 Papertrace 平台的核心基础设施模块,为所有微服务提供必需的基础抽象和工具类。本模块严格遵循"无框架依赖"原则,领域层代码仅依赖 JDK 和精选的工具库(Hutool、Jackson),确保领域逻辑的纯粹性和可移植性。

本模块是 **patra-common** 聚合器的核心子模块,所有微服务的所有层(`*-domain`、`*-app`、`*-infra`、`*-adapter`)都必须依赖此模块。

---

## 核心职责

- **领域层基础**: 提供 DDD 聚合根、领域事件等基类,支持事件驱动架构
- **异常体系**: 统一的异常层次结构,支持领域异常和应用异常
- **错误特征**: 语义化错误分类(ErrorTrait),支持一致的 HTTP 状态码映射
- **共享枚举**: 跨服务使用的通用枚举(ProvenanceCode、Priority 等)
- **JSON 标准化**: ObjectMapper 持有者、JSON 规范化工具,支持签名和去重
- **消息通道**: 消息通道键标识符,支持事件总线路由
- **通用工具**: 哈希计算、字符串处理等工具类

---

## 模块结构

```
patra-common-core/
├── domain/                          (领域层基类)
│   ├── AggregateRoot               (聚合根抽象基类)
│   ├── DomainEvent                 (领域事件标记接口)
│   └── ReadOnlyAggregate           (只读聚合根)
├── error/                           (异常处理框架)
│   ├── DomainException             (领域层异常基类)
│   ├── ApplicationException        (应用层异常基类)
│   ├── trait/
│   │   ├── ErrorTrait              (错误特征枚举)
│   │   └── HasErrorTraits          (错误特征标记接口)
│   ├── codes/
│   │   ├── ErrorCodeLike           (错误码接口)
│   │   └── HttpStdErrors           (标准 HTTP 错误码)
│   └── problem/
│       └── ErrorKeys               (错误键常量)
├── enums/                           (共享枚举)
│   ├── ProvenanceCode              (数据源枚举)
│   ├── Priority                    (优先级枚举)
│   ├── IngestDateType              (采集日期类型)
│   ├── RegistryConfigScope         (配置作用域)
│   └── SortDirection               (排序方向)
├── json/                            (JSON 工具)
│   ├── JsonMapperHolder            (全局 ObjectMapper 持有者)
│   ├── JsonNormalizer              (JSON 规范化工具)
│   ├── JsonNormalizerConfig        (规范化配置)
│   ├── JsonNormalizerResult        (规范化结果)
│   ├── JsonNormalizationException  (规范化异常)
│   ├── JsonNodeMappings            (JsonNode 映射工具)
│   ├── NormalizationPath           (规范化路径)
│   ├── TemporalCoercion            (时间类型强制转换)
│   └── TemporalAccessorWrapper     (时间访问器包装)
├── messaging/                       (消息通道)
│   └── ChannelKey                  (通道键标识符)
└── util/                            (通用工具)
    └── HashUtils                   (哈希计算工具)
```

---

## 主要组件

### 1. domain — 领域层基础

#### AggregateRoot<ID>
聚合根抽象基类,DDD 核心概念实现。

**核心特性**:
- 支持领域事件收集和发布(事件溯源模式)
- 乐观锁版本管理
- 聚合标识符生命周期管理
- 领域不变量检查钩子

**使用示例**:
```java
public class Literature extends AggregateRoot<LiteratureId> {
    private String title;
    private PublicationStatus status;

    public void publish() {
        this.status = PublicationStatus.PUBLISHED;
        addDomainEvent(new LiteraturePublishedEvent(getId(), Instant.now()));
        assertInvariants();
    }

    @Override
    protected void assertInvariants() {
        if (status == PublicationStatus.PUBLISHED && title == null) {
            throw new IllegalStateException("已发布文献必须有标题");
        }
    }
}
```

#### DomainEvent
领域事件标记接口,用于标识领域内发生的重要状态变更。

**设计模式**: 观察者模式 + 事件溯源

---

### 2. error — 异常处理框架

#### DomainException
领域层异常基类,用于表示业务规则违反、领域不变量破坏等领域层错误。

**设计原则**: 领域层保持无框架依赖,不依赖 Spring 或其他基础设施。

**使用示例**:
```java
public class LiteratureNotFoundException extends DomainException {
    public LiteratureNotFoundException(String literatureId) {
        super("文献不存在: " + literatureId);
    }
}
```

#### ErrorTrait
语义化错误特征枚举,用于将异常映射到 HTTP 状态码和统一错误响应。

**支持的特征**:
- `NOT_FOUND` → HTTP 404
- `CONFLICT` → HTTP 409
- `RULE_VIOLATION` → HTTP 422
- `QUOTA_EXCEEDED` → HTTP 429
- `UNAUTHORIZED` → HTTP 401
- `FORBIDDEN` → HTTP 403
- `TIMEOUT` → HTTP 504
- `DEP_UNAVAILABLE` → HTTP 503

**使用示例**:
```java
public class QuotaExceededException extends ApplicationException implements HasErrorTraits {
    @Override
    public Set<ErrorTrait> getErrorTraits() {
        return Set.of(ErrorTrait.QUOTA_EXCEEDED);
    }
}
```

---

### 3. enums — 共享枚举

#### ProvenanceCode
数据源枚举,标识文献的上游来源(PubMed、PMC、EPMC、OpenAlex 等)。

**特性**:
- 支持字符串解析和别名识别
- Jackson 序列化/反序列化支持
- 人类可读的描述信息

**使用示例**:
```java
ProvenanceCode source = ProvenanceCode.parse("pubmed");
// 支持别名: "medline" → PUBMED, "europepmc" → EPMC

String code = source.getCode(); // "PUBMED"
String desc = source.getDescription(); // "PubMed"
```

#### Priority
通用优先级枚举(`HIGH`、`MEDIUM`、`LOW`),用于任务调度、消息队列等场景。

---

### 4. json — JSON 工具

#### JsonMapperHolder
全局 ObjectMapper 持有者,提供非 Spring 环境下的共享 JSON 配置。

**设计目的**:
- 避免 ObjectMapper 重复实例化
- 在 Spring 之外提供一致的 JSON 配置
- 支持 Spring 容器 mapper 桥接

**使用指南**:
- **Spring 环境**: 优先使用 DI 注入 `ObjectMapper`
- **非 Spring 环境**: 使用 `JsonMapperHolder.getObjectMapper()`

**使用示例**:
```java
// 非 Spring 代码
ObjectMapper om = JsonMapperHolder.getObjectMapper();
String json = om.writeValueAsString(data);

// Spring 自动桥接(由 patra-spring-boot-starter-core 完成)
JsonMapperHolder.register(containerManagedMapper);
```

#### JsonNormalizer
JSON 规范化工具,将任意输入(POJO、JsonNode、字符串)转换为确定性的规范 JSON。

**核心功能**:
- **键排序**: 稳定的对象键排序(ASCII/Unicode 比较器)
- **数组处理**: 去重、排序、类型标签化(保留序列字段顺序)
- **空值策略**: 移除空对象/数组/字符串(支持白名单)
- **类型强制转换**: 布尔值、数字、时间戳规范化
- **字符串清理**: trim、空白折叠、字段级小写
- **时间规范化**: 多格式解析(支持秒/毫秒 epoch),输出 UTC ISO-8601
- **安全防护**: UTF-8 字节限制、最大深度、拒绝非有限数字

**使用场景**:
- 内容签名和校验
- 去重键生成
- 缓存键标准化
- 多源数据规范化

**使用示例**:
```java
// 快速规范化
JsonNormalizerResult result = JsonNormalizer.normalizeDefault(payload);
String canonicalJson = result.getCanonicalJson();
byte[] hashMaterial = result.getHashMaterial();

// 自定义配置
JsonNormalizer normalizer = JsonNormalizer.withConfig(
    JsonNormalizerConfig.builder()
        .coerceNumber(true)
        .coerceTime(true)
        .removeEmpty(true)
        .build()
);
JsonNormalizerResult result2 = normalizer.normalize(complexPayload);
```

---

### 5. messaging — 消息通道

#### ChannelKey
消息通道键标识符,用于事件总线路由和消息订阅。

**使用示例**:
```java
public static final ChannelKey LITERATURE_INGEST =
    ChannelKey.of("literature.ingest");

// 发布事件
eventBus.publish(LITERATURE_INGEST, event);

// 订阅事件
eventBus.subscribe(LITERATURE_INGEST, handler);
```

---

### 6. util — 通用工具

#### HashUtils
哈希计算工具,支持 SHA-256、MD5 等算法。

**使用示例**:
```java
String hash = HashUtils.sha256(data);
String md5 = HashUtils.md5(data);
```

---

## 依赖关系

### 上游依赖

| 依赖 | 版本 | 用途 |
|------|------|------|
| **Hutool** | (继承自 patra-parent) | 日期/字符串工具、加密工具 |
| **Jackson** | (继承自 patra-parent) | JSON 序列化/反序列化 |
| **SLF4J** | (provided) | 日志 API(运行时提供) |

### 下游消费者

- **所有微服务**: `patra-ingest`、`patra-registry`、`patra-gateway` 等
- **所有层级**: `*-domain`、`*-app`、`*-infra`、`*-adapter`
- **Starter 模块**: `patra-spring-boot-starter-*`

---

## 使用示例

### Maven 依赖

```xml
<dependency>
    <groupId>com.papertrace</groupId>
    <artifactId>patra-common-core</artifactId>
</dependency>
```

### 典型使用场景

#### 场景 1: 创建聚合根

```java
package com.patra.ingest.domain.literature;

import com.patra.common.domain.AggregateRoot;
import com.patra.common.domain.DomainEvent;
import java.time.Instant;

public class LiteratureBatch extends AggregateRoot<LiteratureBatchId> {
    private String batchId;
    private BatchStatus status;
    private int totalCount;

    public void complete(int processedCount) {
        this.status = BatchStatus.COMPLETED;
        addDomainEvent(new BatchCompletedEvent(getId(), processedCount, Instant.now()));
    }
}
```

#### 场景 2: 定义领域异常

```java
package com.patra.ingest.domain.exception;

import com.patra.common.error.DomainException;

public class InvalidProvenanceException extends DomainException {
    public InvalidProvenanceException(String provenance) {
        super("无效的数据源: " + provenance);
    }
}
```

#### 场景 3: 使用 ProvenanceCode 枚举

```java
import com.patra.common.enums.ProvenanceCode;

ProvenanceCode source = ProvenanceCode.parse(rawSource);
if (source == ProvenanceCode.PUBMED) {
    // PubMed 特定处理逻辑
}
```

#### 场景 4: JSON 规范化用于去重

```java
import com.patra.common.json.JsonNormalizer;
import com.patra.common.util.HashUtils;

JsonNormalizerResult result = JsonNormalizer.normalizeDefault(literatureData);
String contentHash = HashUtils.sha256(result.getHashMaterial());
// 使用 contentHash 作为去重键
```

---

## 技术栈

| 技术 | 版本 | 说明 |
|------|------|------|
| **Java** | 25 | 使用最新 JDK 特性(Record、Pattern Matching) |
| **Hutool** | (继承自 parent) | 轻量级 Java 工具库 |
| **Jackson** | (继承自 parent) | JSON 序列化/反序列化 |
| **Lombok** | (编译时) | 减少样板代码 |

---

## 设计原则

### 1. 无框架依赖(领域层纯粹性)
- 领域层基类(`AggregateRoot`、`DomainException`)仅依赖 JDK
- 不引入 Spring、Jakarta EE 等框架依赖
- 确保领域逻辑可移植、可测试

### 2. 六边形架构兼容
- 清晰的层次边界
- 领域层不依赖基础设施
- 异常体系支持适配器层转换

### 3. 事件驱动架构支持
- 聚合根支持领域事件收集
- 应用层负责事件发布(outbox 模式)
- 解耦聚合间的通信

### 4. 一致性优先
- 统一的异常体系
- 共享的枚举定义
- 标准化的 JSON 处理

---

## 相关文档

- [patra-common/README.md](../README.md) — 多模块聚合器总览
- [patra-common-storage/README.md](../patra-common-storage/README.md) — 存储键生成策略
- [patra-common-model/README.md](../patra-common-model/README.md) — 共享数据模型
- [ARCHITECTURE.md](../../docs/ARCHITECTURE.md) — 六边形架构原则
- [DEV-GUIDE.md](../../docs/DEV-GUIDE.md) — 开发指南

---

**Maven 坐标**: `com.papertrace:patra-common-core`
**版本**: 0.1.0-SNAPSHOT
**最后更新**: 2025-11-03
