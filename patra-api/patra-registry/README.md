# patra-registry

## 概述

`patra-registry` 是 Patra 平台系统字典的**单一真实数据源(SSOT)**,管理国家、语言等平台级字典及其来源标准目录。本服务采用六边形架构 + DDD 设计模式。

`patra-catalog` 导入数据时通过 HTTP Interface 客户端调用本服务,把外部原始值(英文国名、ISO 639-3 语言代码等)解析为平台字典项。

## 核心职责

- **系统字典**: 管理统一的枚举代码、业务字典项及来源标准目录
- **字典解析**: 按来源标准把外部原始值批量解析为字典项

## 模块结构

```
patra-registry/
├── patra-registry-boot/          # 可执行入口,组装所有模块
├── patra-registry-api/           # 外部契约(HTTP Interface、DTO、错误码)
├── patra-registry-domain/        # 纯 Java 领域模型(无框架依赖)
├── patra-registry-app/           # 应用层,用例编排
├── patra-registry-infra/         # 基础设施层,持久化实现
└── patra-registry-adapter/       # 适配器层,REST 端点实现
```

**六边形架构依赖方向**:
- `adapter` → `app` → `domain` ← `infra`
- `api` 为独立契约模块,由 `adapter` 实现
- `boot` 组装所有模块并提供可执行入口

## 子域与分包

模块内先按子域、再按组件类型分包（`dev.patra.registry.{层}.{子域}.{组件}`），模板见 `.claude/rules/tech/package-layout.md`，由 `RegistryArchitectureTest` 执行的 `SubdomainArchitectureRules` 守住。

| 子域 | 内容 |
|------|------|
| `dictionary` | 字典解析，来源标准 `ReferenceStandard` 并入此处 |
| `kernel`（底座） | 只在 domain 层：6 个异常根（`domain.kernel.exception`） |

- 各层落点：读用例 `app.{子域}.usecase.query`（`*QueryService` + `*QueryAssembler`）；仓储与读适配器 `infra.{子域}.persistence(.entity|.dao|.mapper)`；内部 RPC `adapter.{子域}.internal`（`*EndpointImpl` + `*ApiConverter`）；门户字典接口 `adapter.dictionary.rest`；契约 `api.{子域}.{endpoint,dto}`，错误码在保留包 `api.error`。

## 核心领域概念

### 1. Dictionary（字典）

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

### 2. ReferenceStandard（来源标准）

管理外部值遵循的格式规范:
- **ReferenceStandard**: 来源标准值对象(如 `ISO_3166_1_ALPHA2`、`NAME_EN`)
- 支持启用/禁用状态管理
- 通过 `DictionaryItemAlias.sourceStandard` 关联字典项

## 主要 API 契约

### DictionaryEndpoint (内部 RPC)

**基础路径**: `/_internal/dictionaries`

**核心端点**:
- `POST /_internal/dictionaries/resolve` - 批量解析字典值（`sourceStandard` 必填）

## 依赖关系

**上游依赖**:
- `patra-tool-*`: 工具模块（JPA、Web、HTTP Interface 等）

**下游消费者**:
- `patra-catalog`: 通过 `DictionaryEndpoint` 解析字典值（国家、语言等多种类型标准化）
- 其他微服务: 通过 `patra-registry-api` 模块引入 HTTP Interface 契约

## 技术栈

| 组件 | 版本/说明 |
|------|---------|
| **Java** | 25 |
| **Spring Boot** | 4.0.6 |
| **Spring Cloud** | 2025.1.0 |
| **Spring Data JPA** | 持久化框架 |
| **MapStruct** | 对象映射 |
| **Nacos** | 服务注册中心 |
| **Gradle** | 构建工具 |

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

主要配置项包括:
- 数据源连接信息（Nacos 配置 `patra-common.yaml` 与 `patra-registry.yaml`，源文件在 `patra-infra/nacos/dev/`）
- JPA/Hibernate 配置
- 日志级别

## 本地开发

### 启动服务

```bash
# 进入 boot 模块
cd patra-registry-boot

# 启动应用
../../gradlew :patra-registry:patra-registry-boot:bootRun
```

**默认端口**: 8081

### 数据库迁移

Flyway 自动执行数据库迁移,种子数据包括:
- 国家字典（V2：ISO 3166-1 alpha-2 代码、英文名与中文名别名及其来源标准）
- 语言字典（V3）

## 相关文档

- [patra-registry-api 模块](./patra-registry-api/README.md)
- [patra-registry-domain 模块](./patra-registry-domain/README.md)
- [patra-registry-app 模块](./patra-registry-app/README.md)
- [patra-registry-infra 模块](./patra-registry-infra/README.md)
- [patra-registry-adapter 模块](./patra-registry-adapter/README.md)
- [patra-registry-boot 模块](./patra-registry-boot/README.md)

---

**最后更新**: 2026-10-30
