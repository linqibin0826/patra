# Papertrace 文献数据平台

Papertrace（Papertrace Medical Literature Data Platform）聚焦于医疗科研文献的统一采集、标准化处理与智能分析，为下游搜索、画像与洞察能力提供高质量数据底座。本仓库汇聚了平台公共组件、核心服务以及加速开发的内建 Starter。

## 项目概览

- **目标**：对接 10+ 文献源，建立单一事实来源（SSOT），确保从采集、解析到持久化的链路稳定可追踪。
- **当前重点**：保障数据落地链路的可靠性，完善配套基础设施（注册中心、表达式引擎、统一错误模型等）。
- **技术栈**：Java 21、Spring Boot 3.2、Spring Cloud 2023、Spring Cloud Alibaba、MyBatis-Plus、RocketMQ、Elasticsearch、Redis、Nacos、SkyWalking、XXL-Job。

## 架构理念

- 六边形架构 + DDD 分层，严格控制依赖方向，保持领域层纯净。
- CQRS 拆分读写模型，写侧通过 Outbox + Relay 发布领域事件。
- 组件化设计：公共能力沉淀在 `patra-common` 与自研 Starter 中，服务共享统一基建。
- 可观测性优先：SkyWalking 追踪、结构化错误码与统一日志规范确保链路透明。

## 仓库结构速览

```text
Papertrace-api/
├─ patra-parent/                 # Maven 父 POM，统一依赖与插件版本
├─ patra-common/                 # 通用领域基类、错误模型、常量
├─ patra-expr-kernel/            # DSL / 表达式执行内核
├─ patra-gateway-boot/           # API Gateway 入口（Spring Cloud Gateway）
├─ patra-registry/               # 配置/字典/元数据 SSOT 服务
├─ patra-ingest/                 # 文献采集与落地服务（Hexagonal 分层）
├─ patra-spring-boot-starter-*/  # 自研 Spring Boot Starters（core/web/mybatis/expr）
├─ patra-spring-cloud-starter-feign/ # Feign 扩展 Starter
├─ docker/                       # 本地一键启动依赖（MySQL、Redis、ES、MQ、Nacos、SkyWalking 等）
└─ docs/                         # 项目文档与设计说明
```
## 快速开始

1. **环境准备**：确保已安装 JDK 21、Docker、Docker Compose。建议使用项目自带的 `mvnw`，避免本地 Maven 版本差异。
2. **启动依赖服务**：在 `docker/compose` 目录执行 `docker compose up -d`，拉起 MySQL、Redis、Elasticsearch、RocketMQ、Nacos、SkyWalking、XXL-Job 等组件。
3. **编译校验**：在仓库根目录运行 `./mvnw -q -DskipTests compile`，验证多模块依赖是否完整。首次构建由于下载依赖可能耗时较长。
4. **单模块开发**：进入目标模块（例如 `patra-registry`）后执行 `./mvnw -q clean test` 验证基础用例。
5. **运行服务**：各 `*-boot` 模块提供 Spring Boot 启动入口，按需配置 `application-local.yaml` 与 Nacos 配置项。

> 若启动失败，请优先排查 Docker 资源占用、端口冲突以及 Nacos 配置是否同步。

## 核心模块一览

- `patra-parent`：统一的依赖与插件 BOM，约束 Java 版本、Spring 家族版本及公司内部依赖。
- `patra-common`：领域基类、错误模型、通用常量，支撑跨模块的领域建模与异常处理。
- `patra-expr-kernel` / `patra-spring-boot-starter-expr`：表达式执行内核与 Spring Boot 自动配置，提供规则配置与运行能力。
- `patra-ingest`：文献采集主干服务，按照 Hexagonal 架构拆分 adapter/app/domain/infra，维持写侧高吞吐和读侧查询能力。
- `patra-registry`：元数据与配置中心，负责 SSOT，内部包含丰富的字典模板与 SQL 脚本。
- `patra-gateway-boot`：API Gateway，统一入口承担鉴权、流控与路由。
- 自研 Starter：`patra-spring-boot-starter-core/web/mybatis` 与 `patra-spring-cloud-starter-feign` 统一封装日志、错误处理、MyBatis 配置与远程调用规范。
## 开发规范与最佳实践

- 严格遵循六边形架构依赖方向：`adapter → app → domain`，`infra` 仅依赖 `domain`，`domain` 保持无框架依赖。
- 领域模型优先使用聚合、值对象与领域事件；应用层负责用例编排、鉴权、事务与幂等控制。
- 公共能力优先复用 `patra-common` 与自研 Starter，避免重复造轮子；领域层禁止引入 Spring 组件。
- 错误码、日志与跟踪需贯穿全链路：统一使用结构化错误码、`@Slf4j` + 参数化日志、SkyWalking TraceId 透传。
- 配置与密钥通过 Nacos 或环境变量管理，禁止硬编码；涉及数据落地链路的改动必须可回放、幂等、可观测。

## 体检摘要（2025-09-27）

- ✅ `./mvnw -q -DskipTests compile` 全量编译通过（耗时 ~28s），说明依赖树与模块 POM 配置完整。
- ⚠️ 自动化测试覆盖较低，目前仅检测到 1 个单测文件（`patra-expr-kernel`），建议为核心服务补充领域层与应用层用例。
- ⚠️ 未发现统一的 CI/CD 脚本与质量门禁，后续落地需补充构建、扫描与部署流水线。
- ✅ 文档体系初具雏形：`docs/` 下提供错误处理规范、registry 字典模板等，可作为团队知识库持续迭代。

## 文档资源与下一步

- 详细模块说明：参见 `patra-ingest/readme.md`、`docs/patra-registry/README.md`。
- 错误处理规范：`docs/patra-error-handling/`。
- 本地依赖配置样例：`docker/` 目录下按组件划分。
