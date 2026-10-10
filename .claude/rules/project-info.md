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

**通用库** (`linqibin-commons/`，有仓库外下游，改动前见根 CLAUDE.md「下游消费者」):
- `linqibin-commons-core` - DDD 基类、CQRS、异常体系、工具类
- `linqibin-commons-storage` - 对象存储键生成模板
- `linqibin-spring-boot-starter-core/web/jpa/batch/rest-client/http-interface/object-storage/observability/redisson/openapi/test`

**Patra 共享模块** (`patra-api/patra-common/`):
- `patra-common-model` - Shared Kernel 数据模型
- `patra-common-enums` - 共享枚举
- `patra-common-provenance-api` - Provenance 服务契约

**Patra 专属 Starter 与内核**:
- `patra-starters/patra-spring-boot-starter-provenance`
- `patra-starters/patra-spring-boot-starter-expr`
- `patra-api/patra-expr-kernel` - 框架无关的表达式引擎（查询 AST → 各数据源查询语法）

**构建逻辑** (仓库根 `build-logic/`):
- Convention Plugins 定义六边形架构各层的构建约束
- `linqibin.hexagonal-domain` - 领域层纯净性检查（禁止框架依赖）
- `linqibin.hexagonal-app/infra/adapter/api/boot` - 各层依赖配置
- `LinqibinDependencyManagement.kt` - 统一 BOM 和版本管理
