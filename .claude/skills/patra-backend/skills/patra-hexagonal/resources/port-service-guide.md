# Port 与 Service 详细指南

> 命名规范表和选择指南见 `.claude/rules/tech/port-service.md`（自动加载）。
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
- 接口：`{service}-domain/.../domain/{子域}/port/repository/{Entity}Repository.java`
- 实现：`{service}-infra/.../infra/{子域}/persistence/{Entity}RepositoryAdapter.java`

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
- 接口：`{service}-domain/.../domain/{子域}/port/{协作者}/{Function}Port.java`
- 实现：`{service}-infra/.../infra/{子域}/{协作者}/{Function}Adapter.java`（包名与端口包同名）

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
- 接口：`{service}-domain/.../domain/{子域}/port/lookup/{Entity}LookupPort.java`（被其他子域用时放 `port/read/`）
- 默认实现：`{service}-infra/.../infra/{子域}/lookup/Default{Entity}LookupAdapter.java`（端口在 `port/read/` 时放 `persistence/`）
- 缓存装饰器：同上目录的 `Caching{Entity}LookupDecorator.java`
- 批处理实现：同上目录的 `Batch{Entity}LookupAdapter.java`

## ReadPort（CQRS 读端口）

**职责**：面向查询场景的分页/检索能力

**与 Repository 的区别**：
- Repository：聚合根持久化与检索（写端 + 聚合维度查询）
- ReadPort：仅提供查询投影（返回 Read Model，非聚合根）

**位置**：
- 接口：`{service}-domain/.../domain/{子域}/port/read/{Entity}ReadPort.java`
- 实现：`{service}-infra/.../infra/{子域}/persistence/{Entity}ReadAdapter.java`

## Gateway（驱动端口）

**职责**：领域层/Infra 层需要调用应用层服务

**依赖方向**：Domain → (interface) → App（反转通常的依赖方向）

**使用场景**：
- Spring Batch Processor 需要 findOrCreate 语义
- 需要独立事务管理的跨聚合协调

**位置**：
- 接口：`{service}-domain/.../domain/{子域}/port/gateway/{Entity}Gateway.java`
- 实现：`{service}-app/.../app/{子域}/service/{Entity}GatewayImpl.java`

## 目录结构示例

各层先按子域、再按组件类型分包，完整模板见 `.claude/rules/tech/package-layout.md`。以 catalog 的 venue 子域为例：

```
patra-catalog-domain/  domain/venue/
    model/{aggregate,vo,enums,read}
    port/{repository,read,enrichment,parser,storage}
patra-catalog-app/     app/venue/
    usecase/{pubmed,letpub,scopus,query}     # 写用例三件套同包；query 放 QueryService
    service/                                 # GatewayImpl、多个用例复用的类
    config/
patra-catalog-infra/   infra/venue/
    persistence/{entity,dao,mapper,converter}  # RepositoryAdapter、ReadAdapter、查找适配器
    enrichment/  parser/  storage/             # 与 domain 端口包同名
patra-catalog-adapter/ adapter/venue/
    rest/  internal/  scheduler/  config/
```
