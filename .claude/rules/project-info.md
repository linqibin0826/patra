# 项目信息

## 概览

**Patra** — 医学出版物数据平台 (v0.1.0-SNAPSHOT)，采集、解析、存储来自 PubMed/EPMC/Crossref 等 10+ 外部数据源的文献和期刊数据。

**架构**: 微服务 + 六边形架构 + DDD + 事件驱动
**技术栈**: Java 25 | Spring Boot 4.0.8 | Spring Data JPA | PostgreSQL 17 | Nacos
**构建工具**: Gradle 9.5.0 (Kotlin DSL) + Convention Plugins

## 核心服务

- `patra-registry` - SSOT 注册中心 (Provenance 配置、Expression 元数据、字典管理)
- `patra-ingest` - 数据采集服务 (Plan → Task → 外部 API 调用)
- `patra-catalog` - 目录服务 (文献、期刊数据索引)
- `patra-object-storage` - 对象存储元数据服务 (记录上传到 MinIO/S3 的文件元数据，仅对内提供 HTTP Interface)
- `patra-identity` - 身份服务 (前台用户的注册、登录、会话、封禁)
- `patra-interaction` - 社区互动服务 (文献库、收藏夹、讨论、投稿经验、提醒、有帮助；以后的关注)，每层按子域分包，子域边界由 ArchUnit 守
- `patra-gateway-boot` - API 网关 (路由、认证、限流)

## 模块结构

**微服务模块** (六边形架构，位于 `patra-api/`):
```
patra-{service}/
├── patra-{service}-domain   # 纯 Java 领域模型
├── patra-{service}-app      # 编排层 (@Transactional)
├── patra-{service}-infra    # 基础设施 (Repository 实现、HTTP Interface 客户端)
├── patra-{service}-adapter  # 适配器 (Controller、Job)
├── patra-{service}-api      # 服务契约 (DTO、接口定义)
└── patra-{service}-boot     # 启动入口 (@SpringBootApplication)
```

**工具模块**（`patra-tool/`，12 个，包 `dev.patra.tool.<能力>`，配置前缀 `patra.<能力>`，R1–R5 由 `./gradlew check` 守）:
- `patra-tool-core` - 纯 Java：DDD 基类、错误类型、当前用户、`PatraOrders`
- `patra-tool-cqrs` - 命令总线、`SimpleCommandBus`、内置拦截器
- `patra-tool-web` - MVC 错误解析引擎、ProblemDetail、Long → String
- `patra-tool-jpa` / `patra-tool-batch` / `patra-tool-http-client` / `patra-tool-object-storage`
- `patra-tool-observability` - 出日志与 actuator 默认值、logback
- `patra-tool-security` / `patra-tool-openapi`
- `patra-tool-launch` - `PatraApplication`、Nacos 默认值、`Clock`
- `patra-tool-test` - 测试自动配置与 Testcontainers 基座

**服务内分包**：层模块内先按子域、再按组件类型，模板与子域规则见 `.claude/rules/tech/package-layout.md`，由 `patra-tool-test` 的 `SubdomainArchitectureRules` 守（ingest 尚未迁移）。

**Patra 共享模块** (`patra-api/patra-common/`，一个模块): Shared Kernel 数据模型、共享枚举、Provenance 服务契约

**ingest 专属子模块** (`patra-api/patra-ingest/`):
- `patra-ingest-provenance` - Provenance 配置接入
- `patra-ingest-expr` - 表达式引擎（查询 AST → 各数据源查询语法）与其 Spring 接入

**构建逻辑** (仓库根 `build-logic/`):
- Convention Plugins 定义六边形架构各层与工具模块的构建约束
- `patra.tool-module` - 工具模块通用：group、R1 / R2 / R4 / R5 检查
- `patra.hexagonal-domain` - 领域层编译类路径白名单（R3）
- `patra.hexagonal-app/infra/adapter/api/boot` - 各层依赖配置（R5 挂在 domain / api / app / infra / adapter；R6 迁移脚本用户名列检查只挂 infra）
- `PatraDependencyManagement.kt` - 统一 BOM 和版本管理

## 用户名只存 identity

用户名只存在 identity 的 `idn_user` 与 `idn_user_former_username` 两张表：新表不得有 `username` / `user_name` 列，其他表、搜索索引、读模型引用用户一律存 `user_id`，展示时按 ID 现读当前名；`@` 提及正文存用户 ID 标记，对象存储键按用户 ID 命名，只有 URL 路由用用户名。原因：改名只改 identity 一行，不用回刷任何冗余。由 `patra.hexagonal-infra` 的 `checkMigrationColumns`（R6）扫描 Flyway 脚本守住。
