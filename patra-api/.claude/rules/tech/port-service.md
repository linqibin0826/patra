# Port 与 Service 命名规范

## 概述

本规范定义了六边形架构中各类端口（Port）和服务（Service）的命名约定，确保接口命名能准确反映其职责和依赖方向。

## 命名规范表

| 类型 | 定义层 | 实现层 | 接口命名 | 实现命名 | 说明 |
|------|--------|--------|----------|----------|------|
| Repository | Domain | Infra | `{Entity}Repository` | `{Entity}RepositoryAdapter` | 聚合根持久化 |
| Driven Port | Domain | Infra | `{Function}Port` | `{Function}Adapter` | 被驱动端口（外部调用） |
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
- `StreamingDownloadPort` → `StreamingDownloadAdapter`
- `PublicationBatchPort` → `PublicationBatchAdapter`

**位置**：
- 接口：`{service}-domain/src/main/java/.../domain/port/{subdir}/{Function}Port.java`
- 实现：`{service}-infra/src/main/java/.../infra/adapter/{subdir}/{Function}Adapter.java`

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
│       ├── gateway/                        # 驱动端口接口
│       │   └── VenueInstanceGateway.java
│       ├── parser/                         # 解析器端口
│       │   └── PubmedXmlParserPort.java
│       ├── batch/                          # 批处理端口
│       │   └── PublicationBatchPort.java
│       ├── source/                         # 数据源端口
│       │   └── StreamingDownloadPort.java
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
    └── src/main/java/.../infra/adapter/
        ├── persistence/                    # 仓储实现
        │   ├── PublicationRepositoryAdapter.java
        │   └── VenueRepositoryAdapter.java
        ├── parser/
        │   └── PubmedXmlParserAdapter.java
        └── batch/
            └── PublicationBatchAdapter.java
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
