# patra-common — 共享基础(多模块)

> **多模块项目**,为 Papertrace 微服务提供核心工具、存储抽象和共享模型。

---

## 📦 模块结构

`patra-common` 现在是一个**多模块聚合器**,包含三个独立的子模块:

```
patra-common/                    (聚合 POM - 无代码)
├── patra-common-core/          (核心基础 - 所有服务必需)
├── patra-common-storage/       (存储键生成 - 按需依赖)
└── patra-common-model/         (共享数据模型 - 按需依赖)
```

---

## 🎯 设计理念

### 之前(旧结构)
❌ **问题**: 所有微服务被迫依赖 `patra-common` 中的所有代码,包括:
- 对象存储键生成(仅 patra-ingest 使用)
- StandardLiterature 模型(仅 3 个模块使用)
- 违反了"按需依赖"原则

### 之后(新结构)
✅ **解决方案**: 边界清晰的模块化设计:
- **patra-common-core**: 真正共享的核心工具(domain/error/enums/json/util)
- **patra-common-storage**: 存储键生成(可选,DDD 业务规则)
- **patra-common-model**: 共享数据模型(可选,服务间契约)

---

## 📌 子模块

### 1. patra-common-core (所有服务必需)

**构件**: `com.papertrace:patra-common-core`

**目的**: 所有 Papertrace 服务使用的基础类。

**内容**:
- **domain/**: DDD 基础类(`AggregateRoot`、`DomainEvent`)
- **error/**: 异常层次结构、错误码、特征
- **enums/**: 共享枚举(`ProvenanceCode`、`Priority`)
- **json/**: JSON 工具(`JsonMapperHolder`、`JsonNormalizer`)
- **messaging/**: 消息通道标识符
- **util/**: 通用工具(`HashUtils`)

**依赖**: Hutool、Jackson、SLF4J(provided)

**用法**:
```xml
<dependency>
    <groupId>com.papertrace</groupId>
    <artifactId>patra-common-core</artifactId>
</dependency>
```

**使用者**: 所有 `*-domain`、`*-app`、`*-infra`、`*-adapter` 模块

---

### 2. patra-common-storage (可选 - 按需使用)

**构件**: `com.papertrace:patra-common-storage`

**目的**: 标准化的对象存储键生成策略。

**内容**:
- **ObjectKeyContext**: 键生成的不可变上下文
- **ObjectKeyGenerator**: 策略接口
- **DatePartitionedKeyGenerator**: 基于日期的分区(yyyy/MM/dd)
- **ObjectKeyTemplate**: 常见模式的工厂方法

**键格式**:
```
{service}/{business-type}/{yyyy}/{MM}/{dd}/{business-id}.{extension}
```

**示例**:
```java
import com.patra.common.storage.ObjectKeyTemplate;

String key = ObjectKeyTemplate.generateDailyKey(
    "ingest", "literature-batch", "pubmed-123-batch-001", "json"
);
// 结果: ingest/literature-batch/2025/10/28/pubmed-123-batch-001.json
```

**依赖**: Hutool

**用法**:
```xml
<dependency>
    <groupId>com.papertrace</groupId>
    <artifactId>patra-common-storage</artifactId>
</dependency>
```

**使用者**:
- `patra-spring-boot-starter-object-storage` (StorageLocationResolver)
- 任何需要标准化存储键生成的服务

**设计说明**: 这是一个**业务规则**(命名约定),而非基础设施代码。将其与 `object-storage` starter 分离,允许领域层使用标准化命名而无需依赖 Spring 框架。

---

### 3. patra-common-model (可选 - 按需使用)

**构件**: `com.papertrace:patra-common-model`

**目的**: 服务间通信的共享数据模型。

**内容**:
- **StandardLiterature**: 跨服务使用的通用文献数据结构

**依赖**: Jackson(用于 JSON 序列化)

**用法**:
```xml
<dependency>
    <groupId>com.papertrace</groupId>
    <artifactId>patra-common-model</artifactId>
</dependency>
```

**使用者**:
- `patra-ingest-domain` (端口接口)
- `patra-ingest-app` (编排器)
- `patra-spring-boot-starter-provenance` (数据适配器)

---

## 🔧 迁移指南

### 对于服务模块

**之前**(旧依赖):
```xml
<dependency>
    <groupId>com.papertrace</groupId>
    <artifactId>patra-common</artifactId>
</dependency>
```

**之后**(选择您需要的):
```xml
<!-- 必需: 核心工具 -->
<dependency>
    <groupId>com.papertrace</groupId>
    <artifactId>patra-common-core</artifactId>
</dependency>

<!-- 可选: 如果需要存储键生成 -->
<dependency>
    <groupId>com.papertrace</groupId>
    <artifactId>patra-common-storage</artifactId>
</dependency>

<!-- 可选: 如果需要 StandardLiterature 模型 -->
<dependency>
    <groupId>com.papertrace</groupId>
    <artifactId>patra-common-model</artifactId>
</dependency>
```

### 导入语句变更

**存储包重命名**:
```java
// 之前
import com.patra.common.objectstorage.*;

// 之后
import com.patra.common.storage.*;
```

**注意**: `StorageContext` 和 `StorageLocation` 已移至:
```java
import com.patra.starter.objectstorage.StorageContext;
import com.patra.starter.objectstorage.StorageLocation;
```

---

## 🏗️ 架构优势

### 1. 按需依赖 ✅
- 服务仅依赖实际使用的内容
- 减少 classpath 污染
- 不需要所有功能的服务构建更快

### 2. 清晰边界 ✅
- **Core**: 真正通用的工具
- **Storage**: 领域级命名规则(业务逻辑)
- **Model**: 服务间契约

### 3. 符合六边形架构 ✅
- 领域层可以使用 `patra-common-storage`(业务规则)而无需依赖基础设施(`object-storage` starter)
- 关注点分离: 命名策略 vs. 存储实现

### 4. 独立演进 ✅
- 每个子模块可以独立演进
- 版本管理灵活性(未来)

---

## 🔗 依赖关系

```
patra-common (POM 聚合器)
    ↓
    ├─ patra-common-core (Hutool, Jackson, SLF4J)
    │     ↑
    │     └─ 所有微服务层
    │
    ├─ patra-common-storage (Hutool)
    │     ↑
    │     └─ patra-spring-boot-starter-object-storage
    │
    └─ patra-common-model (Jackson)
          ↑
          ├─ patra-ingest-domain
          └─ patra-spring-boot-starter-provenance
```

---

## 📊 模块统计

| 模块 | 类数 | 代码行数 | 依赖 | 使用情况 |
|--------|---------|-----|--------------|-------|
| **patra-common-core** | ~27 | ~2500 | Hutool, Jackson | 所有服务(必需) |
| **patra-common-storage** | 4 | ~300 | Hutool | patra-ingest, object-storage starter |
| **patra-common-model** | 1 | ~200 | Jackson | patra-ingest, patra-provenance |

---

## 🚀 构建命令

```bash
# 构建所有子模块
cd patra-common
mvn clean install

# 构建特定子模块
cd patra-common-core
mvn clean install

# 验证依赖
mvn dependency:tree
```

---

## 🔗 相关文档

- [ARCHITECTURE.md](../docs/ARCHITECTURE.md) — 六边形架构原则
- [DEV-GUIDE.md](../docs/DEV-GUIDE.md) — 开发指南
- [AGENTS-architecture.md](../.claude/AGENTS-architecture.md) — DDD 模式参考

---

**最后更新**: 2025-10-28
**迁移**: patra-common → 多模块结构(patra-common-core/storage/model)
