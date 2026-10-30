# Patra — 医学出版物数据平台

> **状态**: 🚧 引导阶段 (v0.1.0-SNAPSHOT)
> **架构**: 微服务 + 六边形架构 + DDD + 事件驱动
> **技术栈**: Java 25, Spring Boot 4.1.2, Spring Data JPA, PostgreSQL 17

---

## 📖 什么是 Patra?

Patra 是一个面向个人专业用户的**医学文献门户**，后端负责:

1. **采集** 外部数据源（PubMed baseline / updatefiles、MeSH、ROR、PMC 开放获取全文、OpenAlex、Scopus、LetPub）：
   由 catalog 下载文件或调用 API，解析后直接写入自己的库
2. **存储和索引** 文献、期刊、主题词、机构、全文，供门户检索与阅读
3. **账号与社区互动**：注册登录、文献库与收藏夹、讨论、投稿经验、提醒
4. **提供 API** 经网关给门户（patra-portal）使用

---

## 🏗️ 架构概览

```
                ┌──────────────────────────────────────────────┐
                │        API Gateway (patra-gateway-boot)      │
                │        入口: 路由 • 认证 • 身份断言            │
                └───────┬──────────────┬──────────────┬────────┘
                        │              │              │
               ┌────────▼───────┐ ┌────▼─────────┐ ┌──▼──────────────┐
               │ patra-catalog  │ │patra-identity│ │patra-interaction│
               │ 采集 + 目录数据 │ │ 账号与会话    │ │ 社区互动         │
               └───┬────────┬───┘ └──────────────┘ └─────────────────┘
                   │        │       identity、interaction 经内部接口调 catalog
                   │        ▼
                   │  patra-registry（平台字典：国家、语言）
                   ▼
        外部数据源（PubMed、MeSH、ROR、PMC、OpenAlex、Scopus、LetPub）
```

### 核心概念

- **整库镜像 + 每日增量**: PubMed baseline 一次导入，updatefiles 每日跟进，任何文件可重放、不会回退
- **子域分包**: 层模块内先按子域、再按组件类型分包，子域边界由 ArchUnit 守（`.claude/rules/tech/package-layout.md`）
- **领域事件**: 子域之间用同步领域事件，与发布方同一事务（`patra-events` 技能）
- **对象存储**: 各服务经 `patra-tool-object-storage` 直接读写 MinIO，文件引用记在各自的业务表里

---

## 📦 项目结构

### 微服务

| 模块 | 用途 | 入口点 |
|--------|---------|-------------|
| [**patra-registry**](./patra-registry/README.md) | 平台字典（国家、语言）及来源标准，catalog 导入时调它解析 | `patra-registry-boot` |
| [**patra-catalog**](./patra-catalog/README.md) | 采集外部数据源并直接入库；文献、期刊、主题词、机构、全文等目录数据 | `patra-catalog-boot` |
| [**patra-identity**](./patra-identity/README.md) | 前台用户的账号：注册、登录、会话、封禁 | `patra-identity-boot` |
| [**patra-interaction**](./patra-interaction/README.md) | 社区互动：文献库与收藏夹、讨论、投稿经验、提醒，按子域分包 | `patra-interaction-boot` |
| [**patra-gateway-boot**](./patra-gateway-boot/README.md) | API 网关 (入口),包含路由和认证 | `patra-gateway-boot` |

### 工具模块

技术能力（JSON、错误处理、JPA、批处理、HTTP 客户端、对象存储、可观测性、安全、OpenAPI、启动器、测试支持）
在仓库根的 [`patra-tool/`](../patra-tool/) 下，一个能力一个模块、一个 `patra.<能力>` 配置前缀，各模块 README 列了配置键与默认值。

### 构建基础设施

| 模块 | 用途 |
|--------|---------|
| **build-logic** | Gradle Convention Plugins，包含依赖管理和架构约束 |

---

## 🚀 快速开始

### 前置条件

- **Java 25+** (OpenJDK 或 Oracle JDK)
- **Gradle 8.x+** (已内置 Wrapper，无需单独安装)
- **PostgreSQL 17+**
- **Docker & Docker Compose** (用于本地基础设施)

### 1. 启动本地基础设施

```bash
cd docker
docker-compose up -d
```

这将启动:
- PostgreSQL (端口 15432)
- Nacos (端口 8848) — 服务注册中心

### 2. 初始化数据库

各服务启动时由 Flyway 自动建表并灌入种子数据，库本身由基础设施的 PG 初始化脚本创建（`patra-infra/docker/postgres/init-scripts/`）。

### 3. 构建项目

```bash
./gradlew build -x test
```

### 4. 启动服务

```bash
# 终端 1: 启动 registry
./gradlew :patra-api:patra-registry:patra-registry-boot:bootRun

# 终端 2: 启动 catalog
./gradlew :patra-api:patra-catalog:patra-catalog-boot:bootRun

# 终端 3: 启动 gateway
./gradlew :patra-api:patra-gateway-boot:bootRun
```

### 5. 验证服务

```bash
# 健康检查
curl http://localhost:9528/actuator/health   # Gateway
curl http://localhost:6000/actuator/health   # Registry
curl http://localhost:6300/actuator/health   # Catalog
```

## 🔧 环境配置文件

- **5 个后端服务**：配置从 Nacos 读（`patra-common.yaml` 加 `{服务名}.yaml`，源文件在 `patra-infra/nacos/dev/`），启动类用 `PatraApplication.run(服务名, …)`。jar 里只有 `application.yml`，放端口、连接池、超时等随代码变的默认值；库地址、Redis、MinIO、xxl-job、公钥、密钥引用等随环境变的值在 Nacos。IDE 与容器读同一份配置，只差 `PATRA_INFRA_HOST`、`PATRA_ADVERTISE_IP`、`PATRA_LOG_DIR` 三个本地变量。设计见 `docs/patra/specs/2026-10-13-config-migration-batch1-design.md`、`docs/patra/specs/2026-10-14-config-migration-batch2-design.md`。
- 服务注册中心 (Nacos) 在端口 8848 运行，提供服务发现、配置和健康检查功能。
- 密钥（Redis 密码、网关私钥、外部数据源 API key）不进仓库：Nacos 配置里只写 `${变量}`，值放在 `~/.patra/secrets/` 下的 `.env` 文件里，本机由 Nacos starter 读，容器经 compose 的 `env_file` 注入。

---

## 📚 文档

### 核心指南

- [**AGENTS.md**](./AGENTS.md) — Codex 规则主入口（项目架构与工程规范）
- [**plugins/patra-codex**](./plugins/patra-codex/) — Codex 本地插件（skills + hooks）
- [**CLAUDE.md**](./CLAUDE.md) — Claude 规则主入口

### 模块 README

- [patra-registry README](./patra-registry/README.md) — Registry 服务深入解析
- [patra-catalog README](./patra-catalog/README.md) — Catalog 服务深入解析

---

## 🛠️ 开发

### 架构原则

1. **六边形架构**: 领域层在中心,适配器在边缘
2. **DDD**: 聚合强制不变量,领域事件捕获状态变化
3. **CQRS**: 分离读/写模型以实现可扩展性
4. **事件驱动**: 子域之间用同步领域事件，与发布方同一事务
5. **幂等性**: 通过业务键实现安全重试

### 依赖规则 ⚠️

```
adapter  →  app + api
app      →  domain
infra    →  domain
domain   →  仅 patra-tool-core (无框架)
```

**关键**: 领域层 = **纯 Java** (无 Spring,无 JPA 注解)

---

## 🧪 测试

### 分层测试策略

Patra 项目遵循**测试金字塔**，各层使用不同的测试策略：

```bash
# 1. Domain 层：纯单元测试（JUnit 5 + AssertJ，无框架依赖）
./gradlew :patra-{service}:patra-{service}-domain:test

# 2. Application 层：Mock 单元测试（Mock 所有 Port 接口）
./gradlew :patra-{service}:patra-{service}-app:test

# 3. Infrastructure 层：单元测试 + 集成测试
./gradlew :patra-{service}:patra-{service}-infra:test

# 4. Adapter 层：单元测试 + 切片测试
./gradlew :patra-{service}:patra-{service}-adapter:test

# 5. Boot 层：E2E 端到端测试（@SpringBootTest + TestContainers + Awaitility）
./gradlew :patra-{service}:patra-{service}-boot:test

# 运行所有测试
./gradlew check
```

### 测试覆盖率要求

- **Domain 层**：≥ 80%（业务规则、状态转换）
- **Application 层**：≥ 70%（编排逻辑、事务边界）
- **Infrastructure 层**：有单元测试和集成测试
- **Adapter 层**：有单元测试和切片测试
- **Boot 层**：有 E2E 端到端测试

详见：`.specify/templates/test-coverage-checklist.md`

---

## 📊 当前状态

各版本的目标、范围与完成情况见 `docs/patra/release-specs/`。

---

## 🤝 贡献

### 开发工作流

1. **创建特性分支**: `git checkout -b feat/your-feature`
2. **确保编译通过**: `./gradlew classes`
3. **提交 PR** 保持最小差异

### 代码风格

- **Java 25 特性**: 适当使用 `record`、模式匹配、密封类
- **命名**: `*Aggregate`, `*Handler`, `*RepositoryAdapter`
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
- **Spring Data 团队** — 提供优雅的数据访问抽象
- **DDD 社区** — 提供永恒的架构模式

---

**由 Patra 团队用 ❤️ 构建**

最后更新: 2026-10-30
