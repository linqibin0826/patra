# DDD 战略模式

本文档基于 Eric Evans 的《Domain-Driven Design》（蓝皮书）和 Vaughn Vernon 的《DDD Distilled》总结 DDD 战略模式。

## 限界上下文 (Bounded Context)

### 定义

限界上下文是一个显式的边界，在这个边界内，领域模型的含义是明确且一致的。同一个词在不同上下文中可能有不同含义。

### 识别方法

1. **语言边界**：当同一术语开始有不同含义时
2. **团队边界**：不同团队负责的系统部分
3. **业务能力**：围绕核心业务能力划分
4. **数据一致性**：需要强一致性的数据放在一起

### 医学领域示例

| 上下文 | 核心概念 | 说明 |
|--------|---------|------|
| 目录上下文 (Catalog) | Publication, Venue, Author | 文献元数据管理 |
| 采集上下文 (Ingest) | Plan, Task, Job | 数据采集调度 |
| 注册上下文 (Registry) | Provenance, Expression | 配置和元数据 |
| 检索上下文 (Search) | Document, Index | 搜索和发现 |

### 上下文内的"期刊"含义

| 上下文 | "期刊"的含义 | 核心关注点 |
|--------|------------|-----------|
| Catalog | `VenueAggregate` | 完整的期刊元数据、标识符、指标 |
| Ingest | `TargetVenue` | 采集目标、数据源配置 |
| Search | `VenueDocument` | 可搜索的期刊索引 |

## 上下文映射 (Context Map)

### 定义

上下文映射描述不同限界上下文之间的关系和集成方式。

### 关系类型

| 关系 | 说明 | 适用场景 |
|------|------|---------|
| **Shared Kernel** | 共享一部分模型 | 紧密协作的团队 |
| **Customer-Supplier** | 上游供应、下游消费 | 明确的数据流向 |
| **Conformist** | 下游完全遵从上游模型 | 无力影响上游 |
| **Anti-corruption Layer** | 下游隔离上游模型 | 保护领域模型纯净 |
| **Open Host Service** | 上游提供标准化服务 | 多下游消费 |
| **Published Language** | 共享的交换格式 | 跨系统集成 |
| **Separate Ways** | 各自独立 | 无集成需求 |
| **Partnership** | 紧密合作，共同演进 | 相互依赖的团队 |

### Patra 上下文映射

```
┌─────────────────────────────────────────────────────────┐
│                     外部数据源                           │
│   PubMed    OpenAlex    Crossref    DOAJ    ...        │
└────────────────────────┬────────────────────────────────┘
                         │ ACL (防腐层)
                         ↓
┌─────────────────────────────────────────────────────────┐
│                   Ingest Context                         │
│   Plan ←→ Task ←→ Job                                   │
└────────────────────────┬────────────────────────────────┘
                         │ OHS (开放主机服务)
                         ↓
┌─────────────────────────────────────────────────────────┐
│                   Catalog Context                        │
│   Publication ←→ Venue ←→ Author ←→ MeSH               │
└────────────────────────┬────────────────────────────────┘
                         │ PL (发布语言)
                         ↓
┌─────────────────────────────────────────────────────────┐
│                   Search Context                         │
│   PublicationDoc ←→ VenueDoc ←→ AuthorDoc              │
└─────────────────────────────────────────────────────────┘

Registry Context ──────→ 所有上下文 (Shared Kernel)
```

### 关系实现模式

#### Anti-corruption Layer (ACL)

保护领域模型不被外部模型污染：

```java
// 外部模型（OpenAlex）
public record OpenAlexSource(
    String id,
    String display_name,
    String type,
    List<String> issn
) {}

// ACL 转换层
public class OpenAlexVenueAdapter {
    public VenueAggregate toVenue(OpenAlexSource source) {
        return VenueAggregate.fromOpenAlex(
            source.id(),
            VenueType.fromCode(source.type()),
            source.display_name()
        ).withIdentifiers(extractIdentifiers(source));
    }
}
```

#### Open Host Service (OHS)

提供标准化的服务接口：

```java
// API 模块定义服务契约
public interface VenueApi {
    @GetMapping("/api/v1/venues/{id}")
    VenueDTO getById(@PathVariable Long id);

    @GetMapping("/api/v1/venues")
    PageResult<VenueDTO> search(VenueSearchRequest request);
}
```

#### Published Language (PL)

定义共享的数据格式：

```java
// common-model 模块定义共享模型
public record VenueRef(
    Long id,
    String name,
    String issnL,
    VenueType type
) {}
```

## 通用语言 (Ubiquitous Language)

### 定义

通用语言是团队（包括领域专家和开发人员）共同使用的语言，直接反映在代码中。

### 维护方法

1. **术语表**：维护领域术语定义
2. **代码即语言**：类名、方法名使用领域术语
3. **持续更新**：随着理解加深更新术语

### 医学领域术语表示例

| 术语 | 定义 | 代码表示 |
|------|------|---------|
| 载体 (Venue) | 文献发表的载体，如期刊、仓库 | `VenueAggregate` |
| 描述符 (Descriptor) | MeSH 主题词 | `MeshDescriptorAggregate` |
| 限定词 (Qualifier) | MeSH 副主题词 | `MeshQualifierAggregate` |
| 来源追踪 (Provenance) | 数据来源信息 | `ProvenanceInfo` |
| ISSN-L | 期刊 Linking ISSN | `issnL` 字段 |

## 共享内核 (Shared Kernel)

### 定义

多个上下文共享的一小部分模型，变更需要双方协调。

### Patra 共享内核

```
patra-common-core/
├── domain/
│   ├── AggregateRoot.java      # 聚合根基类
│   ├── Entity.java             # 实体基类
│   └── DomainException.java    # 领域异常基类
├── enums/
│   └── ProvenanceCode.java     # 数据源编码
└── util/
    └── Assert.java             # 断言工具

patra-common-model/
├── VenueRef.java               # 期刊引用
├── AuthorRef.java              # 作者引用
└── MeshRef.java                # MeSH 引用
```

### 使用注意事项

1. **最小化**：只放真正需要共享的内容
2. **稳定性**：变更需要慎重，影响多个上下文
3. **版本管理**：考虑使用版本号管理变更

## 上下文边界决策流程

```
1. 识别核心业务能力
   ↓
2. 分析团队结构和职责
   ↓
3. 识别通用语言差异
   ↓
4. 确定数据一致性需求
   ↓
5. 划分限界上下文边界
   ↓
6. 定义上下文间关系
   ↓
7. 设计集成模式
```

## 常见反模式

### 1. 大泥球 (Big Ball of Mud)

**症状**：所有概念混在一起，没有清晰边界

**解决**：识别边界，逐步拆分

### 2. 过度共享

**症状**：Shared Kernel 过大，变更困难

**解决**：减少共享，使用 ACL 隔离

### 3. 忽略语言差异

**症状**：同一术语在不同地方有不同含义

**解决**：明确定义每个上下文的术语

### 4. 强耦合集成

**症状**：上下文直接依赖对方内部模型

**解决**：使用 OHS + PL 或 ACL 解耦
