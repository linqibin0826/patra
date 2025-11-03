# patra-common-model — 共享数据模型

> **跨服务共享的标准化数据模型模块**,提供 Shared Kernel 契约,支持服务间数据交换和通信。

---

## 概述

`patra-common-model` 是 Papertrace 平台的共享数据模型模块,采用 DDD 的 **Shared Kernel** 模式,定义跨服务共享的核心数据结构。本模块提供的模型作为服务间的契约,确保数据交换的一致性和兼容性。

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
    └── StandardLiterature           (标准化文献模型)
        ├── StandardLiterature       (主模型)
        ├── StandardAuthor           (作者快照)
        └── StandardJournal          (期刊快照)
```

---

## 主要组件

### StandardLiterature — 标准化文献模型

跨服务共享的规范化文献数据结构,作为采集、存储、检索服务之间的数据交换契约。

**设计定位**: Shared Kernel — 多个服务共享的核心领域模型

**核心字段**:
- `title`: 文献标题(String)
- `abstractText`: 摘要或总结文本(String)
- `authors`: 作者列表(`List<StandardAuthor>`,保持呈现顺序)
- `journal`: 期刊元数据(`StandardJournal`,可选)
- `identifiers`: 标识符映射(如 PMID、DOI、PMC,`Map<String, String>`)
- `publicationDate`: 发布日期(`LocalDate`,日精度,可选)
- `keywords`: 领域级关键词(`List<String>`)

**嵌套模型**:

#### StandardAuthor
作者快照,与 Catalog 契约需求对齐:
- `lastName`: 姓氏
- `foreName`: 名字
- `affiliation`: 所属机构

#### StandardJournal
期刊快照,与 Catalog 契约需求对齐:
- `title`: 期刊名称
- `issn`: 国际标准期刊号
- `publisher`: 出版商

---

## 依赖关系

**上游依赖**:
- `jackson-databind`: JSON 序列化支持
- `jackson-datatype-jsr310`: Java 8 日期时间支持

**下游消费者**:
- **patra-ingest-domain**: 端口接口定义(文献适配器返回类型)
- **patra-ingest-app**: 应用服务编排(跨 Provenance 适配器的统一返回类型)
- **patra-spring-boot-starter-provenance**: Provenance 适配器实现(PubMed、EPMC 等)
- 未来: `patra-catalog`、`patra-search` 等下游服务

---

## 使用示例

### Maven 依赖

```xml
<dependency>
    <groupId>com.papertrace</groupId>
    <artifactId>patra-common-model</artifactId>
</dependency>
```

### 创建标准化文献

```java
import com.patra.common.model.StandardLiterature;
import com.patra.common.model.StandardLiterature.StandardAuthor;
import com.patra.common.model.StandardLiterature.StandardJournal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

StandardLiterature literature = StandardLiterature.builder()
    .title("Deep Learning in Medical Image Analysis")
    .abstractText("This study presents a comprehensive review...")
    .authors(List.of(
        StandardAuthor.builder()
            .lastName("Smith")
            .foreName("John")
            .affiliation("Stanford University")
            .build(),
        StandardAuthor.builder()
            .lastName("Doe")
            .foreName("Jane")
            .affiliation("MIT")
            .build()
    ))
    .journal(StandardJournal.builder()
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

### 序列化与反序列化

```java
import com.fasterxml.jackson.databind.ObjectMapper;

ObjectMapper mapper = new ObjectMapper();

// 序列化为 JSON
String json = mapper.writeValueAsString(literature);

// 反序列化为对象
StandardLiterature deserialized = mapper.readValue(json, StandardLiterature.class);
```

### 在领域层使用(端口接口)

```java
package com.patra.ingest.domain.port;

import com.patra.common.model.StandardLiterature;
import java.util.Optional;

/**
 * Provenance 适配器端口接口
 */
public interface ProvenanceAdapter {
    /**
     * 根据外部 ID 获取标准化文献数据
     */
    Optional<StandardLiterature> fetchLiterature(String externalId);
}
```

### 在应用层使用(编排)

```java
package com.patra.ingest.app.service;

import com.patra.common.model.StandardLiterature;
import com.patra.ingest.domain.port.ProvenanceAdapter;

public class LiteratureIngestionService {
    private final ProvenanceAdapter provenanceAdapter;

    public StandardLiterature ingestFromPubMed(String pmid) {
        return provenanceAdapter.fetchLiterature(pmid)
            .orElseThrow(() -> new LiteratureNotFoundException(pmid));
    }
}
```

### 在基础设施层使用(适配器实现)

```java
package com.patra.starter.provenance.pubmed;

import com.patra.common.model.StandardLiterature;
import com.patra.ingest.domain.port.ProvenanceAdapter;

public class PubMedAdapter implements ProvenanceAdapter {
    @Override
    public Optional<StandardLiterature> fetchLiterature(String pmid) {
        // 1. 调用 PubMed API
        PubMedArticle article = pubMedClient.fetchArticle(pmid);

        // 2. 转换为 StandardLiterature
        StandardLiterature standardized = convertToStandard(article);

        return Optional.of(standardized);
    }

    private StandardLiterature convertToStandard(PubMedArticle article) {
        return StandardLiterature.builder()
            .title(article.getTitle())
            .abstractText(article.getAbstractText())
            .authors(convertAuthors(article.getAuthors()))
            .journal(convertJournal(article.getJournal()))
            .identifiers(Map.of("PMID", article.getPmid()))
            .publicationDate(article.getPublicationDate())
            .keywords(article.getKeywords())
            .build();
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

### 4. 可选字段
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
- **修改现有字段**: 创建新版本模型(`StandardLiteratureV2`)
- **删除字段**: 必须确保无消费者依赖

### 示例: 添加新字段

```java
@Value
@Builder
@Jacksonized
public class StandardLiterature {
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

---

## 架构优势

### 1. 解耦服务间依赖
- 服务不直接依赖彼此的内部模型
- 通过共享契约交换数据
- 降低变更影响范围

### 2. 统一数据理解
- 所有服务对"文献"概念有一致理解
- 减少数据转换错误
- 提高开发效率

### 3. 易于扩展
- 新增服务可直接使用现有模型
- 无需重复定义相同概念
- 降低维护成本

### 4. 测试友好
- 不可变对象易于断言
- Builder 模式方便构造测试数据
- 清晰的数据契约

---

## 相关文档

- [patra-common/README.md](../README.md) — 多模块聚合器总览
- [patra-common-core/README.md](../patra-common-core/README.md) — 核心基础设施
- [patra-common-storage/README.md](../patra-common-storage/README.md) — 存储键生成策略
- [patra-ingest/README.md](../../patra-ingest/README.md) — 数据采集服务
- [patra-spring-boot-starter-provenance/README.md](../../patra-spring-boot-starter-provenance/README.md) — Provenance 适配器

---

**Maven 坐标**: `com.papertrace:patra-common-model`
**版本**: 0.1.0-SNAPSHOT
**最后更新**: 2025-11-03
