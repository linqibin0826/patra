# Patra — 医学文献数据平台

> **状态**: 🚧 引导阶段 (v0.1.0-SNAPSHOT)
> **架构**: 微服务 + 六边形架构 + DDD + 事件驱动
> **技术栈**: Java 25, Spring Boot 3.5.7, MyBatis-Plus, MySQL 8.x

---

## 📖 什么是 Patra?

Patra 是一个**医学文献数据平台**,旨在:

1. **采集** 来自 10+ 外部数据源的文献 (PubMed, EPMC, Crossref 等)
2. **解析和标准化** 原始数据为统一的模式
3. **存储和索引** 以实现高效的搜索和分析
4. **提供 API** 供下游应用使用 (搜索、推荐、分析)

**当前重点**: 确保**可靠的数据落地** — 采集 → 解析 → 存储,具备幂等性、重试和可观测性。

---

## 🏗️ 架构概览

```
┌──────────────────────────────────────────────────────────┐
│           API Gateway (patra-gateway-boot)               │
│         入口: 路由 • 认证 • 限流                           │
└────────────┬──────────────────────────────┬──────────────┘
             │                              │
    ┌────────▼────────────┐        ┌────────▼────────────┐
    │  patra-registry     │◀───────│   patra-ingest      │
    │  (SSOT 注册中心)     │        │   (数据采集器)       │
    └─────────────────────┘        └──────────┬──────────┘
      • Provenance 配置                       │
      • Expression 元数据                     │
      • 字典管理                               │
                                              │
                                              ▼
                                      外部 API
                                (PubMed, EPMC, Crossref 等)
```

### 核心概念

- **Provenance**: 外部数据源 (PubMed, Crossref) 及其操作配置
- **Plan**: 数据采集蓝图 (时间窗口 + 切片策略)
- **Task**: 原子工作单元 (例如: 从 PubMed 抓取记录 1-1000)
- **Outbox**: 事务性事件发布,用于可靠的异步通信
- **Cursor**: 水位线跟踪,用于增量采集

详见 [架构文档](./docs/ARCHITECTURE.md) 深入了解。

---

## 📦 项目结构

### 微服务

| 模块 | 用途 | 入口点 |
|--------|---------|-------------|
| [**patra-registry**](./patra-registry/README.md) | Provenance 配置、表达式、字典的 SSOT | `patra-registry-boot` |
| [**patra-ingest**](./patra-ingest/README.md) | 编排采集计划,管理任务生命周期 | `patra-ingest-boot` |
| [**patra-gateway-boot**](./patra-gateway-boot/README.md) | API 网关 (入口),包含路由和认证 | `patra-gateway-boot` |


### 共享库

| 模块 | 用途 |
|--------|---------|
| **patra-common** | 基础类 (AggregateRoot, DomainEvent)、错误码、枚举 |
| **patra-expr-kernel** | 表达式引擎,用于动态 API 参数映射 |
| **patra-spring-boot-starter-core** | 核心自动配置 (Jackson, 错误处理) |
| **patra-spring-boot-starter-web** | Web 自动配置 (REST, Feign, 追踪) |
| **patra-spring-boot-starter-mybatis** | MyBatis-Plus 自动配置 |
| **patra-spring-boot-starter-provenance** | Provenance 配置集成 |
| **patra-spring-cloud-starter-feign** | Feign 客户端增强 |

### 构建基础设施

| 模块 | 用途 |
|--------|---------|
| **patra-parent** | 父 POM,包含依赖管理 |

---

## 🚀 快速开始

### 前置条件

- **Java 25+** (OpenJDK 或 Oracle JDK)
- **Maven 3.9+**
- **MySQL 8.0+**
- **Docker & Docker Compose** (用于本地基础设施)

### 1. 启动本地基础设施

```bash
cd docker
docker-compose up -d
```

这将启动:
- MySQL (端口 3306)
- Nacos (端口 8848) — 配置中心
- RocketMQ (端口 9876, 10911) — 消息队列

### 2. 初始化数据库

```bash
# 运行 SQL 脚本 (位置待定)
mysql -h127.0.0.1 -uroot -p < scripts/init-registry.sql
mysql -h127.0.0.1 -uroot -p < scripts/init-ingest.sql
```

### 3. 构建项目

```bash
./mvnw clean install -DskipTests
```

### 4. 启动服务

```bash
# 终端 1: 启动 registry
cd patra-registry/patra-registry-boot
../../mvnw spring-boot:run

# 终端 2: 启动 ingest
cd patra-ingest/patra-ingest-boot
../../mvnw spring-boot:run

# 终端 3: 启动 gateway
cd patra-gateway-boot
../mvnw spring-boot:run
```

### 5. 验证服务

```bash
# 健康检查
curl http://localhost:8080/actuator/health   # Gateway
curl http://localhost:8081/actuator/health   # Registry
curl http://localhost:8082/actuator/health   # Ingest
```

## 🔧 环境配置文件

- Spring profiles: `dev` (默认) 用于本地 Docker Compose 环境,`prod` 预留给未来的云部署。基础配置将 `SPRING_PROFILES_ACTIVE` 默认设置为 `dev`;在部署时导出 `SPRING_PROFILES_ACTIVE=prod` (或其他 profile)。
- 每个 `*-boot` 模块现在使用 `application.yml` + `application-{profile}.yml`。将共享设置 (端口、starters、Jackson) 放在基础文件中,在 profile 文件中覆盖环境特定配置 (数据源、redis、日志)。
- 配置中心 (Nacos) DataIds 遵循 `<service-name>-<profile>.yaml` 格式,加上可选的共享配置 `patra-<profile>.yaml`。Namespace/group 来自 `NACOS_NAMESPACE_ID`/`NACOS_CONFIG_GROUP` (回退保留旧的 `NACOS_NAMESPACE`/`NACOS_GROUP` 值)。
- 敏感值 (DB/Redis 凭证、API 密钥) 存放在环境变量中,在 `application-prod.yml` 中使用 (例如: `REGISTRY_DB_URL`, `INGEST_DB_URL`)。仅提交本地引导的默认开发配置。

---

## 📚 文档

### 核心指南

- [**架构**](./docs/ARCHITECTURE.md) — 六边形架构 + DDD 原则、依赖规则、设计模式
- [**开发指南**](./docs/DEV-GUIDE.md) — 添加用例、聚合、端点的代码示例
- [**CLAUDE.md**](CLAUDE.md) — AI 助手使用本代码库的说明

### 模块 README

- [patra-registry README](./patra-registry/README.md) — Registry 服务深入解析
- [patra-ingest README](./patra-ingest/README.md) — Ingest 服务深入解析

---

## 🛠️ 开发

### 架构原则

1. **六边形架构**: 领域层在中心,适配器在边缘
2. **DDD**: 聚合强制不变量,领域事件捕获状态变化
3. **CQRS**: 分离读/写模型以实现可扩展性
4. **事件驱动**: 通过 Outbox 模式实现异步通信
5. **幂等性**: 通过业务键实现安全重试

### 依赖规则 ⚠️

```
adapter  →  app + api
app      →  domain + patra-common
infra    →  domain
domain   →  仅 patra-common (无框架)
```

**关键**: 领域层 = **纯 Java** (无 Spring,无 MyBatis 注解)

### 代码模式

**添加用例?** 参见 [DEV-GUIDE § 添加新用例](./docs/DEV-GUIDE.md#1-adding-a-new-use-case)

**添加聚合?** 参见 [DEV-GUIDE § 添加新聚合](./docs/DEV-GUIDE.md#2-adding-a-new-aggregate)

**添加端点?** 参见 [DEV-GUIDE § 添加新 REST 端点](./docs/DEV-GUIDE.md#5-adding-a-new-rest-endpoint)

---

## 🧪 测试

```bash
# 单元测试 (领域层)
./mvnw test -pl patra-{service}-domain

# 集成测试 (仓储层)
./mvnw verify -pl patra-{service}-infra

# API 测试 (适配器层)
./mvnw verify -pl patra-{service}-adapter
```

---

## 📊 当前状态

### ✅ 已完成

- ✅ 六边形架构脚手架
- ✅ 领域模型 (Provenance, Plan, Task 聚合)
- ✅ Registry CRUD API (基于 Feign)
- ✅ Plan 编排 (时间窗口解析、切片、任务生成)
- ✅ Outbox 模式实现
- ✅ Cursor 水位线跟踪
- ✅ 通过业务键实现幂等性

### 🚧 进行中

- 🚧 PubMed 批量规划器 (最近提交)
- 🚧 任务执行工作器
- 🚧 数据解析和清洗管道

### 📋 计划中

- 📋 更多数据源 (EPMC, Crossref 等)
- 📋 数据存储微服务 (`patra-data`)
- 📋 搜索微服务 (集成 Elasticsearch)
- 📋 可观测性 (指标、分布式追踪)
- 📋 管理后台 UI

---

## 🤝 贡献

### 开发工作流

1. **创建特性分支**: `git checkout -b feat/your-feature`
2. **遵循 7 步流程** (参见 [DEV-GUIDE](./docs/DEV-GUIDE.md))
3. **确保编译通过**: `./mvnw clean compile -DskipTests`
4. **提交 PR** 保持最小差异

### 代码风格

- **Java 25 特性**: 适当使用 `record`、模式匹配、密封类
- **命名**: `*Aggregate`, `*Orchestrator`, `*RepositoryMpImpl`
- **日志**: 参数化、仅英文 (`log.info("Processing {}", id)`)
- **不可变性**: 优先使用 `record` 和 `final` 字段

---

## 📞 支持

- **Issues**: https://github.com/yourorg/patra/issues
- **讨论**: https://github.com/yourorg/patra/discussions
- **邮箱**: dev@patra.io

---

## 📄 许可证

本项目采用 Apache License 2.0 许可 - 详见 [LICENSE](LICENSE) 文件。

---

## 🙏 致谢

- **Spring 团队** — 提供卓越的 Spring Boot/Cloud 生态系统
- **MyBatis 团队** — 提供灵活的 SQL 映射
- **DDD 社区** — 提供永恒的架构模式

---

**由 Patra 团队用 ❤️ 构建**

最后更新: 2025-01-12
