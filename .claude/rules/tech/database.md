---
paths: patra-api/**/db/migration/**/*.sql, patra-api/*/*-infra/**/*.java, patra-tool/patra-tool-jpa/**/*.java
---

# 库表规范

适用于 6 个服务由 Flyway 管理的 PostgreSQL 库（ClickHouse、Spring Batch 元数据库不在内）。由各服务的结构检查集成测试和 ArchUnit 守。设计与推导见 `docs/patra/specs/2026-11-16-database-standard-design.md`。

## 1. 表

- 业务表都在 `public`，禁止 `CREATE SCHEMA`。表名 `{前缀}_{聚合}[_{子表}]`，单数、小写下划线。前缀：registry `sys`、catalog `cat`、identity `idn`、interaction `itr`、notification `ntf`、analytics `anl`。
- 每张表登记在本服务的 `db/tables.yml`，写明档位和一句说明。**主表**：有自己的生命周期、会被按行修改、「谁改的」有意义；其余都是**其他表**。拿不准的表在登记文件里写死并附理由。
- 分区子表 `{父表}_p00`，与父表同在 `public`，不登记。框架表保持默认名、登记为例外：`flyway_schema_history`、`event_publication`、`processed_event`。

## 2. 主键与引用

- 每张表主键 `id BIGINT`，雪花 ID 由应用预分配；禁止 `IDENTITY`、`serial`、UUID 主键。分区表主键 `(分区键, id)`。
- 自然键另建唯一索引（`CREATE UNIQUE INDEX`），不用唯一约束：以后要改回联合主键时，唯一索引能直接提升为主键。
- 引用列 `[{角色}_]{实体}_id`，指向用户的以 `user_id` 结尾（`author_user_id`）。外部编码按含义起名（`pmid`）。
- 不建外键，引用关系由应用保证。

## 3. 必备列

| 档位 | 列 |
|---|---|
| 每张表 | `id`、`created_at`、`updated_at` |
| 主表再加 | `created_by`、`updated_by`（用户 ID，空表示系统写入）、`ip_address INET`、`record_remarks JSONB` |

- `version BIGINT` 只用于乐观锁，只放主表；别的「版本」按含义起名（`body_version`）。
- 禁止 `username`、`user_name`、`*_by_name` 列（identity 的两张用户名表除外）。

## 4. 时间

- `created_at` / `updated_at` 由数据库填：`DEFAULT now()`，并挂 `BEFORE INSERT OR UPDATE` 的 `set_audit_times()` 触发器（插入时两列取当前时间；更新时行内容真的变了才刷新 `updated_at`；应用写的值一律忽略）。应用不写这两列。
- JPA 实体默认不映射这两列；查询要用时只读映射（`insertable = false, updatable = false`）。不用 `@Generated`、`@CreationTimestamp`：它们会让批量插入退化成逐条执行。
- 读取可以用它们展示和排序；写侧的业务规则（时间窗口、宽限期、过期清理、抄进别的列或事件）不得依赖，另起业务时间列（`published_at`），由应用用注入的 `Clock` 写。
- 时间类型一律 `TIMESTAMPTZ`。

## 5. 软删除

- 用显式的 `deleted_at TIMESTAMPTZ NULL`，查询自己写 `deleted_at IS NULL`。禁用 `@SoftDelete`。
- 身份标识（PMID、NLM 号、用户名、邮箱）全表唯一；删除后允许别人重新占用的值，才用 `WHERE deleted_at IS NULL` 的部分唯一索引。
- 记录「上游某物已删」的墓碑列不叫 `deleted_at`，按含义起名。

## 6. 列类型、命名与注释

- 枚举存大写字符串，加命名的检查约束列出取值；JSON 用 `JSONB`；字符串默认 `TEXT`，有业务上限才用 `VARCHAR(n)`；计数 `NOT NULL DEFAULT 0`。
- 除主键外显式命名：`uk_` / `ck_` / `idx_` + 完整表名 + 用途短语（不超过 3 个词，不罗列列名）。任何标识符不超过 63 字节，PostgreSQL 超长会静默截断。
- 每张表、每个业务列写中文 `COMMENT`；第 3 节的标准列不用写。

## 7. 迁移脚本

- 版本号 `V{yyyyMMddHHmmss}__{描述}.sql`，按子域分目录；种子数据用幂等的 `R__` 脚本，`id` 写死。
- 出现第一个不能丢数据的环境后，已执行的脚本禁止修改；大表建索引用 `CONCURRENTLY`，加约束先 `NOT VALID` 再 `VALIDATE`。

## 8. 写侧技术（按服务）

- **JPA 服务**（registry、catalog、identity）：聚合经实体存取，写侧不用 `JdbcClient` / `JdbcTemplate`。不经实体的写入只能是 Dao 上的 `@Query`，并带 `@Modifying(flushAutomatically = true, clearAutomatically = true)`：原生语句不会触发 Hibernate 自动落库。
- **SQL 服务**（interaction、notification、analytics）：不得出现 JPA 实体；写入用 `JdbcClient`，只在 `infra..persistence..`。主表的 INSERT 写 `created_by`，UPDATE 写 `updated_by`；系统写入（消息消费、定时任务）写空，不留上一个用户的 ID。
- 读适配器（实现 `port.read` 端口的类）读法不限，不得写库。
