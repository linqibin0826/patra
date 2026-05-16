# Port 与 Service 详细指南

> 命名规范表和选择指南见 `rules/tech/port-service.md`（自动加载）。
> 本文档提供各 Port 类型的详细说明、依赖方向图、注入方式和目录结构示例。

## Repository（仓储）

**职责**：聚合根的持久化与检索

```
Domain 层                          Infra 层
┌─────────────────────┐           ┌─────────────────────────┐
│ PublicationRepository│ ←─────── │ PublicationRepositoryAdapter │
│     (interface)     │           │    (implementation)      │
└─────────────────────┘           └─────────────────────────┘
```

**位置**：
- 接口：`{service}-domain/.../domain/port/repository/{Entity}Repository.java`
- 实现：`{service}-infra/.../infra/adapter/persistence/{Entity}RepositoryAdapter.java`

## Driven Port（被驱动端口）

**职责**：定义领域层需要的外部能力（解析、下载、批处理等）

```
Domain 层                          Infra 层
┌─────────────────────┐           ┌─────────────────────────┐
│ PubmedXmlParserPort │ ←─────── │ PubmedXmlParserAdapter  │
│     (interface)     │           │    (implementation)      │
└─────────────────────┘           └─────────────────────────┘
```

**位置**：
- 接口：`{service}-domain/.../domain/port/{subdir}/{Function}Port.java`
- 实现：`{service}-infra/.../infra/adapter/{subdir}/{Function}Adapter.java`

## LookupPort（查找端口）

**职责**：实体查找能力（ID 匹配、缓存优化），装饰器模式三层实现

```
Domain 层                          Infra 层
┌─────────────────────┐           ┌─────────────────────────────┐
│ VenueLookupPort     │           │ DefaultVenueLookupAdapter   │ ← 无缓存，直接查库
│     (interface)     │ ←─────── │ CachingVenueLookupDecorator │ ← 缓存装饰器
│                     │           │ BatchVenueLookupAdapter     │ ← @StepScope 批处理专用
└─────────────────────┘           └─────────────────────────────┘
```

**使用场景**：
- 批处理中频繁查询实体 ID（Venue、Funder、Language）
- 需要多种标识符匹配（NLM ID、ISSN、FundRef ID、ROR ID）

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

**位置**：
- 接口：`{service}-domain/.../domain/port/lookup/{Entity}LookupPort.java`
- 默认实现：`{service}-infra/.../infra/adapter/lookup/Default{Entity}LookupAdapter.java`
- 缓存装饰器：`{service}-infra/.../infra/adapter/lookup/Caching{Entity}LookupDecorator.java`
- 批处理实现：`{service}-infra/.../infra/adapter/lookup/Batch{Entity}LookupAdapter.java`

## ReadPort（CQRS 读端口）

**职责**：面向查询场景的分页/检索能力

**与 Repository 的区别**：
- Repository：聚合根持久化与检索（写端 + 聚合维度查询）
- ReadPort：仅提供查询投影（返回 Read Model，非聚合根）

**位置**：
- 接口：`{service}-domain/.../domain/port/read/{Entity}ReadPort.java`
- 实现：`{service}-infra/.../infra/adapter/read/{Entity}ReadAdapter.java`

## Gateway（驱动端口）

**职责**：领域层/Infra 层需要调用应用层服务

**依赖方向**：Domain → (interface) → App（反转通常的依赖方向）

**使用场景**：
- Spring Batch Processor 需要 findOrCreate 语义
- 需要独立事务管理的跨聚合协调

**位置**：
- 接口：`{service}-domain/.../domain/port/gateway/{Entity}Gateway.java`
- 实现：`{service}-app/.../app/usecase/{domain}/service/{Entity}GatewayImpl.java`

## 目录结构示例

```
patra-catalog/
├── patra-catalog-domain/
│   └── domain/port/
│       ├── repository/          # 仓储接口
│       ├── read/                # CQRS 读端口
│       ├── gateway/             # 驱动端口接口
│       ├── lookup/              # 查找端口接口
│       ├── parser/              # 解析器端口
│       ├── batch/               # 批处理端口
│       ├── enrichment/          # 富化端口
│       ├── source/              # 数据源端口
│       └── registry/            # 注册中心端口
│
├── patra-catalog-app/
│   └── app/usecase/
│       ├── publication/
│       │   ├── service/         # Gateway 实现
│       │   └── query/           # QueryService
│       └── venue/
│           └── query/
│
└── patra-catalog-infra/
    └── infra/
        ├── adapter/             # 端口适配器
        │   ├── persistence/     # 仓储实现
        │   ├── lookup/          # 查找端口实现（含缓存装饰器）
        │   ├── read/            # CQRS 读适配器
        │   ├── integration/     # 外部服务集成
        │   └── source/          # 数据源适配器
        ├── batch/               # Spring Batch 子系统
        ├── parser/              # XML 解析子系统
        └── persistence/         # JPA 内部实现（非端口）
```
