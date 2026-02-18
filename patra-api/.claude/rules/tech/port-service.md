# Port 与 Service 命名规范

## 概述

本规范定义了六边形架构中各类端口（Port）和服务（Service）的命名约定，确保接口命名能准确反映其职责和依赖方向。

## 命名规范表

| 类型 | 定义层 | 实现层 | 接口命名 | 实现命名 | 说明 |
|------|--------|--------|----------|----------|------|
| Repository | Domain | Infra | `{Entity}Repository` | `{Entity}RepositoryAdapter` | 聚合根持久化 |
| Driven Port | Domain | Infra | `{Function}Port` | `{Function}Adapter` | 被驱动端口（外部调用） |
| LookupPort | Domain | Infra | `{Entity}LookupPort` | `Default{Entity}LookupAdapter` + `Caching{Entity}LookupDecorator` + `Batch{Entity}LookupAdapter` | 查找端口（带缓存装饰器） |
| ReadPort | Domain | Infra | `{Entity}ReadPort` | `{Entity}ReadAdapter` | CQRS 读端口 |
| Driving Port | Domain | App | `{Entity}Gateway` | `{Entity}GatewayImpl` | 驱动端口（调用应用层） |
| QueryService | - | App | 无接口 | `{Domain}QueryService` | CQRS 查询服务 |

## 详细说明

### Repository（仓储）

**职责**：聚合根的持久化与检索

**依赖方向**：Domain → (interface) → Infra

```
Domain 层                          Infra 层
┌─────────────────────┐           ┌─────────────────────────┐
│ PublicationRepository│ ←─────── │ PublicationRepositoryAdapter │
│     (interface)     │           │    (implementation)      │
└─────────────────────┘           └─────────────────────────┘
```

**命名示例**：
- `PublicationRepository` → `PublicationRepositoryAdapter`
- `VenueRepository` → `VenueRepositoryAdapter`
- `AuthorRepository` → `AuthorRepositoryAdapter`

**位置**：
- 接口：`{service}-domain/src/main/java/.../domain/port/repository/{Entity}Repository.java`
- 实现：`{service}-infra/src/main/java/.../infra/adapter/persistence/{Entity}RepositoryAdapter.java`

### Driven Port（被驱动端口）

**职责**：定义领域层需要的外部能力（解析、下载、批处理等）

**依赖方向**：Domain → (interface) → Infra

```
Domain 层                          Infra 层
┌─────────────────────┐           ┌─────────────────────────┐
│ PubmedXmlParserPort │ ←─────── │ PubmedXmlParserAdapter  │
│     (interface)     │           │    (implementation)      │
└─────────────────────┘           └─────────────────────────┘
```

**命名示例**：
- `PubmedXmlParserPort` → `PubmedXmlParserAdapter`
- `FileDownloadPort` → `FileDownloadAdapter`
- `PublicationBatchPort` → `PublicationBatchAdapter`

**位置**：
- 接口：`{service}-domain/src/main/java/.../domain/port/{subdir}/{Function}Port.java`
- 实现：`{service}-infra/src/main/java/.../infra/adapter/{subdir}/{Function}Adapter.java`

### LookupPort（查找端口）

**职责**：定义领域层需要的实体查找能力（ID 匹配、缓存优化）

**依赖方向**：Domain → (interface) → Infra

**实现模式**：装饰器模式，三层实现结构

```
Domain 层                          Infra 层
┌─────────────────────┐           ┌─────────────────────────────┐
│ VenueLookupPort     │           │ DefaultVenueLookupAdapter   │ ← 无缓存，直接查库
│     (interface)     │ ←─────── │ CachingVenueLookupDecorator │ ← 缓存装饰器（手动实例化）
│                     │           │ BatchVenueLookupAdapter     │ ← @StepScope 批处理专用
└─────────────────────┘           └─────────────────────────────┘
```

**使用场景**：
- 批处理中频繁查询实体 ID（如 Venue、Funder、Language）
- 需要多种标识符匹配（如 NLM ID、ISSN、FundRef ID、ROR ID）
- API 单次查询（使用 DefaultAdapter，无缓存）
- Spring Batch Step 级别缓存（使用 BatchAdapter）

**命名示例**：
- `VenueLookupPort` → `DefaultVenueLookupAdapter` + `CachingVenueLookupDecorator` + `BatchVenueLookupAdapter`
- `FunderLookupPort` → `DefaultFunderLookupAdapter` + `CachingFunderLookupDecorator` + `BatchFunderLookupAdapter`
- `LanguageLookupPort` → `DefaultLanguageLookupAdapter` + `CachingLanguageLookupDecorator` + `BatchLanguageLookupAdapter`

**位置**：
- 接口：`{service}-domain/src/main/java/.../domain/port/lookup/{Entity}LookupPort.java`
- 默认实现：`{service}-infra/src/main/java/.../infra/adapter/lookup/Default{Entity}LookupAdapter.java`
- 缓存装饰器：`{service}-infra/src/main/java/.../infra/adapter/lookup/Caching{Entity}LookupDecorator.java`
- 批处理实现：`{service}-infra/src/main/java/.../infra/adapter/lookup/Batch{Entity}LookupAdapter.java`

**注入方式**：
```java
// API 场景：直接注入默认实现
@Autowired
private FunderLookupPort funderLookupPort; // DefaultFunderLookupAdapter

// 批处理场景：通过 @Qualifier 注入批处理实现
@Bean
@StepScope
public PubmedArticleItemProcessor processor(
    @Qualifier("batchFunderLookupAdapter") FunderLookupPort funderLookupPort) {
  return new PubmedArticleItemProcessor(..., funderLookupPort);
}
```

### ReadPort（CQRS 读端口）

**职责**：CQRS 读端驱动端口，提供面向查询场景的分页/检索能力

**依赖方向**：Domain → (interface) → Infra

```
Domain 层                          Infra 层
┌─────────────────────┐           ┌─────────────────────────┐
│ VenueReadPort       │ ←─────── │ VenueReadAdapter        │
│     (interface)     │           │    (implementation)      │
└─────────────────────┘           └─────────────────────────┘
```

**命名示例**：
- `VenueReadPort` → `VenueReadAdapter`
- `PublicationReadPort` → `PublicationReadAdapter`
- `AuthorReadPort` → `AuthorReadAdapter`

**位置**：
- 接口：`{service}-domain/src/main/java/.../domain/port/read/{Entity}ReadPort.java`
- 实现：`{service}-infra/src/main/java/.../infra/adapter/read/{Entity}ReadAdapter.java`

**与 Repository 的区别**：
- Repository 负责聚合根的持久化与检索（写端 + 聚合维度查询）
- ReadPort 不持久化聚合根，仅提供查询投影（返回 Read Model，非聚合根）
- ReadPort 的返回值是 `PageResult<{Entity}SummaryReadModel>` 等只读投影

### Driving Port / Gateway（驱动端口）

**职责**：领域层/基础设施层需要调用应用层服务的场景

**依赖方向**：Domain → (interface) → App（反转通常的依赖方向）

```
Domain 层                          App 层
┌─────────────────────┐           ┌─────────────────────────┐
│ VenueInstanceGateway│ ←─────── │ VenueInstanceGatewayImpl│
│     (interface)     │           │    (implementation)      │
└─────────────────────┘           └─────────────────────────┘
```

**使用场景**：
- Infra 层组件（如 Spring Batch Processor）需要调用涉及业务逻辑的服务
- 需要独立事务管理的 findOrCreate 语义
- 跨聚合的协调操作（但不适合放在 Handler 中）

**命名示例**：
- `VenueInstanceGateway` → `VenueInstanceGatewayImpl`
- `AuthorGateway` → `AuthorGatewayImpl`

**位置**：
- 接口：`{service}-domain/src/main/java/.../domain/port/gateway/{Entity}Gateway.java`
- 实现：`{service}-app/src/main/java/.../app/usecase/{domain}/service/{Entity}GatewayImpl.java`

### QueryService（查询服务）

**职责**：CQRS 读端服务，提供查询功能

**特点**：无接口定义，直接在 App 层实现

```
App 层
┌─────────────────────────┐
│ VenueQueryService       │
│ (concrete class)        │
└─────────────────────────┘
```

**命名示例**：
- `VenueQueryService`
- `PublicationQueryService`
- `AuthorQueryService`

**位置**：
- `{service}-app/src/main/java/.../app/usecase/{domain}/query/{Domain}QueryService.java`

## 目录结构示例

```
patra-catalog/
├── patra-catalog-domain/
│   └── src/main/java/.../domain/port/
│       ├── repository/                     # 仓储接口（按类型分组）
│       │   ├── PublicationRepository.java
│       │   └── VenueRepository.java
│       ├── read/                           # CQRS 读端口
│       │   └── VenueReadPort.java
│       ├── gateway/                        # 驱动端口接口
│       │   └── VenueInstanceGateway.java
│       ├── lookup/                         # 查找端口接口
│       │   ├── VenueLookupPort.java
│       │   ├── FunderLookupPort.java
│       │   └── LanguageLookupPort.java
│       ├── parser/                         # 解析器端口
│       │   └── PubmedXmlParserPort.java
│       ├── batch/                          # 批处理端口
│       │   └── PublicationBatchPort.java
│       ├── enrichment/                     # 富化端口
│       │   └── WikidataEnrichmentQueryPort.java
│       ├── source/                         # 数据源端口
│       │   └── FileDownloadPort.java
│       └── registry/                       # 注册中心端口
│           └── DictionaryResolverPort.java
│
├── patra-catalog-app/
│   └── src/main/java/.../app/usecase/
│       ├── publication/
│       │   ├── service/
│       │   │   └── VenueInstanceGatewayImpl.java  # Gateway 实现
│       │   └── query/
│       │       └── PublicationQueryService.java   # QueryService
│       └── venue/
│           └── query/
│               └── VenueQueryService.java
│
└── patra-catalog-infra/
    └── src/main/java/.../infra/
        ├── adapter/                        # 端口适配器（仅端口实现）
        │   ├── persistence/                # 仓储实现
        │   │   ├── PublicationRepositoryAdapter.java
        │   │   └── VenueRepositoryAdapter.java
        │   ├── lookup/                     # 查找端口实现（含缓存装饰器）
        │   │   ├── DefaultVenueLookupAdapter.java
        │   │   ├── CachingVenueLookupDecorator.java
        │   │   ├── BatchVenueLookupAdapter.java
        │   │   ├── DefaultFunderLookupAdapter.java
        │   │   ├── CachingFunderLookupDecorator.java
        │   │   └── BatchFunderLookupAdapter.java
        │   ├── read/                       # CQRS 读适配器
        │   │   └── VenueReadAdapter.java
        │   ├── integration/                # 外部服务集成
        │   │   ├── DictionaryResolverAdapter.java
        │   │   └── wikidata/              # Wikidata 集成
        │   │       ├── WikidataSparqlClient.java
        │   │       └── WikidataEnrichmentQueryAdapter.java
        │   └── source/                     # 数据源适配器
        │       └── FileDownloadAdapter.java
        ├── batch/                          # Spring Batch 子系统
        │   └── publication/
        │       └── PublicationBatchAdapter.java
        ├── parser/                         # XML 解析子系统
        │   └── PubmedXmlParserAdapter.java
        └── persistence/                    # JPA 内部实现（非端口）
            ├── entity/
            ├── dao/
            └── converter/
```

## 选择指南

```
需要定义的接口是什么类型？
│
├── 聚合根持久化 ─────────→ Repository（Domain → Infra）
│
├── 外部能力（解析/下载/批处理等）
│   └─────────────────────→ {Function}Port（Domain → Infra）
│
├── 实体 ID 查找（需要缓存优化）
│   └─────────────────────→ {Entity}LookupPort（Domain → Infra，装饰器模式）
│
├── CQRS 读端查询（分页/检索投影）
│   └─────────────────────→ {Entity}ReadPort（Domain → Infra）
│
├── 应用层服务供其他层调用
│   └─────────────────────→ {Entity}Gateway（Domain → App）
│
└── 纯查询服务 ────────────→ {Domain}QueryService（App 层直接实现）
```

## 禁止行为

1. **禁止** Repository 使用 `Port` 后缀（如 ~~`PublicationPort`~~）
2. **禁止** 驱动端口使用 `Port` 后缀（应使用 `Gateway`）
3. **禁止** QueryService 定义接口（CQRS 读端不需要抽象）
4. **禁止** 在 Domain 层定义接口但在 Domain 层实现
