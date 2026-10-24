# patra-interaction

社区互动服务：本版（v0.13）是文献库与收藏夹；以后的讨论、关注动态、通知、行为数据也都放进这个服务，各占一个子域。

工程设计：`docs/patra/specs/2026-10-24-interaction-skeleton-design.md`（服务骨架、子域边界、存储与部署接入）。依据 release spec `docs/patra/release-specs/v0.13-library.md` 决策 A、B、G。

## 1. 子域

| 子域 | 职责 | 本版 |
|---|---|---|
| `subject` | 对象底座：每个被互动过的外部对象（本版只有文献）在登记表 `subject.registry` 里一行，互动数据只引用本地 `subject_id`；对象身份锚定在外部标识上（文献是 `pmid:…`），catalog 内部 ID 只是缓存。首次互动时同步向 catalog 解析并登记（catalog 不可用或对象不存在则拒绝），每日对账按 PMID 刷新；能力矩阵在 `SubjectType`。工程设计 `docs/patra/specs/2026-10-24-subject-registry-design.md` | PAP-112 |
| `collection` | 文献库与收藏夹：文献库条目（用户 × 文献唯一，可带备注），扁平的收藏夹（私有 / 公开、一个条目可进多个夹） | PAP-113 |

新增子域时同时改两处：各层下建子域包；`InteractionArchitectureTest` 里 `SubdomainArchitectureRules` 的子域列表。不建没有代码的空包和空 schema。

## 2. 模块与包

六层模块照常（domain / app / infra / adapter / api / boot），每层下再按子域分包：

```
dev.patra.interaction.{domain|app|infra|adapter|api}.{subject|collection}..
dev.patra.interaction                  启动类
dev.patra.interaction.config           装配（XXL-Job 执行器等），不属于任何子域
```

层下面只能是已声明的子域，不放 `common` 之类的共享包。子域内部的分包沿用各服务的写法（`domain.collection.model.aggregate`、`domain.collection.port.repository`、`infra.collection.persistence`、`adapter.collection.rest`、`adapter.collection.scheduler`）。api 模块是服务间契约，本版没有别的服务调用 interaction，模块里还没有类。

## 3. 子域边界

子域之间只经对方的**公开包**交互，由 `InteractionArchitectureTest`（`patra-tool-test` 的 `SubdomainArchitectureRules`）守住：

| 子域 | 公开包 |
|---|---|
| 任意子域 X | `domain.X.port.read`（读端口）、`domain.X.model.read`（读端口用到的读模型） |
| 底座 `subject` 另加 | `domain.subject.port.gateway`（驱动端口，如首次互动时解析对象，实现在 `app.subject`）、`domain.subject.model.vo`、`domain.subject.model.enums`（`SubjectId`、`SubjectType` 这类所有互动都引用的值） |

- 公开包之外的一切（聚合、实体、仓储、领域服务、app / infra / adapter 的任何类）只在子域内部用。
- 子域之间无环；底座不依赖任何其他子域。
- 推论：非底座子域的读端口，签名里只能用 JDK 类型、自己 `model.read` 里的类型和底座公开的类型。

## 4. 存储

- 库 `patra_interaction`（现有 PG 实例），每个子域一个 schema，schema 名就是子域名，在迁移脚本里 `CREATE SCHEMA`。
- 整个服务一份 Flyway 历史（`public.flyway_schema_history`）。脚本放 `patra-interaction-infra/src/main/resources/db/migration/{子域}/`，版本号是写脚本时的时间戳 `yyyyMMddHHmmss`（如 `V20261024100000__create_subject_schema.sql`），子域各自加脚本不会撞号。不设 `spring.flyway.schemas`：设了 Flyway 会自己建 schema 并把历史表挪进去。
- 实体用 `@Table(schema = "…")` 写明 schema，原生 SQL 写带 schema 的表名；不设 Hibernate 默认 schema。
- 子域的表不对 `subject` 建跨 schema 外键，只建索引；引用完整性由写入路径和每日对账保证。
- 表里只存用户 ID，不存用户名、昵称、头像（R6 检查守着迁移脚本）。
- 表名不带服务前缀，schema 已经说明归属（如 `subject.registry`）。
- `set_updated_at()` 触发器函数每个 schema 一份（`subject.set_updated_at()`），schema 整体搬走时不依赖 `public`。

## 5. 运行

| 项 | 值 |
|---|---|
| 端口 | 6500；XXL-Job 执行器 6501（不映射到宿主机） |
| 网关前缀 | `/patra-interaction/**` |
| 错误码前缀 | `INTERACTION`（如 `INTERACTION-0404`） |
| 配置 | jar 里的 `application.yml` 放随代码变的默认值；库地址在 Nacos `patra-interaction.yaml`，账号与 xxl-job 管理端地址在 `patra-common.yaml` |
| 定时任务 | 执行器 `patra-interaction`；对象对账 `subjectReconciliationJob`（`0 30 6 * * ?`，超时 600 秒、不重试，PAP-112；catalog 换库重建或重跑导入后立即手动触发一次）、注销清理 `deletedUserCleanupJob`（`0 0 6 * * ?`，PAP-113） |
| 上线 | `docs/patra/runbooks/v0.13-library-go-live-runbook.md` |

## 6. 什么时候拆分

各种互动放在一个服务里，是因为单人在一台 mini 上维护，每多一个服务就多一套模块、JVM、库、Nacos 配置和 CD；而各种互动共享「用户对某个对象做动作」这一抽象和同一条读聚合。

当某个子域的**负载、发布节奏或存储引擎**明显和其余部分分化时（比如行为数据要换 ClickHouse、讨论的写入量远超收藏），把它剥成独立服务：

1. 先确认它和其他子域之间只剩公开包上的依赖（ArchUnit 已保证）。
2. 它的 schema 整体搬到新服务的库里；没有跨 schema 外键，不用先拆约束。
3. 它被别的子域用到的公开包换成 HTTP 契约，放进新服务的 api 模块。
