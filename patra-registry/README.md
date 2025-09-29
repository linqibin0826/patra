# patra-registry

Papertrace 平台的元数据与配置单一可信源（SSOT），为采集、表达式与下游分析提供高质量快照。

## 1. 模块定位
- **服务/组件作用**：管理 Dictionary、Provenance、Expr 三大子域的配置，提供按 Scope/时间生效的快照
- **主要消费者**：`patra-ingest`（采集计划）、`patra-spring-boot-starter-expr`（表达式渲染）、未来搜索/画像服务
- **架构边界**：六边形架构 + CQRS；领域层保持无框架依赖，读侧允许快照/缓存投影

## 2. 核心能力
- **Dictionary 子域**：统一字典类型/条目/别名视图，校验默认项、状态过滤
- **Provenance 子域**：按 Scope (SOURCE/TASK) 合成多切片配置（窗口、分页、限流、凭证等）
- **Expr 子域**：聚合字段能力、渲染模板、API 参数映射，输出 `ExprSnapshot`
- **时间生效模型**：所有规则支持 `effective_from`/`effective_to`；Scope 合并规则 SOURCE < TASK
- **错误体系**：`REG-1xxx`、`REG-0xxx`（HttpStdErrors）等，配合核心 Starter 输出 ProblemDetail

> 深入设计、数据表与扩展策略请参阅 `docs/modules/registry/deep-dive.md`。

## 3. 分层结构与依赖
- 子模块：`api`（契约/错误码）、`adapter`（REST/MQ/Scheduler）、`app`（用例）、`domain`（聚合）、`infra`（MyBatis-Plus）、`boot`（启动）
- 依赖：`patra-common`、`patra-spring-boot-starter-mybatis`、Nacos、MySQL、Caffeine（规划中）
- 禁止事项：domain 层引入框架、adapter 反向依赖 infra、跨层访问

## 4. 运行与配置
- **引入方式**：`patra-registry-boot` 提供 Spring Boot 启动；Maven 依赖示例：
  ```xml
  <dependency>
    <groupId>com.papertrace</groupId>
    <artifactId>patra-registry-boot</artifactId>
    <version>0.1.0-SNAPSHOT</version>
  </dependency>
  ```
- **配置入口**：核心数据存储于 MySQL，推荐通过管理端或 SQL 种子 (`docs/modules/registry/sql`) 导入；服务配置走 Nacos
- **运行手册**：
  ```bash
  # 模块测试
  ../../mvnw -pl patra-registry -am test
  # 本地运行
  ../../mvnw -pl patra-registry/patra-registry-boot spring-boot:run
  ```

## 5. 观测与运维
- 指标建议：`registry.validation.errors.count`、`registry.snapshot.build.duration`、DB 查询耗时/行数
- 缓存策略：规划中引入 Caffeine + 版本失效（Outbox 广播）；当前需要关注 MySQL 负载
- 巡检任务：计划实现 Scope 重叠检查、默认项冲突检测
- 常见故障：配置重叠、字典多默认、快照不一致；记录处理案例至 `docs/operations/troubleshooting.md`

## 6. 测试策略
- Domain：验证子域不变量（默认项唯一、窗口无重叠、Scope 合并逻辑）
- Infra：快照查询、MyBatis 映射、分页/限流切片合成
- App：用例编排、事务、事件发布
- Adapter：DTO 序列化、错误码映射、REST/MQ 契约
- 性能：对快照构建冷/热缓存进行基准，关注延迟

## 7. Roadmap 与风险
| 项目 | 优先级 | 风险/备注 |
|------|--------|-----------|
| Caffeine 缓存 + 版本失效 | High | 需确保快照一致性与失效广播 |
| Snapshot 预编译 | High | 优化表达式渲染性能，注意兼容性 |
| Outbox 广播缓存刷新 | Mid | 需与 ingest 协调，防止放大流量 |
| JSON Schema 校验 paramsJson | Mid | 引入 schema 管理与验证成本 |
| 健康巡检任务 | Mid | 避免配置冲突滞后发现 |

重点风险：配置时间窗口重叠、Scope 覆盖顺序错误、缓存陈旧。建议建立巡检与告警。

## 8. 参考资料
- 深度文档：`docs/modules/registry/deep-dive.md`
- 配置 SQL 种子：`docs/modules/registry/sql/`
- 错误规范：`docs/standards/platform-error-handling.md`
- 采集链路：`docs/process/ingest-dataflow.md`
