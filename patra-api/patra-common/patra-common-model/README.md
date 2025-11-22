# patra-common-model — 共享数据模型

> **跨服务共享的标准化数据模型模块**,提供 Shared Kernel 契约,支持服务间数据交换和通信。

---

## 概述

`patra-common-model` 是 Patra 平台的共享数据模型模块,采用 DDD 的 **Shared Kernel** 模式,定义跨服务共享的核心数据结构。本模块提供的模型作为服务间的契约,确保数据交换的一致性和兼容性。

**设计原则**:
- **无行为**: 模型仅包含数据结构,不包含业务逻辑
- **不可变**: 使用 `@Value` 和 `@Builder` 确保不可变性
- **框架无关**: 仅依赖 Jackson,保持可移植性
- **契约优先**: 变更需要考虑所有消费者的兼容性

---

## 核心职责

- **数据契约**: 定义跨服务共享的标准化数据结构
- **服务解耦**: 通过共享模型解耦服务间的直接依赖
- **一致性保证**: 确保不同服务对相同概念的理解一致
- **序列化支持**: 提供 Jackson 注解支持 JSON 序列化/反序列化

---

## 模块结构

```
patra-common-model/
└── model/
    ├── CanonicalPublication           (标准化出版物模型)
    │   ├── CanonicalPublication       (主模型)
    │   ├── AuthorInfo                (作者快照)
    │   └── JournalInfo               (期刊快照)
    └── plan/                         (计划元数据模型)
        ├── PlanMetadata              (计划元数据抽象基类) ⭐新增
        ├── PubmedPlanMetadata        (PubMed 特定元数据) ⭐新增
        ├── EpmcPlanMetadata          (EPMC 特定元数据) ⭐新增
        └── DoajPlanMetadata          (DOAJ 特定元数据) ⭐新增
```

---

## 主要组件

### CanonicalPublication — 标准化出版物模型

跨服务共享的规范化出版物数据结构,作为采集、存储、检索服务之间的数据交换契约。

**设计定位**: Shared Kernel — 多个服务共享的核心领域模型

**核心字段**:
- `title`: 文献标题(String)
- `abstractText`: 摘要或总结文本(String)
- `authors`: 作者列表(`List<AuthorInfo>`,保持呈现顺序)
- `journal`: 期刊元数据(`JournalInfo`,可选)
- `identifiers`: 标识符映射(如 PMID、DOI、PMC,`Map<String, String>`)
- `publicationDate`: 发布日期(`LocalDate`,日精度,可选)
- `keywords`: 领域级关键词(`List<String>`)

**嵌套模型**:

#### AuthorInfo
作者快照,与下游服务契约需求对齐:
- `lastName`: 姓氏
- `foreName`: 名字
- `affiliation`: 所属机构

#### JournalInfo
期刊快照,与下游服务契约需求对齐:
- `title`: 期刊名称
- `issn`: 国际标准期刊号
- `publisher`: 出版商

---

### PlanMetadata 继承体系 — 数据源计划元数据 ⭐新增

**设计定位**: 跨模块通用领域模型,支持多数据源的批次规划和执行。

**核心理念**: 使用继承体系替代 `Map<String, Object>`,提供类型安全和编译时检查。

#### PlanMetadata (抽象基类)

封装将采集任务分解为批次所需的信息,同时允许执行阶段重用上游缓存(如 PubMed 的 WebEnv)。

**核心字段**:
- `dataSourceType`: 数据源类型(如 "pubmed"、"epmc"、"doaj")
- `totalCount`: 总记录数(≥0)
- `plannedAt`: 计划时间戳
- `extensionMetadata`: 扩展元数据(可选,用于未来扩展)

**抽象方法**:
- `hasSessionToken()`: 检查是否包含会话令牌(用于优化批次请求)

**业务约束**:
- `totalCount` 必须 ≥ 0
- `dataSourceType` 不能为空

#### PubmedPlanMetadata (PubMed 特定实现)

包含 PubMed ESearch API 返回的特定信息:

**特定字段**:
- `webEnv`: History Server 会话令牌(用于批次请求优化)
- `queryKey`: 查询键,与 webEnv 配对使用

**业务约束**:
- `webEnv` 和 `queryKey` 必须同时存在或同时为空

**使用场景**:
- 批次规划器使用 `webEnv` + `queryKey` 生成优化的批次请求
- 批次执行器重用上游缓存,避免重复查询

#### EpmcPlanMetadata (EPMC 特定实现)

包含 EPMC API 返回的特定信息:

**特定字段**:
- `cursorMark`: 游标标记,用于基于游标的分页

**使用场景**:
- 支持 EPMC 的游标分页机制

#### DoajPlanMetadata (DOAJ 特定实现)

包含 DOAJ API 返回的特定信息:

**特定字段**:
- `scrollId`: Elasticsearch Scroll ID
- `pageSize`: 每页大小

**业务约束**:
- `pageSize` 必须 > 0

**使用场景**:
- 支持 DOAJ 的 Elasticsearch Scroll API

---

## 依赖关系

**上游依赖**:
- `jackson-databind`: JSON 序列化支持
- `jackson-datatype-jsr310`: Java 8 日期时间支持

**下游消费者**:
- **patra-ingest-domain**: 端口接口定义(DataSourcePort 返回类型)
- **patra-ingest-app**: 应用服务编排(批次规划器使用)
- **patra-ingest-infra**: 基础设施层(适配器实现)
- **patra-spring-boot-starter-provenance**: Provenance 适配器实现(PubMed、EPMC 等)
- 未来: `patra-search` 等下游服务

---

## 使用示例

### Maven 依赖

```xml
<dependency>
    <groupId>com.patra</groupId>
    <artifactId>patra-common-model</artifactId>
</dependency>
```

### 示例 1: 创建标准化文献

```java
import com.patra.common.model.CanonicalPublication;
import com.patra.common.model.CanonicalPublication.AuthorInfo;
import com.patra.common.model.CanonicalPublication.JournalInfo;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

CanonicalPublication publication = CanonicalPublication.builder()
    .title("Deep Learning in Medical Image Analysis")
    .abstractText("This study presents a comprehensive review...")
    .authors(List.of(
        AuthorInfo.builder()
            .lastName("Smith")
            .foreName("John")
            .affiliation("Stanford University")
            .build(),
        AuthorInfo.builder()
            .lastName("Doe")
            .foreName("Jane")
            .affiliation("MIT")
            .build()
    ))
    .journal(JournalInfo.builder()
        .title("Nature Medicine")
        .issn("1546-170X")
        .publisher("Nature Publishing Group")
        .build())
    .identifiers(Map.of(
        "PMID", "12345678",
        "DOI", "10.1038/nm.1234",
        "PMC", "PMC9876543"
    ))
    .publicationDate(LocalDate.of(2025, 10, 15))
    .keywords(List.of("deep learning", "medical imaging", "AI"))
    .build();
```

### 示例 2: 创建 PlanMetadata (PubMed)

```java
import com.patra.common.model.plan.PubmedPlanMetadata;

// 创建 PubMed 计划元数据
PubmedPlanMetadata pubmedPlan = new PubmedPlanMetadata(
    1000,           // totalCount
    "webenv123",    // webEnv
    "1"             // queryKey
);

// 检查会话令牌
if (pubmedPlan.hasSessionToken()) {
    System.out.println("可使用 WebEnv 优化批次请求");
    System.out.println("WebEnv: " + pubmedPlan.webEnv());
    System.out.println("QueryKey: " + pubmedPlan.queryKey());
}
```

### 示例 3: 使用多态处理不同数据源

```java
import com.patra.common.model.plan.PlanMetadata;
import com.patra.common.model.plan.PubmedPlanMetadata;
import com.patra.common.model.plan.EpmcPlanMetadata;

public List<Batch> generateBatches(PlanMetadata plan) {
    // 类型安全的多态处理
    if (plan instanceof PubmedPlanMetadata pubmedPlan) {
        // PubMed 特定逻辑
        return generatePubmedBatches(pubmedPlan);
    } else if (plan instanceof EpmcPlanMetadata epmcPlan) {
        // EPMC 特定逻辑
        return generateEpmcBatches(epmcPlan);
    }
    throw new UnsupportedOperationException("不支持的数据源类型");
}
```

### 示例 4: 在领域层使用(端口接口)

```java
package com.patra.ingest.domain.port;

import com.patra.common.model.plan.PlanMetadata;
import com.patra.common.model.DataType;

/// 数据源端口接口
public interface DataSourcePort {
    /// 准备计划元数据
/// 
/// @param context 执行上下文
/// @param dataType 数据类型
/// @return 计划元数据(使用继承体系支持不同数据源)
    PlanMetadata preparePlan(ExecutionContext context, DataType dataType);
}
```

### 示例 5: 在应用层使用(批次规划)

```java
package com.patra.ingest.app.strategy.planner;

import com.patra.common.model.plan.PlanMetadata;
import com.patra.common.model.plan.PubmedPlanMetadata;

public class UnifiedBatchPlanner {

    public BatchPlan plan(ExecutionContext ctx) {
        // 1. 获取计划元数据
        PlanMetadata planMetadata = dataSourcePort.preparePlan(ctx, DataType.PUBLICATION);

        // 2. 根据类型生成批次
        List<Batch> batches = generateBatches(planMetadata, ctx);

        // 3. 将元数据附加到上下文
        ExecutionContext enrichedContext = ctx.withPlanMetadata(planMetadata);

        return new BatchPlan(batches, enrichedContext);
    }

    private List<Batch> generateBatches(PlanMetadata plan, ExecutionContext ctx) {
        if (plan instanceof PubmedPlanMetadata pubmedPlan) {
            // 使用 WebEnv 优化批次请求
            return generatePubmedBatches(pubmedPlan, ctx);
        }
        // ... 处理其他数据源
    }
}
```

---

## 技术栈

| 技术 | 版本 | 说明 |
|------|------|------|
| **Java** | 25 | 使用 Record、Pattern Matching 等现代特性 |
| **Jackson** | (继承自 parent) | JSON 序列化/反序列化 |
| **Lombok** | (编译时) | `@Value`、`@Builder` 减少样板代码 |

---

## 设计考量

### 1. Shared Kernel 模式
- **定义**: 多个限界上下文共享的核心领域模型
- **适用场景**: 当多个服务需要对相同概念有一致理解时
- **权衡**: 增加耦合度换取一致性,需要谨慎变更

### 2. 快照模式
- **作者和期刊**: 使用快照而非引用
- **优势**: 避免分布式查询,提高性能和可用性
- **劣势**: 数据可能不同步,需要定期更新机制

### 3. 不可变性
- **@Value**: 确保对象一旦创建不可修改
- **线程安全**: 天然线程安全,无需同步
- **缓存友好**: 可安全缓存和共享

### 4. 继承体系 vs Map (PlanMetadata 设计)

#### 为什么使用继承而非 Map<String, Object>?

**❌ 使用 Map 的问题**:
```java
// 问题 1: 无类型安全
Map<String, Object> metadata = plan.getMetadata();
String webEnv = (String) metadata.get("webEnv");  // 运行时类型转换
Integer count = (Integer) metadata.get("count");  // 可能抛出 ClassCastException

// 问题 2: 无编译时检查
String queryKey = (String) metadata.get("queryKy");  // 拼写错误,编译通过,运行时返回 null

// 问题 3: IDE 无法自动补全
metadata.get("???");  // IDE 不知道有哪些 key
```

**✅ 使用继承的优势**:
```java
// 优势 1: 编译时类型安全
PubmedPlanMetadata pubmedPlan = (PubmedPlanMetadata) plan;
String webEnv = pubmedPlan.webEnv();  // 编译时检查,无需类型转换

// 优势 2: IDE 友好
pubmedPlan.  // IDE 自动补全 webEnv(), queryKey()

// 优势 3: 重构安全
// 重命名字段时,IDE 可以自动更新所有引用

// 优势 4: 表达清晰的类型关系
PlanMetadata plan = dataSourcePort.preparePlan(...);
if (plan instanceof PubmedPlanMetadata pubmedPlan) {
    // 编译器保证 pubmedPlan 有 webEnv() 和 queryKey()
}
```

### 5. 可选字段
- **null 安全**: 使用 Java 类型系统表达可选性
- **字段如 `journal`、`publicationDate`**: 可为 null,表示数据不可用
- **列表字段**: 使用空列表而非 null

---

## 变更策略

### 版本管理
- **向后兼容**: 新增字段使用可选类型(允许 null)
- **破坏性变更**: 必须通知所有消费者,协调升级
- **弃用流程**: 标记 `@Deprecated` → 迁移期 → 移除

### 扩展建议
- **添加新字段**: 使用可选类型,提供默认值
- **修改现有字段**: 创建新版本模型(`CanonicalPublicationV2`)
- **删除字段**: 必须确保无消费者依赖
- **新增数据源**: 创建新的 PlanMetadata 子类(如 `CrossrefPlanMetadata`)

### 示例 1: 添加新字段

```java
@Value
@Builder
@Jacksonized
public class CanonicalPublication {
    String title;
    String abstractText;
    // ... 现有字段

    // 新增字段(向后兼容)
    @Builder.Default
    List<String> citations = List.of();  // 默认空列表

    @Builder.Default
    String fullTextUrl = null;  // 可选字段
}
```

### 示例 2: 新增数据源

```java
// 新增 Crossref 数据源的 PlanMetadata
public class CrossrefPlanMetadata extends PlanMetadata {
    private final String cursor;
    private final int pageSize;

    public CrossrefPlanMetadata(int totalCount, String cursor, int pageSize) {
        super("crossref", totalCount);
        this.cursor = cursor;
        this.pageSize = pageSize;
    }

    @Override
    public boolean hasSessionToken() {
        return cursor != null && !cursor.isBlank();
    }

    public String cursor() {
        return cursor;
    }

    public int pageSize() {
        return pageSize;
    }
}
```

---

## 架构优势

### 1. 解耦服务间依赖
- 服务不直接依赖彼此的内部模型
- 通过共享契约交换数据
- 降低变更影响范围

### 2. 统一数据理解
- 所有服务对"文献"、"计划元数据"概念有一致理解
- 减少数据转换错误
- 提高开发效率

### 3. 易于扩展
- 新增服务可直接使用现有模型
- 新增数据源只需创建新的 PlanMetadata 子类
- 无需重复定义相同概念
- 降低维护成本

### 4. 测试友好
- 不可变对象易于断言
- Builder 模式方便构造测试数据
- 清晰的数据契约

### 5. 类型安全 (PlanMetadata 继承体系)
- 编译时检查,避免运行时错误
- IDE 自动补全,提升开发效率
- 重构安全,减少维护成本

---

## 相关文档

- [patra-common/README.md](../README.md) — 多模块聚合器总览
- [patra-common-core/README.md](../patra-common-core/README.md) — 核心基础设施
- [patra-common-storage/README.md](../patra-common-storage/README.md) — 存储键生成策略
- [patra-ingest/README.md](../../patra-ingest/README.md) — 数据采集服务
- [patra-ingest-domain/README.md](../../patra-ingest/patra-ingest-domain/README.md) — 端口接口定义
- [patra-spring-boot-starter-provenance/README.md](../../patra-spring-boot-starter-provenance/README.md) — Provenance 适配器
- [统一数据源端口设计](../../dev/docs/统一数据源端口设计/README.md) — 架构设计方案

---

**Maven 坐标**: `com.patra:patra-common-model`
**版本**: 0.1.0-SNAPSHOT
**最后更新**: 2025-11-13
