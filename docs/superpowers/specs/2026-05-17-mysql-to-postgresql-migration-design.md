# 设计：将 Patra 后端从 MySQL 8.0 彻底迁移到 PostgreSQL 17

- **状态**：Draft（等用户审阅）
- **日期**：2026-05-17
- **作者**：linqibin（与 Claude Code 协作）
- **范围**：仅 `patra-api` 仓库与 `patra-infra/docker` 中的核心 MySQL 容器
- **前提**：绿地项目，单人开发，**不需要迁移 MySQL 中已有数据**（用户明确说明）

---

## 1. 目标与原则

把 `patra-api` 仓库中所有持久化层从 MySQL 8.0 一刀切换到 PostgreSQL 17。Java 业务代码 / 领域模型 / 测试用例 / Spring Boot 业务行为对外保持不变；JPA/Hibernate 通过自动方言检测无缝切换；Flyway DDL / Docker 编排 / 文档全部重写为 PG 版本。

遵循项目 CLAUDE.md 的强制要求：

- **零向后兼容**：不保留 MySQL 兼容 shim、不引入双方言并存、不留 deprecated 注解或注释。
- **直接采用最优解**：决策按 PG 原生最优形态落地，不保留 MySQL 思维残留。
- **质量优先**：每个值得现场验证的点都在第 §10 节列出。

---

## 2. 方案选型

| 方案 | 描述 | 评估 |
|---|---|---|
| **A. 一刀切替换（推荐）** | 直接重写 Flyway 脚本、替换驱动/容器/Compose；不保留 MySQL 路径 | **采用** — 零技术债、最低复杂度；与项目"无历史包袱"前提一致 |
| B. 双方言 profile 并存 | 通过 `spring.profiles.active` 切换 MySQL/PG | 否决 — 引入 starter 条件依赖与双套 DDL 维护，违反 CLAUDE.md |
| C. 抽象 DDL 生成器 | YAML → 多方言 SQL | 否决 — 收益小于一次性翻译 81 张表的成本 |

---

## 3. 工作量盘点（来自 4 个调研代理的实测数据）

| 类别 | 数量 / 位置 | 工作量 |
|---|---|---|
| Flyway 脚本 | **81 张表**（registry 15 / ingest 10 / catalog 55 / object-storage 1），分布在 22 个 V*.sql 文件 | 高 |
| Java native query 含 MySQL JSON 函数 | `VenueDao.findFilteredJournalPage`（3 处 `JSON_UNQUOTE/JSON_EXTRACT`）+ `TaskRunDao` 1 处 `CAST(... AS JSON)` | 中 |
| Java 错误码映射 | `JpaErrorMappingContributor`（MySQL 1062/1451/1452 switch） | 低 |
| Spring Batch schema 加载 | `BatchSchemaInitializer`（`schema-mysql.sql` 常量） | 低 |
| application*.yml | 4 个 boot × {main, dev, prod, test}，5 处 `jdbc:mysql://` URL + 4 处 `driver-class-name` | 低 |
| Docker Compose / init scripts | `patra-infra/docker/docker-compose.core.yaml` + `.env.dev` + `docker/mysql/init-scripts/02-create-batch-db.sql` | 低 |
| Testcontainers 初始化器 | `MySQLContainerInitializer` + 2 个子类 + `ContainerType.MYSQL` 枚举 | 低 |
| `.idea/dataSources.xml` 已入库且含 MySQL URL | git 已跟踪 | 低 + 加 `.gitignore` |
| 文档面 README/CLAUDE/Skill 引用 MySQL | 14+ 个 Markdown 文件 | 中 |
| 字段重命名（PG 保留字） | `cat_publication_identifier.value` → `identifier_value`（仅此一处） | 低 |
| LIKE 大小写不敏感语义 | `VenueDao:94` + `PublicationDao:125`：MySQL collation 隐式提供 → PG 改 `ILIKE` 显式 | 低 |
| 注释中"MySQL"字样 | ~10 处 Java 文件注释 | 低 |
| **不迁移**：XXL-Job 元数据库 | `docker-compose.jobs.yaml`，独立 MySQL 容器仅用于 XXL-Job admin | — |

---

## 4. 关键决策矩阵

| # | 决策项 | 选择 | 依据 |
|---|---|---|---|
| 4.1 | PostgreSQL 版本 | **PG 17**（Docker tag `postgres:17`） | 最新稳定；ICU/JSONB/STORED Generated Columns 全部 GA |
| 4.2 | JDBC 驱动 | `org.postgresql:postgresql`（Boot 4 BOM 托管 42.7.10） | 官方驱动 |
| 4.3 | Flyway PG 模块 | `org.flywaydb:flyway-database-postgresql`（Boot 4 BOM 托管 11.x） | Flyway 11 拆分包结构 |
| 4.4 | Hibernate Dialect | 自动检测（不显式配置 `hibernate.dialect`） | `PostgreSQLDialect` 单类覆盖 PG 14+ |
| 4.5 | JSON 列类型 | **`jsonb`**（DDL 显式写 `jsonb`） | `@JdbcTypeCode(SqlTypes.JSON)` 自动映射到 `jsonb`；支持 GIN 索引 |
| 4.6 | `BIGINT UNSIGNED` | → `BIGINT`（雪花 ID 53 位有效，范围充足） | PG 无 UNSIGNED |
| 4.7 | `INT UNSIGNED` | → `INTEGER`（视语义，部分计数器加 `CHECK (x >= 0)`） | PG 无 UNSIGNED |
| 4.8 | `TINYINT UNSIGNED` | → `SMALLINT`（视语义加 `CHECK (x >= 0)`） | PG 无 UNSIGNED 与 TINYINT |
| 4.9 | `TINYINT(1)` + MySQL `BOOLEAN` | → 统一 `BOOLEAN`（PG 原生） | PG `boolean` 是真布尔，不接受 0/1 字面量 |
| 4.10 | Seed 中 `0`/`1` boolean 字面量 | → 全数替换为 `false`/`true` | 涉及 V1.1.0 / V1.2.0 / V1.3.0 / V1.4.0 共 4 个 seed 文件 |
| 4.11 | `VARBINARY(16)` (ip_address) | → `bytea`（BaseJpaEntity `byte[] ipAddress` 保持不变） | PG 标准二进制类型，零 Java 代码改动 |
| 4.12 | Seed 中 `INET6_ATON('192.168.1.10')` | → `'\xC0A8010A'::bytea`（十六进制字面量） | PG 无此函数；只涉及 V1.1.0 极少几条 |
| 4.13 | `DECIMAL(38, x)` | → `NUMERIC(38, x)` | PG `NUMERIC` 即 ANSI DECIMAL |
| 4.14 | `AUTO_INCREMENT` | **删除**（37 张表受影响）；统一用应用层雪花 ID | 与 `BaseJpaEntity` "不用 `@GeneratedValue`" 设计一致 |
| 4.15 | `TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)` | → `timestamptz(6)` + `set_updated_at()` 触发器函数 + 每张表 `BEFORE UPDATE` 触发器 | PG 无 `ON UPDATE`；触发器加条件 `IF NEW.updated_at IS NOT DISTINCT FROM OLD.updated_at` 避免覆盖 JPA `@LastModifiedDate` 已设置的值 |
| 4.16 | `TIMESTAMP(6)` 时区语义 | 统一 `timestamptz(6)` | PG 推荐；与现有"UTC 存储"语义一致 |
| 4.17 | `GENERATED ALWAYS AS (...) STORED` 表达式翻译 | 见 §5.1 逐字段翻译表 | `IFNULL`→`COALESCE`、`IF(x=1,a,b)`→`CASE WHEN x THEN a ELSE b END`、`SUBSTRING_INDEX`→`split_part`；全部 IMMUTABLE |
| 4.18 | `CHECK (REGEXP_LIKE(col, pat))` | → `CHECK (col ~ 'pat')` | PG `~` POSIX 正则操作符 |
| 4.19 | `FULLTEXT INDEX ... WITH PARSER ngram` | **全部删除**（6 个索引） | catalog 全文检索由 ES 承接（已在 catalog-infra 描述中预告）；本次不引入 pg_trgm/pg_bigm |
| 4.20 | `FOREIGN KEY` 约束 | 保留（仅 V1.0.1 中 6 条） | PG 默认 `NO ACTION` 等价于 MySQL 默认 `RESTRICT` |
| 4.21 | `ENGINE=InnoDB / CHARSET=utf8mb4 / COLLATE=... / ROW_FORMAT=DYNAMIC` | **全部删除** | PG 无对应概念 |
| 4.22 | 数据库默认 collation | PG 标准 **`C` collation**（确定性） | 避免 nondeterministic ICU collation 带来的 B-tree dedup 失效 + LIKE 索引失效 |
| 4.23 | Case-insensitive LIKE 查询 | `VenueDao:94` / `PublicationDao:125` 改用 **`ILIKE`** 实现 | PG 原生大小写不敏感 LIKE，与 collation 解耦；语义等价 |
| 4.24 | Java 侧 ICU4J Collator 去重逻辑 | **保留不变**，仅更新注释为"对外部数据源的 collation-aware 去重，与 DB 无关" | 该逻辑在内存中处理解析后对象，与 DB collation 解耦 |
| 4.25 | PG 保留字字段冲突 | `cat_publication_identifier.value` → 重命名 **`identifier_value`** | 仅此一处真冲突；其他子串字段（`cursor_value`/`new_value`/`window_spec`/`window_from_ts` 等）不是保留字 |
| 4.26 | `VenueDao.findFilteredJournalPage` JSON 函数 | `JSON_UNQUOTE(JSON_EXTRACT(col, '$.k'))` → `col->>'k'`；`JSON_EXTRACT(col, '$.k')` → `(col->>'k')::numeric` | PG JSONB 原生操作符 |
| 4.27 | `TaskRunDao` `CAST(:checkpointJson AS JSON)` | → `:checkpointJson::jsonb` | PG 原生 cast 语法 |
| 4.28 | `PublicationDao` `ORDER BY citation_count IS NULL, citation_count DESC` | → `ORDER BY citation_count DESC NULLS LAST` | PG 默认 `DESC` 是 `NULLS FIRST`（与 MySQL 相反）；显式标注语义 |
| 4.29 | `JpaErrorMappingContributor` MySQL 1062/1451/1452 switch | **整体删除** | PG SQLState `23505`→`DuplicateKeyException`、`23503/02/14`→`DataIntegrityViolationException`，由 Spring Framework 自动翻译，上层 `DataIntegrityViolationException` 分支已映射为 CONFLICT |
| 4.30 | Spring Batch schema | `BatchSchemaInitializer.SCHEMA_RESOURCE` → `db/batch/schema-postgresql.sql` | Spring Batch 5 自带 `org/springframework/batch/core/schema-postgresql.sql`，复制到本项目同路径 |
| 4.31 | catalog 独立 Batch 数据源 `patra_batch` | 同步建 PG 库；schema 走上述 PG 版本 | catalog `application-dev.yml:50` 已配置独立数据源 |
| 4.32 | Testcontainers MySQL → PG | `MySQLContainerInitializer` → `PostgreSQLContainerInitializer`；2 个服务子类同步重命名；`ContainerType.MYSQL` → `POSTGRESQL`；镜像 `postgres:17` | API 完全对称 |
| 4.33 | Testcontainers JDBC URL（batch starter test） | `jdbc:tc:mysql:8.0.40:///test_batch?TC_TMPFS=...` → `jdbc:tc:postgresql:17:///test_batch` | TC 标准格式 |
| 4.34 | docker-compose.core.yaml | `mysql:8.0.36` 服务整段替换为 `postgres:17` | 容器名 `patra-mysql` → `patra-postgres`；端口 `13306:3306` → `15432:5432`；healthcheck `mysqladmin ping` → `pg_isready -U postgres`；卷路径 `~/.patra/docker/mysql/*` → `~/.patra/docker/postgres/*` |
| 4.35 | `.env.dev` | `MYSQL_ROOT_PASSWORD` / `MYSQL_PORT` → `POSTGRES_PASSWORD` / `POSTGRES_PORT` | |
| 4.36 | `docker/mysql/init-scripts/02-create-batch-db.sql` | 重写为 PG 语法 `CREATE DATABASE patra_batch;`，移到 `docker/postgres/init-scripts/` | PG `CREATE DATABASE` 无 CHARACTER SET 子句 |
| 4.37 | JDBC URL 参数 | 移除 `useUnicode=true&characterEncoding=utf8&serverTimezone=UTC&rewriteBatchedStatements=true` | PG 默认 UTF-8；`rewriteBatchedStatements` 是 MySQL 驱动专属 |
| 4.38 | JDBC 默认用户名 | `root` → `postgres`；密码 `123456` 保持 | PG 镜像惯例 |
| 4.39 | JDBC 默认端口 | `13306` → `15432`（避开本地默认 5432） | 与 MySQL 13306 端口避让风格一致 |
| 4.40 | `.idea/dataSources.xml` 已入库 | 加入 `.gitignore` 并 `git rm` 已跟踪文件 | IDE 配置不应入库；迁移后开发者在本地重建 PG 连接 |
| 4.41 | XXL-Job 元数据库 | **不迁移**（明确划出范围） | XXL-Job 3.2.0 元数据 schema 由上游维护；保留 `docker-compose.jobs.yaml` 中专给 XXL-Job 的独立 MySQL 容器 |

---

## 5. 关键细节

### 5.1 STORED Generated Column 表达式翻译表

| 表 | 字段 | MySQL 表达式 | PG 表达式（IMMUTABLE） |
|---|---|---|---|
| `sys_dict_item` | `default_key BIGINT` | `CASE WHEN (is_default=1 AND enabled=1 AND deleted_at IS NULL) THEN type_id ELSE NULL END` | `CASE WHEN (is_default AND enabled AND deleted_at IS NULL) THEN type_id ELSE NULL END` |
| `sys_reference_standard` | `canonical_key VARCHAR(64)` | `CASE WHEN (is_canonical=1 AND enabled=1) THEN dict_type_code ELSE NULL END` | `CASE WHEN (is_canonical AND enabled) THEN dict_type_code ELSE NULL END` |
| `reg_prov_expr_render_rule` | `match_type_key VARCHAR(16)` | `IFNULL(match_type_code, 'ANY')` | `COALESCE(match_type_code, 'ANY')` |
| `reg_prov_expr_render_rule` | `negated_key CHAR(3)` | `IFNULL(IF(negated = 1, 'T', 'F'), 'ANY')` | `COALESCE(CASE WHEN negated THEN 'T' WHEN NOT negated THEN 'F' END, 'ANY')` |
| `reg_prov_expr_render_rule` | `value_type_key VARCHAR(16)` | `IFNULL(value_type_code, 'ANY')` | `COALESCE(value_type_code, 'ANY')` |
| `cat_publication` | `language_base VARCHAR(5)` | `SUBSTRING_INDEX(language_code, '-', 1)` | `split_part(language_code, '-', 1)` |

`negated_key` 的 PG 表达式用 `CASE WHEN negated THEN 'T' WHEN NOT negated THEN 'F' END` 形式覆盖 NULL 情况，再用 `COALESCE(..., 'ANY')` 给 NULL 兜底；等价于 MySQL 原始的 "`IF(negated=1,'T','F')` 在 NULL 输入上返回 NULL → 再 `IFNULL` 兜底" 语义。

### 5.2 `set_updated_at()` 触发器

在每个服务首张表所在的 Flyway 脚本（registry V1.0.0 / ingest V0.1.0 / catalog V1.0.0 / object-storage V0.1.0）中添加：

```sql
CREATE OR REPLACE FUNCTION set_updated_at() RETURNS TRIGGER AS $$
BEGIN
  IF NEW.updated_at IS NOT DISTINCT FROM OLD.updated_at THEN
    NEW.updated_at = now();
  END IF;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;
```

然后每张含 `updated_at` 字段的表追加：

```sql
CREATE TRIGGER trg_<table>_updated_at
BEFORE UPDATE ON <table>
FOR EACH ROW EXECUTE FUNCTION set_updated_at();
```

条件 `IS NOT DISTINCT FROM` 确保：
- JPA 路径（Hibernate `@LastModifiedDate` 已显式设值）→ NEW.updated_at != OLD.updated_at，跳过；
- 非 JPA 路径（Flyway seed、psql 手工、运维脚本）→ NEW.updated_at == OLD.updated_at，触发器填 `now()`。

### 5.3 PG 字段重命名清单

| 表 | 旧字段 | 新字段 | 受影响 Java 文件 |
|---|---|---|---|
| `cat_publication_identifier` | `value` | `identifier_value` | `PublicationIdentifierEntity.java` 的 `@Column(name="value")` → `@Column(name="identifier_value")`；Java 属性名 `value` 保持不变（不是保留字） |

仅此 1 处真冲突。其他名字（`cursor_value` / `new_value` / `prev_value` / `observed_max_value` / `window_spec` / `window_from_ts` / `window_to_ts` / `effective_from` / `effective_to` / `last_synced_at`）含保留字子串但本身不是保留字，PG 接受无需引号。

### 5.4 ILIKE 改写

| 文件:行 | 原 SQL 片段 | 改写后 |
|---|---|---|
| `VenueDao.java:94`（注释依赖 collation） | `WHERE name LIKE :pattern` | `WHERE name ILIKE :pattern` |
| `PublicationDao.java:125`（注释依赖 collation） | 同上模式 | 同上 |

并同步把这两处 Java 注释里"依赖 MySQL `utf8mb4_0900_ai_ci`"改为"显式 ILIKE 实现大小写不敏感"。

### 5.5 docker-compose.core.yaml postgres 服务（目标态）

```yaml
postgres:
  image: postgres:17
  container_name: patra-postgres
  restart: unless-stopped
  ports: [ "15432:5432" ]
  environment:
    POSTGRES_USER: postgres
    POSTGRES_PASSWORD: ${POSTGRES_PASSWORD:-123456}
    TZ: Asia/Shanghai
  volumes:
    - ${HOME}/.patra/docker/postgres/data:/var/lib/postgresql/data
    - ${HOME}/.patra/docker/postgres/init:/docker-entrypoint-initdb.d
  healthcheck:
    test: [ "CMD-SHELL", "pg_isready -U postgres" ]
    interval: 10s
    timeout: 5s
    retries: 10
```

init 脚本 `~/.patra/docker/postgres/init/02-create-databases.sql`（重写自原 `02-create-batch-db.sql`）：

```sql
CREATE DATABASE patra_registry;
CREATE DATABASE patra_ingest;
CREATE DATABASE patra_catalog;
CREATE DATABASE patra_storage;
CREATE DATABASE patra_batch;
```

---

## 6. 实施清单（按依赖顺序）

按以下顺序执行，每个里程碑后用对应模块的 IT 测试套件验证。

### M1. 基础设施层（Gradle + 容器编排）

1. `gradle/libs.versions.toml`：`testcontainers-mysql` → `testcontainers-postgresql`。
2. `linqibin-spring-boot-starter-jpa/build.gradle.kts`：`flyway-mysql` → `flyway-database-postgresql`；`mysql-connector-j` → `postgresql`。
3. `linqibin-spring-boot-starter-test/build.gradle.kts`：同上 2 处。
4. `linqibin-spring-boot-starter-batch/build.gradle.kts`：`compileOnly("com.mysql:mysql-connector-j")` → `compileOnly("org.postgresql:postgresql")`。
5. `patra-infra/docker/docker-compose.core.yaml`：mysql 服务整段替换。
6. `patra-infra/docker/.env.dev`：`MYSQL_*` → `POSTGRES_*`。
7. 重写 `patra-infra/docker/mysql/init-scripts/02-create-batch-db.sql` → `patra-infra/docker/postgres/init-scripts/02-create-databases.sql`。
8. 删除 git 中的 `.idea/dataSources.xml` 系列文件，把它们加入 `.gitignore`。

**验证**：`cd patra-infra && docker compose -f docker/docker-compose.core.yaml up -d postgres && docker exec -it patra-postgres psql -U postgres -c '\l'`。

### M2. starter-test 容器初始化器

9. 重命名 `MySQLContainerInitializer` → `PostgreSQLContainerInitializer`（镜像、JDBC URL、driver-class-name、`ContainerType.MYSQL` → `POSTGRESQL`、`getDatabaseName()` 默认值 `patra_test`、用户名 `postgres`、密码 `123456`）。
10. catalog/ingest 模块内 `CatalogMySQLContainerInitializer` / `IngestMySQLContainerInitializer` → PG 命名同步重命名（包含 `application-test.yml` 中如有引用）。
11. `ContainerType` 枚举值同步更名。

**验证**：`./gradlew :linqibin-spring-boot-starter-test:test`。

### M3. starter-jpa 错误映射与 starter-batch schema

12. `JpaErrorMappingContributor`：在 `mapSqlExceptions` 中删除 MySQL 1062/1451/1452 的 switch 分支与相关注释（PG 上永远不会命中）；保留现有的 `SQLIntegrityConstraintViolationException → CONFLICT` 和 `SQLState 08*/HY* → UNAVAILABLE` 通用分支不变（`23*` 这类 PG 完整性错误由 Spring Data 在上游已包装为 `DataIntegrityViolationException`，已在 `mapSpringDataExceptions` 中映射为 CONFLICT）。同步更新 `linqibin-spring-boot-starter-jpa/README.md` 中错误码对照表（1062→23505、1451/1452→23503，仅文档对照用途）。
13. `BatchSchemaInitializer.SCHEMA_RESOURCE` → `db/batch/schema-postgresql.sql`；删除 `linqibin-spring-boot-starter-batch/src/main/resources/db/batch/schema-mysql.sql`；从 Spring Batch 5 jar 抽取 `org/springframework/batch/core/schema-postgresql.sql` 放到 `db/batch/schema-postgresql.sql`。
14. `linqibin-spring-boot-starter-batch/src/test/resources/application-test.yml`：`jdbc:tc:mysql:8.0.40:///test_batch?TC_TMPFS=...` → `jdbc:tc:postgresql:17:///test_batch`。

**验证**：`./gradlew :linqibin-spring-boot-starter-jpa:test :linqibin-spring-boot-starter-batch:test`。

### M4. Flyway DDL 全量重写（按服务）

15. **patra-registry**（V1.0.0 sys_dict 4 表 + V1.0.1 reg_prov_* 12 表 + V1.0.2 reg_expr_* 3 表 + V1.1.0/V1.2.0/V1.3.0/V1.4.x seed/ddl 共 8 文件）：
    - 删除所有 `ENGINE/CHARSET/COLLATE/ROW_FORMAT`、`AUTO_INCREMENT`、`UNSIGNED`、`ON UPDATE`、反引号；
    - 类型替换（`BIGINT UNSIGNED`/`TINYINT(1)`/`JSON`/`VARBINARY(16)`/`DECIMAL(38,12)`/`TIMESTAMP(6)` → §4 表格）；
    - 6 条 `REGEXP_LIKE` CHECK → `~` 操作符；
    - 5 处 generated column 表达式重写（§5.1）；
    - 在 V1.0.0 首文件顶部注入 `set_updated_at()` 函数；
    - 每张含 `updated_at` 的表后追加 `BEFORE UPDATE` 触发器；
    - V1.1.0/V1.2.0/V1.3.0/V1.4.0 中 seed 数据：所有 `is_xxx=1/0` 改 `true/false`；`INET6_ATON('192.168.1.10')` → `'\xC0A8010A'::bytea`；`JSON_ARRAY(...)` → `jsonb_build_array(...)`；`JSON_OBJECT(...)` → `jsonb_build_object(...)`；`NOW(6)` → `CURRENT_TIMESTAMP`。
16. **patra-ingest**（V0.1.0 共 10 表）：同样的逐项替换。
17. **patra-catalog**（V1.0.0–V1.5.1 共 15 文件 55 表）：
    - 同上替换；
    - **额外**：`cat_publication_identifier.value` → `identifier_value`；
    - **额外**：6 个 `CREATE FULLTEXT INDEX ... WITH PARSER ngram` 语句全部删除；
    - V1.1.0 中 `language_base` 生成列表达式 → `split_part`。
18. **patra-object-storage**（V0.1.0 共 1 表）：同上替换。

**验证**：对每个服务执行 `./gradlew :patra-<service>-infra:test` + 启动 boot `./gradlew :patra-<service>-boot:bootRun`，确认 Flyway 全部 migration 成功。

### M5. Java 应用代码修复

19. `VenueDao.findFilteredJournalPage`（行 196/205/228）：`JSON_UNQUOTE(JSON_EXTRACT(...))` 全部 → `->>`；`JSON_EXTRACT(citation_metrics, '$.hIndex')` → `(citation_metrics->>'hIndex')::numeric`。同步更新 countQuery。
20. `VenueDao.java:94` / `PublicationDao.java:125` LIKE → ILIKE；注释更新。
21. `PublicationDao.java:210`：`ORDER BY citation_count IS NULL, citation_count DESC, publication_year DESC` → `ORDER BY citation_count DESC NULLS LAST, publication_year DESC`。
22. `TaskRunDao.java:55`：`CAST(:checkpointJson AS JSON)` → `:checkpointJson::jsonb`。
23. `PublicationIdentifierEntity.java`：`@Column(name="value")` → `@Column(name="identifier_value")`，Java 属性名 `value` 保持不变。
24. `ProvExprRenderRuleEntity` / `SysDictItemEntity` / `MeshQualifierEntity` 中注释里"MySQL 生成列"/"MySQL JSON 类型"等字样改为 PG 描述。
25. `PublicationRepositoryAdapter` / `RorOrganizationParser` / `PubMedComputedAuthorParser` 中"匹配 MySQL `utf8mb4_0900_ai_ci`"注释改为"用于解析后对象去重的 collation-aware 处理（与 DB 无关）"。
26. `BaseJpaEntity` 中 JSON 注释保持（已写"PG → JSONB，MySQL → JSON"，迁移后可简化）。
27. `commons-core/StringUtils.java` LIKE ESCAPE 注释里"MySQL"字样改为通用描述（PG 行为一致）。

**验证**：`./gradlew test` 全量。

### M6. application*.yml 配置

28. 4 个 `application.yml` 中 `driver-class-name: com.mysql.cj.jdbc.Driver` → `org.postgresql.Driver`。
29. 4 个 `application-dev.yml` + catalog 的 batch 独立数据源共 5 处 `jdbc:mysql://127.0.0.1:13306/<db>?...` → `jdbc:postgresql://127.0.0.1:15432/<db>`（移除所有 MySQL 专用 URL 参数）。
30. 4 个 `application-prod.yml`：保留 `${INGEST_DB_URL}` 等环境变量占位符（实际部署侧由运维注入 PG URL）。
31. object-storage `application-dev.yml:3` 默认值 `jdbc:mysql://localhost:13306/...` → PG URL。
32. 各 boot `application-dev.yml` 中数据库账号 `root` → `postgres`（密码 `123456` 保持）。

**验证**：每个 boot 模块 `./gradlew :patra-<service>-boot:bootRun`。

### M7. 文档同步

33. `.claude/rules/project-info.md`：`MySQL 8.x` → `PostgreSQL 17`。
34. `.claude/agents/test-checker.md`：`MySQLContainerInitializer` → `PostgreSQLContainerInitializer`。
35. 根 `README.md`、`patra-ingest/README.md`、`patra-object-storage/README.md`：技术栈/端口/启动命令统一替换。
36. `linqibin-spring-boot-starter-jpa/README.md`：错误码对照表 1062/1451/1452 → 23505/23503/23502/23514。
37. `linqibin-spring-boot-starter-test/README.md`：MySQLContainerInitializer 用例改为 PG。
38. `linqibin-spring-boot-starter-batch/README.md`：`schema-mysql.sql` / `com.mysql.cj.jdbc.Driver` / `jdbc:mysql://batch-db:3306` 替换。
39. `patra-infra/docker/README.md`：14 处 MySQL 引用全数刷新。
40. `patra-catalog/docs/pubmed-parsing-gap-analysis.md`：描述性提及更新。

---

## 7. 验证计划

每个 M 里程碑 OK 后再进下一个；M4-M6 完成后进行端到端验证：

1. `docker compose -f patra-infra/docker/docker-compose.core.yaml up -d postgres`，确认 4 个业务库 + `patra_batch` 已创建。
2. 依次启动：`patra-registry-boot → patra-ingest-boot → patra-catalog-boot → patra-object-storage-boot`，每个：
   - Flyway 全部 migration 通过（日志确认）；
   - `/actuator/health` 返回 UP；
   - 该服务一条代表性 API 端到端读写成功。
3. `./gradlew test` 全量绿色（含 `*IT`、`*Test`、ArchUnit）。
4. 抽样 native query：手工触发 `VenueDao.findFilteredJournalPage`（带 `oaType` / `sortBy=hIndex` 入参）确认 JSONB 查询正确。
5. 抽样触发器：psql 手工 `UPDATE` 一行不动 `updated_at`，确认 `updated_at` 被触发器刷新；从 JPA 路径 update 一行，确认 `updated_at` 由 Hibernate 设置且触发器不覆盖。

---

## 8. 风险与回滚

- **不可逆性低**：本次迁移不涉及数据迁移，唯一"破坏性"操作是 git 中删除 `.idea/dataSources.xml`（用户本地配置）和 `~/.patra/docker/mysql/` 数据卷（用户手动清理）。
- **回滚路径**：任一里程碑失败，回退到上一个 git commit 即可；MySQL 容器编排在 git 历史中可随时复活；现有 `docker-compose.jobs.yaml` 中保留的 MySQL 容器（给 XXL-Job）可临时复用做对照测试。
- **测试覆盖**：所有 IT 测试通过 Testcontainers 切到 PG，与生产容器版本对齐（`postgres:17`），最小化 dev/prod skew。

---

## 9. 不在范围

- **不迁移 MySQL 中的现有数据**（用户明确说明）。
- **不迁移 XXL-Job 元数据库**：`docker-compose.jobs.yaml` 中专用 MySQL 容器保留；XXL-Job 3.2.0 元数据 schema 由上游维护，迁移成本高且收益低。
- **不重构 schema**：表结构、索引设计、关系建模均按现有 MySQL 蓝图等价翻译，不做优化。
- **不引入 PG 扩展**：pgvector / pg_trgm / pg_bigm / pg_cron / moddatetime 等均不引入；如未来 catalog 需要数据库内全文检索再单独立项。
- **不改变 ICU4J Java 侧去重逻辑**：那是对解析后内存对象的处理，与数据库 collation 解耦。
- **不切换到 PG 分区表 / 物化视图**。
- **不修改 IntelliJ 项目层 DataSource 配置以外的 IDE 设置**。

---

## 10. 实施时需要现场验证的项

| # | 项 | 验证方式 |
|---|---|---|
| 10.1 | Java 25 + virtual threads + pgjdbc 42.7.10 是否存在 connection pinning | 启动 boot 模块后压测一个 IO-bound 接口，jstack 观察 carrier 线程；若 pinning 严重，临时关闭 virtual threads（`spring.threads.virtual.enabled=false`）作为兜底 |
| 10.2 | Boot 4.0.1 BOM 实际锁定的 Flyway / pgjdbc 版本 | `./gradlew :linqibin-spring-boot-starter-jpa:dependencies \| grep -E "flyway\|postgres"` |
| 10.3 | STORED 生成列 + `split_part` 在 PG 17 上实际 IMMUTABLE 接受 | M4 阶段执行 catalog V1.1.0 migration 时确认 |
| 10.4 | Spring Batch 5 实际 `schema-postgresql.sql` 内容与 Boot 4 BOM 版本对齐 | M3 阶段从依赖 jar 中抽取最新版本写入项目 |
| 10.5 | XXL-Job 容器与新 postgres 容器在同一 Docker network 不冲突 | M1 阶段两个 compose 文件分别 up，端口与容器名不撞 |
| 10.6 | catalog `ILIKE` 改写后查询结果与 MySQL 原 collation 行为完全等价 | M5 阶段抽取一份典型用例，比对 MySQL/PG 返回结果集 |

---

## 11. 后续工作（独立立项，不在本 spec）

- catalog 全文检索接入 Elasticsearch（替代被删除的 6 个 FULLTEXT INDEX 的功能）。
- 若未来真需要 DB 内全文检索：评估 pg_trgm / pg_bigm 引入。
- XXL-Job 升级到支持 PG 的版本（如有官方路径）。
- ICU collation 列级启用（若发现 ILIKE 不足以覆盖业务需求）。

---

*以上设计基于 4 个并行调研代理的实测结果：Flyway 脚本全量盘点（81 表）、JPA 持久化层方言依赖审计（21 处命中）、配置/容器/CI 痕迹审计（10 类）、PG17/Hibernate7/Boot4/Flyway11 兼容性官方文档验证（12 条假设全部确认或修正）。*
