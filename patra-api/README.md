# Papertrace 文献数据平台

Papertrace 聚焦医学科研文献的统一采集、标准化与智能分析，为搜索、画像和洞察提供高质量数据底座。当前团队仅 1 人，兼顾架构、开发、运维与文档，项目大量代码由 AI 生成，需通过体系化文档确保可维护性。

## 系统总览
- **业务目标**：接入 10+ 文献源，构建单一可信源（SSOT），保障采集→解析→入库链路可追踪
- **技术特色**：六边形架构 + DDD、事件驱动（Outbox→RocketMQ）、统一错误模型（ProblemDetail）
- **当前重点**：稳定数据落地链路、完善 Registry 配置治理、强化错误与观测规范
- **核心依赖**：Java 21、Spring Boot 3.2.4、Spring Cloud 2023.0.1、Spring Cloud Alibaba 2023.0.1.0、MyBatis-Plus 3.5.12、RocketMQ 5.3.2、MySQL 8.0、Redis 7.0、Elasticsearch 8.14、Nacos、SkyWalking、XXL-Job

> 架构详情见 `docs/overview/architecture.md`。

## 快速开始
1. **环境准备**：安装 JDK 21、Docker、Docker Compose，优先使用仓库自带 `./mvnw`
2. **启动依赖**：进入 `docker/compose` 执行 `docker compose up -d` 拉起 MySQL、Redis、Elasticsearch、RocketMQ、Nacos、SkyWalking、XXL-Job 等基础设施
3. **全仓编译**：在仓库根目录运行 `./mvnw -q -DskipTests compile` 验证依赖完整性（首次构建依赖下载耗时较长）
4. **单模块开发**：进入目标模块（例如 `patra-registry`）执行 `./mvnw -q clean test`
5. **运行服务**：各 `*-boot` 模块提供 Spring Boot 启动入口，根据需要配置 `application-local.yaml` 与 Nacos 配置项

> 启动异常时优先排查：Docker 资源、端口占用、Nacos 配置同步。

## 模块速查表

| 模块 | 职责摘要 | 深入文档 |
|------|---------|---------|
| `patra-ingest` | 采集计划装配、窗口切片、Outbox 发布 | [README](patra-ingest/README.md) · [采集链路](docs/process/ingest-dataflow.md) |
| `patra-registry` | 配置/字典/表达式 SSOT，提供快照服务 | [README](patra-registry/README.md) · [专题](docs/modules/registry/deep-dive.md) |
| `patra-gateway-boot` | API 网关、路由、鉴权与错误对齐 | [README](patra-gateway-boot/README.md) |
| `patra-common` | 跨服务领域基类、错误模型、JSON 规范化 | [README](patra-common/README.md) |
| `patra-expr-kernel` | 表达式 AST 与规范化引擎 | [README](patra-expr-kernel/README.md) |
| `patra-parent` | Maven 父 POM，统一依赖与插件 | [README](patra-parent/README.md) |
| Starters 系列 | 核心/Web/Feign/MyBatis/RocketMQ 自动配置 | [core](patra-spring-boot-starter-core/README.md) · [web](patra-spring-boot-starter-web/README.md) · [feign](patra-spring-cloud-starter-feign/README.md) · [rocketmq](patra-spring-boot-starter-rocketmq/README.md) |

## 业务与流程入口
- **采集→计划→出站**：详见 `docs/process/ingest-dataflow.md`
- **错误规范与跨服务处理**：`docs/standards/platform-error-handling.md`、`docs/standards/cross-service-error-best-practices.md`

- 更多流程文档将逐步补充至 `docs/process/`

## 开发规范速览
- 严守依赖方向：`adapter → app → domain ← infra`，领域层禁止引入框架依赖
- 领域模型使用聚合、值对象与领域事件；应用层负责鉴权、事务、幂等
- 公共能力优先复用 `patra-common` 与自研 Starters，不重复造轮子
- 错误输出采用 ProblemDetail，使用 `patra.error.context-prefix` 管理错误段
- 配置与密钥通过 Nacos/环境变量管理，禁止写入仓库
- 更多规范参见 `docs/standards/platform-error-handling.md` 与各模块专题文档（deep-dive）

## 观测与运维
- 指标：通过 Micrometer 输出错误计数、延迟、慢调用、熔断等指标，计划统一落到 SkyWalking/Prometheus
- 日志：全链路传递 `traceId`，关键类使用 `@Slf4j` + 参数化日志，禁止打印敏感信息
- 调度：XXL-Job 负责计划触发，调度参数与执行状态需在 `schedule_instance` 表留痕
- 故障案例沉淀至 `docs/operations/troubleshooting.md`

## Roadmap 与贡献
- 当前高优先级：完善采集链路监控、补充 Registry 配置巡检、搭建 Docs-as-Code 门户
- 中期计划：引入执行端闭环、扩展切片策略（CURSOR/ID_RANGE）、完善错误指标看板
- 贡献要求：小步提交，描述变更目的/风险/回滚方案，领域层新增需说明不变量
- 建议在 PR 中附带验证步骤，测试覆盖核心逻辑与边界条件

## 文档索引
- 统一索引：`docs/README.md`
- 架构专题：`docs/overview/architecture.md`
- 流程专题：`docs/process/`
- 模块专题：`docs/modules/`
- 运维手册：`docs/operations/`
- 规范与模板：`docs/standards/`、`docs/templates/`
