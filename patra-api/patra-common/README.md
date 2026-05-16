# patra-common — 共享基础设施聚合器

> **多模块聚合项目**,为 Patra 微服务提供核心工具、存储抽象和共享模型。

---

## 概述

`patra-common` 是 Patra 平台的基础设施层聚合器,采用多模块结构,按职责清晰划分为三个独立的子模块:

- **patra-common-core**: 核心基础设施(领域基类、异常处理、通用枚举、JSON 工具)
- **patra-common-storage**: 对象存储键生成策略(业务级命名规则)
- **patra-common-model**: 跨服务共享的标准化数据模型

每个子模块独立发布,服务可按需引入所需依赖,避免不必要的 classpath 污染。

---

## 核心职责

- **基础设施抽象**: 提供 DDD 领域层基类、异常层次结构、通用枚举
- **JSON 标准化**: 统一的 JSON 序列化/反序列化配置和规范化工具
- **存储命名规范**: 标准化的对象存储键生成策略(日期分区、层次化结构)
- **数据模型共享**: 服务间通信的共享数据结构(Shared Kernel)

---

## 模块结构

```
patra-common/                         (Gradle 聚合项目 - 无代码)
├── patra-common-core/               (核心基础 - 所有服务必需)
│   ├── domain/                      (领域层基类)
│   ├── error/                       (异常处理框架)
│   │   └── remote/                  (远程调用异常)
│   ├── enums/                       (共享枚举)
│   ├── json/                        (JSON 工具)
│   ├── messaging/                   (消息通道标识)
│   └── util/                        (通用工具)
├── patra-common-storage/            (存储键生成 - 按需依赖)
│   └── storage/                     (对象存储键生成策略)
└── patra-common-model/              (共享模型 - 按需依赖)
    └── model/                       (CanonicalPublication 等)
```

---

## 子模块说明

### 1. patra-common-core (必需)

**模块坐标**: `dev.linqibin.patra:patra-common-core`

**定位**: 所有 Patra 服务必须依赖的核心基础设施。

**主要内容**:
- **domain**: `AggregateRoot`、`DomainEvent`、`ReadOnlyAggregate`
- **error**: `DomainException`、`ApplicationException`、`ErrorCodeLike`、`ErrorTrait`、`RemoteCallException`、`RemoteErrorHelper`
- **enums**: `ProvenanceCode`、`Priority`、`IngestDateType`、`RegistryConfigScope`
- **json**: `JsonMapperHolder`、`JsonNormalizer`、`JsonNormalizerConfig`
- **messaging**: `ChannelKey`
- **util**: `HashUtils`

**使用场景**: 所有 `*-domain`、`*-app`、`*-infra`、`*-adapter` 模块

**详细文档**: [patra-common-core/README.md](patra-common-core/README.md)

---

### 2. patra-common-storage (可选)

**模块坐标**: `dev.linqibin.patra:patra-common-storage`

**定位**: 标准化的对象存储键生成策略(业务规则,非基础设施代码)。

**主要内容**:
- `ObjectKeyContext`: 键生成的不可变上下文
- `ObjectKeyGenerator`: 策略接口
- `DatePartitionedKeyGenerator`: 日期分区实现(yyyy/MM/dd)
- `ObjectKeyTemplate`: 常见模式的工厂方法

**键格式**: `{service}/{business-type}/{yyyy}/{MM}/{dd}/{business-id}.{extension}`

**使用场景**: 任何需要标准化存储键生成的服务

**详细文档**: [patra-common-storage/README.md](patra-common-storage/README.md)

---

### 3. patra-common-model (可选)

**模块坐标**: `dev.linqibin.patra:patra-common-model`

**定位**: 跨服务共享的标准化数据模型(Shared Kernel)。

**主要内容**:
- `CanonicalPublication`: 标准化出版物数据结构
- `AuthorInfo`: 作者快照
- `JournalInfo`: 期刊快照

**使用场景**: `patra-ingest`、`patra-spring-boot-starter-provenance`

**详细文档**: [patra-common-model/README.md](patra-common-model/README.md)

---

## 依赖关系

**上游依赖**:
- `patra-common-core`: Hutool、Jackson、SLF4J
- `patra-common-storage`: Hutool
- `patra-common-model`: Jackson

**下游消费者**:
- **core**: 所有微服务的所有层
- **storage**: 需要对象存储的服务
- **model**: 需要标准化出版物模型的服务

---

## 使用示例

### 引入依赖

```kotlin
// 必需: 核心基础设施
implementation(project(":patra-common:patra-common-core"))

// 可选: 存储键生成策略
implementation(project(":patra-common:patra-common-storage"))

// 可选: 共享数据模型
implementation(project(":patra-common:patra-common-model"))
```

### 使用存储键生成器

```java
import dev.linqibin.patra.common.storage.ObjectKeyTemplate;

String key = ObjectKeyTemplate.generateDailyKey(
    "ingest", "publication-batch", "pubmed-123-batch-001", "json"
);
// 结果: ingest/publication-batch/2025/11/03/pubmed-123-batch-001.json
```

### 使用 JSON 标准化工具

```java
import dev.linqibin.patra.common.json.JsonNormalizer;

JsonNormalizerResult result = JsonNormalizer.normalizeDefault(payload);
String canonicalJson = result.getCanonicalJson();
byte[] hashMaterial = result.getHashMaterial();
```

---

## 技术栈

| 模块 | 核心依赖 | 说明 |
|------|---------|------|
| **patra-common-core** | Hutool, Jackson, SLF4J | 核心基础设施 |
| **patra-common-storage** | Hutool | 存储键生成策略 |
| **patra-common-model** | Jackson | 共享数据模型 |

---

## 架构优势

### 1. 按需依赖
服务仅依赖实际使用的模块,减少 classpath 污染和构建时间。

### 2. 清晰边界
- **Core**: 真正通用的基础设施
- **Storage**: 业务级命名规则(DDD 业务逻辑)
- **Model**: 服务间契约(Shared Kernel)

### 3. 符合六边形架构
领域层可使用 `storage` 模块(业务规则)而无需依赖基础设施(`object-storage` starter)。

### 4. 独立演进
每个子模块可独立版本管理和发布。

---

## 相关文档

- [patra-common-core/README.md](patra-common-core/README.md) — 核心基础设施详细文档
- [patra-common-storage/README.md](patra-common-storage/README.md) — 存储键生成策略详细文档
- [patra-common-model/README.md](patra-common-model/README.md) — 共享数据模型详细文档

---

**版本**: 0.1.0-SNAPSHOT
**最后更新**: 2026-01-14
