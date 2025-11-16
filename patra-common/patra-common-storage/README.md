# patra-common-storage — 对象存储键生成策略

> **标准化的对象存储键生成策略模块**,提供业务级命名规则,支持日期分区和层次化结构。

---

## 概述

`patra-common-storage` 是 Patra 平台的对象存储键生成策略模块,提供标准化的对象存储键命名规则。本模块定义了跨所有微服务的统一键格式,确保对象存储(S3/MinIO/OSS)中的数据组织一致、可预测、易管理。

**重要说明**: 本模块是**业务规则**(命名约定),而非基础设施代码。它与 `patra-spring-boot-starter-object-storage` 分离,允许领域层使用标准化命名而无需依赖 Spring 框架,完全符合六边形架构的关注点分离原则。

---

## 核心职责

- **标准化命名**: 定义跨服务的统一对象键格式
- **日期分区**: 支持基于日期的时间分区(yyyy/MM/dd),便于生命周期管理和成本分析
- **策略模式**: 提供可扩展的键生成策略接口,支持运行时切换或自定义实现
- **规范化处理**: 自动规范化服务名、业务类型、扩展名等字段
- **便捷工厂**: 提供静态工厂方法,简化常见场景的键生成

---

## 模块结构

```
patra-common-storage/
└── storage/
    ├── ObjectKeyContext              (不可变上下文)
    ├── ObjectKeyGenerator            (策略接口)
    ├── DatePartitionedKeyGenerator   (日期分区实现)
    └── ObjectKeyTemplate             (工厂方法)
```

---

## 主要组件

### 1. ObjectKeyContext — 不可变上下文

封装对象键生成所需的所有参数,确保参数验证和不可变性。

**核心字段**:
- `serviceName`: 微服务名称(短形式,如 "ingest"、"storage"、"publication")
- `businessType`: 业务类型(kebab-case,如 "publication-batch"、"metadata-snapshot")
- `businessId`: 唯一业务标识符(如 "pubmed-123-batch-001")
- `partitionDate`: 分区日期(用于时间分区 yyyy/MM/dd)
- `extension`: 文件扩展名(不含前导点,如 "json"、"json.gz"、"xml")
- `customSegments`: 可选的自定义路径段(不可变 Map)

**使用示例**:
```java
// 简单构造
ObjectKeyContext context = ObjectKeyContext.of(
    "ingest",
    "publication-batch",
    "pubmed-123-batch-001",
    LocalDate.now(),
    "json"
);

// Builder 模式(复杂场景)
ObjectKeyContext context = ObjectKeyContext.builder()
    .serviceName("publication")
    .businessType("index-snapshot")
    .businessId("snapshot-20251103-001")
    .partitionDate(LocalDate.of(2025, 11, 3))
    .extension("json.gz")
    .customSegment("env", "prod")
    .build();
```

---

### 2. ObjectKeyGenerator — 策略接口

定义键生成策略的通用契约,支持策略模式。

**设计模式**: 策略模式(Strategy Pattern)

**接口定义**:
```java
@FunctionalInterface
public interface ObjectKeyGenerator {
    String generate(ObjectKeyContext context);
}
```

**实现要求**:
- 无状态且线程安全
- 返回相对路径(不含存储桶名称)
- 符合规范化要求(小写、kebab-case、统一分隔符)

**标准实现**:
- `DatePartitionedKeyGenerator`: 日期分区实现
- 未来扩展: `MonthPartitionedKeyGenerator`、`HierarchicalKeyGenerator` 等

---

### 3. DatePartitionedKeyGenerator — 日期分区实现

按日期分区的键生成器,遵循标准格式:

**键格式**: `{service}/{business-type}/{yyyy}/{MM}/{dd}/{business-id}.{extension}`

**示例输出**:
```
ingest/publication-batch/2025/11/03/pubmed-123-batch-001.json
storage/metadata-snapshot/2025/11/02/snapshot-20251102-001.json.gz
publication/index/2025/11/03/index-pmid-12345.xml
```

**规范化规则**:
- **服务名**: 转小写(`Ingest` → `ingest`)
- **业务类型**: 转小写并将下划线替换为连字符(`publication_batch` → `publication-batch`)
- **扩展名**: 移除前导点(`.json` → `json`)
- **日期分区**: 三级层次结构(`2025/11/03`)

**线程安全**: 单例实例 `DatePartitionedKeyGenerator.INSTANCE` 可安全共享。

**使用示例**:
```java
ObjectKeyContext context = ObjectKeyContext.of(
    "ingest", "publication-batch", "pubmed-123", LocalDate.now(), "json"
);
String key = DatePartitionedKeyGenerator.INSTANCE.generate(context);
// 结果: ingest/publication-batch/2025/11/03/pubmed-123.json
```

---

### 4. ObjectKeyTemplate — 工厂方法

提供便捷的静态工厂方法,简化常见场景的键生成。

**核心方法**:

#### generateDailyKey(service, businessType, businessId, extension)
使用当前日期生成键(最常用)。

```java
String key = ObjectKeyTemplate.generateDailyKey(
    "ingest", "publication-batch", "pubmed-123-batch-001", "json"
);
// 结果: ingest/publication-batch/2025/11/03/pubmed-123-batch-001.json
```

#### generateDailyKey(service, businessType, businessId, partitionDate, extension)
使用指定日期生成键(历史数据/回填场景)。

```java
String key = ObjectKeyTemplate.generateDailyKey(
    "ingest",
    "publication-batch",
    "pubmed-456-batch-002",
    LocalDate.of(2025, 10, 20),
    "json.gz"
);
// 结果: ingest/publication-batch/2025/10/20/pubmed-456-batch-002.json.gz
```

#### builder()
创建 Builder 用于复杂场景(自定义段)。

```java
String key = ObjectKeyTemplate.builder()
    .serviceName("publication")
    .businessType("index_snapshot")
    .businessId("snapshot-20251103-001")
    .partitionDate(LocalDate.now())
    .extension("json.gz")
    .customSegment("env", "prod")
    .build();
```

---

## 依赖关系

### 上游依赖

| 依赖 | 版本 | 用途 |
|------|------|------|
| **Hutool** | (继承自 patra-parent) | 日期格式化、字符串处理 |

### 下游消费者

- **patra-spring-boot-starter-object-storage**: StorageLocationResolver
- **patra-ingest-domain**: 文献批次键生成
- **patra-ingest-infra**: 对象存储适配器
- 任何需要标准化存储键生成的服务

---

## 使用示例

### Maven 依赖

```xml
<dependency>
    <groupId>com.patra</groupId>
    <artifactId>patra-common-storage</artifactId>
</dependency>
```

### 典型使用场景

#### 场景 1: 文献批次数据存储(当前日期)

```java
import com.patra.common.storage.ObjectKeyTemplate;

public class PublicationBatchRepository {
    public String generateBatchKey(String batchId) {
        return ObjectKeyTemplate.generateDailyKey(
            "ingest",
            "publication-batch",
            batchId,
            "json"
        );
    }
}
// 输出: ingest/publication-batch/2025/11/03/batch-001.json
```

#### 场景 2: 历史数据回填(指定日期)

```java
import com.patra.common.storage.ObjectKeyTemplate;
import java.time.LocalDate;

public class HistoricalDataIngester {
    public String generateHistoricalKey(String dataId, LocalDate originalDate) {
        return ObjectKeyTemplate.generateDailyKey(
            "ingest",
            "historical-batch",
            dataId,
            originalDate,
            "json.gz"
        );
    }
}
// 输出: ingest/historical-batch/2025/10/01/data-001.json.gz
```

#### 场景 3: 领域层使用(无 Spring 依赖)

```java
package com.patra.ingest.domain.batch;

import com.patra.common.storage.ObjectKeyTemplate;
import java.time.LocalDate;

public class PublicationBatch {
    private final String batchId;
    private final LocalDate creationDate;

    public String generateStorageKey() {
        return ObjectKeyTemplate.generateDailyKey(
            "ingest",
            "publication-batch",
            batchId,
            creationDate,
            "json"
        );
    }
}
```

#### 场景 4: 自定义策略(未来扩展)

```java
import com.patra.common.storage.*;

public class MonthPartitionedKeyGenerator implements ObjectKeyGenerator {
    @Override
    public String generate(ObjectKeyContext context) {
        // 自定义实现: {service}/{business-type}/{yyyy}/{MM}/{business-id}.{ext}
        // 省略日级分区
    }
}

// 使用自定义策略
ObjectKeyContext context = ObjectKeyContext.of(...);
String key = ObjectKeyTemplate.generate(context, new MonthPartitionedKeyGenerator());
```

---

## 技术栈

| 技术 | 版本 | 说明 |
|------|------|------|
| **Java** | 25 | 使用 Record、Pattern Matching 等现代特性 |
| **Hutool** | (继承自 parent) | 日期格式化、字符串工具 |

---

## 设计原则

### 1. 业务规则而非基础设施
- 本模块定义**命名约定**(业务规则),而非存储实现(基础设施)
- 领域层可直接使用,无需依赖 Spring 或对象存储客户端
- 符合六边形架构的关注点分离

### 2. 策略模式 + 单例
- `ObjectKeyGenerator` 接口支持策略模式
- `DatePartitionedKeyGenerator.INSTANCE` 提供无状态单例
- 便于扩展新的分区策略

### 3. 不可变上下文
- `ObjectKeyContext` 是不可变 Record
- 参数验证在构造时完成
- 线程安全且易于测试

### 4. 便捷工厂
- `ObjectKeyTemplate` 提供静态工厂方法
- 简化常见场景的使用
- 无需直接实例化生成器

---

## 架构优势

### 1. 分离业务规则与基础设施
- **本模块**: 定义命名规则(业务逻辑)
- **object-storage starter**: 实现存储操作(基础设施)
- 领域层可使用命名规则而不依赖存储实现

### 2. 统一命名规范
- 跨所有微服务的一致键格式
- 便于运维、监控、成本分析
- 支持基于日期的生命周期策略

### 3. 易于扩展
- 策略接口支持自定义实现
- 可按服务/用例配置不同策略
- 未来可添加月分区、层次化等策略

### 4. 领域层友好
- 无框架依赖
- 纯 Java 实现
- 符合 DDD 原则

---

## 相关文档

- [patra-common/README.md](../README.md) — 多模块聚合器总览
- [patra-common-core/README.md](../patra-common-core/README.md) — 核心基础设施
- [patra-common-model/README.md](../patra-common-model/README.md) — 共享数据模型
- [patra-spring-boot-starter-object-storage](../../patra-spring-boot-starter-object-storage/README.md) — 对象存储 Starter

---

**Maven 坐标**: `com.patra:patra-common-storage`
**版本**: 0.1.0-SNAPSHOT
**最后更新**: 2025-11-03
