# patra-registry

## 概述

`patra-registry` 是 Patra 平台的**单一真实数据源(SSOT)**和配置中枢,负责管理所有数据源的元数据、运营配置、表达式元数据和系统字典。本服务采用六边形架构 + DDD 设计模式,通过时态配置机制确保配置变更的可追溯性和安全性。

其他微服务(特别是 `patra-ingest`)通过 Feign 客户端查询本服务,获取特定时间点的有效配置快照,实现配置的集中管理和一致性保障。

## 核心职责

- **数据源管理**: 维护外部数据源目录(PubMed、EPMC、Crossref 等)及其基础元数据
- **运营配置**: 管理 HTTP 策略、重试策略、速率限制、分页规则、批处理配置、时间窗口偏移等运营参数
- **表达式元数据**: 提供 API 参数映射、字段定义、渲染规则,支持 `patra-expr-kernel` 动态表达式编译
- **时态切片**: 通过 `effectiveFrom` 和 `effectiveUntil` 实现配置的时间有效性管理,支持安全的配置更新和审计
- **系统字典**: 管理统一的枚举代码、业务字典项及来源标准目录

## 模块结构

```
patra-registry/
├── patra-registry-boot/          # 可执行入口,组装所有模块
├── patra-registry-api/           # 外部契约(Feign 客户端、DTO、错误码)
├── patra-registry-domain/        # 纯 Java 领域模型(无框架依赖)
├── patra-registry-app/           # 应用层,用例编排
├── patra-registry-infra/         # 基础设施层,持久化实现
└── patra-registry-adapter/       # 适配器层,REST 端点实现
```

**六边形架构依赖方向**:
- `adapter` → `app` → `domain` ← `infra`
- `api` 为独立契约模块,由 `adapter` 实现
- `boot` 组装所有模块并提供可执行入口

详细的子模块说明请参阅各子模块的 README.md。

## 核心领域概念

### 1. Provenance(数据源)

外部数据源的核心元数据,包含唯一标识符(`code`)、显示名称、默认基础 URL、默认时区、激活状态和生命周期状态代码。

**领域对象**: `Provenance` (值对象)

### 2. ProvenanceConfiguration(聚合根)

只读聚合,将 `Provenance` 与所有运营配置组合成统一视图,包括:
- `WindowOffsetConfig`: 时间窗口分段配置
- `PaginationConfig`: 分页策略
- `HttpConfig`: HTTP 客户端设置
- `BatchingConfig`: 批处理规则
- `RetryConfig`: 重试策略
- `RateLimitConfig`: 速率限制

**作用域优先级**: TASK 级配置覆盖 SOURCE 级默认值。

### 3. 时态配置

所有运营配置(除 `Provenance` 本身)都具有时间有效性范围:
- `effectiveFrom`: 配置生效时间
- `effectiveUntil`: 配置失效时间

**查询模式**: 通过指定时间点(`at`)查询该时刻有效的配置,支持配置的安全更新、A/B 测试和审计轨迹。

### 4. 表达式元数据

支持动态 API 参数映射的元数据体系:
- **ExprCapability**: 数据源支持的操作能力(HARVEST、UPDATE 等)
- **ApiParamMapping**: 逻辑参数到 API 查询参数的映射规则
- **ExprField**: 字段定义(数据类型、约束、语义键)
- **ExprRenderRule**: 表达式渲染为 API 查询的转换规则

### 5. Dictionary（字典）

提供字典值的统一解析服务，支持将外部系统的代码映射到内部标准代码:

- **DictionaryType**: 字典类型(如 `country`、`language`)
- **DictionaryItem**: 字典项(如 `CN=中国`、`US=美国`)
- **DictionaryItemAlias**: 字典项别名，实现外部代码到内部代码的映射

**解析策略**（按优先级）:
1. 直接匹配: `rawValue` 作为 `itemCode` 直接查询
2. 别名匹配: 通过 `sourceStandard` + `externalCode` 查询别名表

**领域异常**:
- `DictionaryTypeNotFoundException`: 字典类型不存在
- `DictionaryStandardNotFoundException`: 来源标准不存在
- `DictionaryStandardDisabledException`: 来源标准已禁用

### 6. ReferenceStandard（来源标准）

管理外部值遵循的格式规范:
- **ReferenceStandard**: 来源标准值对象(如 `ISO_3166_1_ALPHA2`、`NAME_EN`)
- 支持启用/禁用状态管理
- 通过 `DictionaryItemAlias.sourceStandard` 关联字典项

## 主要 API 契约

### ProvenanceEndpoint (内部 RPC)

**基础路径**: `/_internal/provenances`

**核心端点**:
- `GET /_internal/provenances` - 列出所有数据源
- `GET /_internal/provenances/{code}` - 获取单个数据源
- `GET /_internal/provenances/{code}/config` - 加载完整配置聚合(支持时态切片)

### ExprEndpoint (内部 RPC)

**基础路径**: `/_internal/expr`

**核心端点**:
- `GET /_internal/expr/snapshot` - 获取完整表达式快照(支持时态切片)

### DictionaryEndpoint (内部 RPC)

**基础路径**: `/_internal/dictionaries`

**核心端点**:
- `POST /_internal/dictionaries/resolve` - 批量解析字典值(支持 `sourceStandard` 可选,缺省使用 `GLOBAL`)

## 依赖关系

**上游依赖**:
- `patra-common-core`: 共享枚举、工具类
- `patra-spring-boot-starter-*`: 统一的 Spring Boot 配置

**下游消费者**:
- `patra-ingest`: 通过 Feign 客户端查询数据源配置
- `patra-catalog`: 通过 `DictionaryClient` 解析国家编码（ISO 3166-1 alpha-2 标准化）
- 其他微服务: 通过 `patra-registry-api` 模块引入客户端

## 技术栈

| 组件 | 版本/说明 |
|------|---------|
| **Java** | 25 |
| **Spring Boot** | 3.5.7 |
| **Spring Cloud** | 2025.0.0 |
| **Spring Data JPA** | 持久化框架 |
| **MapStruct** | 对象映射 |
| **Nacos** | 服务注册与配置中心 |
| **Maven** | 构建工具 |

## 配置说明

### 应用配置 (application.yml)

```yaml
spring:
  application:
    name: patra-registry

server:
  port: 8081

patra:
  logging:
    trace:
      enabled: true
```

### 数据库配置

通过 Nacos 配置中心动态管理,主要配置项包括:
- 数据源连接信息
- JPA/Hibernate 配置
- 日志级别

## 本地开发

### 启动服务

```bash
# 进入 boot 模块
cd patra-registry-boot

# 启动应用
../../mvnw spring-boot:run
```

**默认端口**: 8081

### 数据库迁移

Flyway 自动执行数据库迁移,种子数据包括:
- 数据源元数据
- 表达式配置(字段、能力、映射、规则)
- 系统字典
- 来源标准目录

## 扩展指南

### 添加新的配置类型

假设需要添加 `CacheConfig`:

1. **定义值对象**: `patra-registry-domain/.../vo/provenance/CacheConfig.java`
2. **定义读模型**: `patra-registry-domain/.../read/provenance/CacheConfigQuery.java`
3. **更新聚合根**: 在 `ProvenanceConfiguration` 中添加 `cacheConfig` 字段
4. **扩展仓储接口**: 在 `ProvenanceConfigRepository` 中添加查询方法
5. **创建 DO 和 Mapper**: 在 `patra-registry-infra` 中创建数据库实体和映射器
6. **实现仓储方法**: 在 `ProvenanceConfigRepositoryAdapter` 中实现查询逻辑
7. **更新 DTO**: 在 `patra-registry-api` 中添加响应 DTO

## 相关文档

- [patra-registry-api 模块](./patra-registry-api/README.md)
- [patra-registry-domain 模块](./patra-registry-domain/README.md)
- [patra-registry-app 模块](./patra-registry-app/README.md)
- [patra-registry-infra 模块](./patra-registry-infra/README.md)
- [patra-registry-adapter 模块](./patra-registry-adapter/README.md)
- [patra-registry-boot 模块](./patra-registry-boot/README.md)

---

**最后更新**: 2025-12-29
