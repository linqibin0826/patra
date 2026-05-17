# MySQL → PostgreSQL 17 迁移实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把 `patra-api` 仓库的全部持久化层从 MySQL 8.0 一刀切到 PostgreSQL 17（绿地、不迁数据），同时把 22 个 Flyway 文件合并重整为 16 个干净的 PG baseline。

**Architecture:** 一刀切替换；按 M1→M7 七里程碑分阶段推进，每个里程碑独立可验证。Java 业务行为对外不变；JPA/Hibernate 通过 PG dialect 自动适配；Flyway baseline 从 V1 重新计数。

**Tech Stack:** PostgreSQL 17、`org.postgresql:postgresql` 42.x、Flyway 11 `flyway-database-postgresql`、Hibernate 7.1 `PostgreSQLDialect`、Spring Boot 4.0.1、Spring Batch 5、Testcontainers `postgres:17`。

**Spec：** [`docs/superpowers/specs/2026-05-17-mysql-to-postgresql-migration-design.md`](../specs/2026-05-17-mysql-to-postgresql-migration-design.md)（490 行，57 条决策） — 实施前必读。所有类型/函数/语法翻译规则在 spec §4 与 §5 中。

---

## 并行执行编排（Parallel Execution Matrix）

69 个 task 按真实依赖图拆为 **7 个 Wave**。每个 Wave 内由 **N 个 Track 并行** 跑，Wave 之间是同步屏障（sync barrier）。每个 Track 是一组 **必须由同一 subagent 顺序执行** 的 task。任务标题会附 `[Track: W?-T?]` 标签便于 dispatcher 扫描。

### 整体波次

| Wave | 依赖前置 | 并行 Track 数 | 包含 Tasks | Sync 点 |
|---|---|---:|---|---|
| **W1** | — | **4** | M1.1–M1.7 | `M1.8 smoke test` |
| **W2** | W1 sync | 1 | M2.1–M2.3 | （内部顺序）|
| **W3** | W2 完成 | **7** | M3.1–M3.8 | `M3.9 smoke test` |
| **W4-registry** | W3 sync | **4** | M4.1, M4.2, M4.3, M4.4 | `M4.5 删旧` → `M4.6 validate` |
| **W4-ingest** | W3 sync | **4** | M4.7, M4.8, M4.9, M4.10 | `M4.11 删旧` → `M4.12 validate` |
| **W4-catalog** | W3 sync | **7** | M4.13–M4.19 | `M4.20 删旧` → `M4.21 validate` |
| **W4-storage** | W3 sync | 1 | M4.22 | `M4.23 删+validate` |
| **W5** | W4-相关服务 sync | **9** | M5.1–M5.9 | `M5.10 全量测试` |
| **W6** | M5.10 完成 | **3** | M6.1, M6.2, M6.3 | `M6.4 4-boot bootRun` |
| **W7**（文档）| W3 sync（最早可启动） | **5** | M7.1–M7.5 | （无 sync，独立完成）|
| **FV** | W5 + W6 + W7 全部完成 | sequential | FV.1–FV.7 | （内部顺序）|

**关键并行性**：
- **W1 4 tracks + W3 7 tracks + W4 16 tracks（4+4+7+1） + W5 9 tracks + W6 3 tracks + W7 5 tracks**
- 理论峰值在 **W4**：16 个 subagent 同时写 Flyway 文件（4 服务×4-7 文件，各自完全独立）
- W7 文档面可与 W5/W6 并行启动（只读引用，不改运行时）

### Track 详细分配

#### Wave 1（4 tracks，无 sync）→ M1.8 同步

| Track | Tasks（顺序） | 改动域 |
|---|---|---|
| **W1-T1** | M1.1 → M1.3 | `gradle/libs.versions.toml` → `starter-test/build.gradle.kts`（消费者依赖前者） |
| **W1-T2** | M1.2 | `starter-jpa/build.gradle.kts` |
| **W1-T3** | M1.4 | `starter-batch/build.gradle.kts` |
| **W1-T4** | M1.5 → M1.6 → M1.7 | `patra-infra/docker/` 同目录顺序操作（compose + env + init） |

**Sync barrier**：M1.8 启动 postgres 容器验证 5 库就位（dispatcher 单任务）。

#### Wave 2（单 Track，sequential）

| Track | Tasks（顺序） | 改动域 |
|---|---|---|
| **W2-T1** | M2.1 → M2.2 → M2.3 | starter-test 容器类重命名（强顺序：枚举 → 基类 → 子类引用） |

> **不可并行原因**：M2.2 引用 `ContainerType.POSTGRESQL`（M2.1 定义）；M2.3 引用 `PostgreSQLContainerInitializer`（M2.2 定义）。

#### Wave 3（7 tracks，无 sync）→ M3.9 同步

| Track | Tasks（顺序） | 改动域 |
|---|---|---|
| **W3-T1** | M3.1 | `JpaErrorMappingContributor.java` 错误码 switch 删除 |
| **W3-T2** | M3.2 → M3.3 | 抽取 schema-postgresql.sql → 切换 BatchSchemaInitializer 常量 |
| **W3-T3** | M3.4 | `starter-batch` test `application-test.yml` URL |
| **W3-T4** | M3.5 | `DualDataSourceIT.java` URL |
| **W3-T5** | M3.6 | `BatchDataSourceConfigurationTest.java` 容器类型 |
| **W3-T6** | M3.7 | `BatchPropertiesTest.java` URL |
| **W3-T7** | M3.8 | `HibernatePropertiesCustomizer.java` metadata 优化 |

**Sync barrier**：M3.9 跑 starter-jpa + starter-batch 测试套件（dispatcher 单任务）。

#### Wave 4（16 tracks 跨 4 服务）

每个服务内的 V*.sql 文件**完全独立**（各自 `CREATE TABLE` + 触发器，不引用其他 V*.sql 的内容），所以可并行写。

##### W4-registry（4 tracks → M4.5 删旧 → M4.6 validate）

| Track | Task | 文件 | 备注 |
|---|---|---|---|
| W4r-T1 | M4.1 | V1__init_registry_schema.sql | 含 `set_updated_at()` 函数定义（registry 服务的副本） |
| W4r-T2 | M4.2 | V2__seed_provenance_expr.sql | |
| W4r-T3 | M4.3 | V3__seed_dict_country.sql | |
| W4r-T4 | M4.4 | V4__seed_dict_language.sql | |

##### W4-ingest（4 tracks → M4.11 删旧 → M4.12 validate）

| Track | Task | 文件 | 备注 |
|---|---|---|---|
| W4i-T1 | M4.7 | V1__init_ingest_scheduling.sql | 含 ingest 服务的 `set_updated_at()` 副本 |
| W4i-T2 | M4.8 | V2__init_ingest_execution.sql | 含 partial index |
| W4i-T3 | M4.9 | V3__init_ingest_cursor.sql | |
| W4i-T4 | M4.10 | V4__init_ingest_outbox.sql | 含 2 partial index |

##### W4-catalog（7 tracks → M4.20 删旧 → M4.21 validate）

| Track | Task | 文件 | 备注 |
|---|---|---|---|
| W4c-T1 | M4.13 | V1__create_venue.sql | 含 catalog 服务的 `set_updated_at()` 副本 |
| W4c-T2 | M4.14 | V2__create_publication.sql | 含 value→identifier_value 重命名 + 删 2 FULLTEXT |
| W4c-T3 | M4.15 | V3__create_author.sql | 含 2 函数索引 |
| W4c-T4 | M4.16 | V4__create_organization.sql | 含 value→name_value 重命名 |
| W4c-T5 | M4.17 | V5__create_mesh.sql | 删 3 FULLTEXT |
| W4c-T6 | M4.18 | V6__create_keyword.sql | 删 1 FULLTEXT + 1 冗余索引 |
| W4c-T7 | M4.19 | V7__create_funding.sql | |

##### W4-storage（1 track）

| Track | Tasks（顺序） | 文件 |
|---|---|---|
| W4s-T1 | M4.22 → M4.23 | V1__init_storage_schema.sql + 删旧 + validate |

**重要**：每个服务的 W4 内部 4-7 个 track 并行 → 各自服务的 sync（删旧 + validate）独立完成。**4 个服务的 W4 可以完全并行**（彼此 PG 库独立、Java infra 模块独立）。

#### Wave 5（9 tracks → M5.10 同步）

每个 track 的依赖在备注列写明。

| Track | Task | 依赖 | 改动 |
|---|---|---|---|
| W5-T1 | M5.1 | W4-catalog 完成 | `PublicationIdentifierEntity.java` value→identifier_value |
| W5-T2 | M5.2 | W4-catalog 完成 | `OrganizationNameEntity.java` value→name_value（含 @UniqueConstraint + @Index） |
| W5-T3 | M5.3 | W4-catalog 完成 | `VenueDao.java` JSON 函数 → PG 操作符 |
| W5-T4 | M5.4 | W4-catalog 完成 | `VenueDao` + `PublicationDao` LIKE → ILIKE |
| W5-T5 | M5.5 | W4-catalog 完成 | `PublicationDao` ORDER BY NULLS LAST |
| W5-T6 | M5.6 | W4-ingest 完成 | `TaskRunDao` CAST AS jsonb |
| W5-T7 | M5.7 | 无 | 6 文件 Java 注释批量更新（与 schema 无关）|
| W5-T8 | M5.8 | 无 | 3 文件 ICU4J 注释更新 |
| W5-T9 | M5.9 | W4-catalog 完成 + PG 容器 | MeshScr/MeshDescriptor IT 断言更新 |

**Sync barrier**：M5.10 `./gradlew test` 全量。

#### Wave 6（3 tracks → M6.4 同步）

| Track | Task | 改动 |
|---|---|---|
| W6-T1 | M6.1 | 4 个 application.yml driver-class-name |
| W6-T2 | M6.2 | 5 处 application-dev.yml URL + 账号 |
| W6-T3 | M6.3 | 删除 Flyway baseline 配置 |

**Sync barrier**：M6.4 4 服务 bootRun 健康检查。

#### Wave 7（5 tracks，独立完成，无 sync）

可以**与 W5 / W6 并行启动**（最早 W3 sync 后即可），完全独立。

| Track | Task | 改动 |
|---|---|---|
| W7-T1 | M7.1 | `.claude/rules/project-info.md` + `.claude/agents/test-checker.md` + `.codex/agents/test-checker.toml` + `patra-infra/.claude/CLAUDE.md` |
| W7-T2 | M7.2 | 根 README + ingest README + object-storage README |
| W7-T3 | M7.3 | starter-jpa/test/batch README |
| W7-T4 | M7.4 | `patra-infra/docker/README.md` |
| W7-T5 | M7.5 | catalog docs/pubmed-parsing-gap-analysis.md |

#### Final Verification（sequential）

| Step | Task | 依赖 |
|---|---|---|
| 1 | FV.1 | W5 sync + W6 sync 全部完成 |
| 2 | FV.2 | FV.1 通过 |
| 3 | FV.3 | FV.2 通过（需 PG 业务库已建） |
| 4 | FV.4 | FV.2 通过 |
| 5 | FV.5 | FV.2 通过 |
| 6 | FV.6 | FV.2 通过 |
| 7 | FV.7 | 全部完成后做 git log 巡视 |

### Dispatcher 实操建议

**Subagent-Driven 执行（推荐）**：

```
Wave W1 → dispatch 4 subagents 并行：T1, T2, T3, T4
  ↓ 等全部完成后由 dispatcher 跑 M1.8 同步
Wave W2 → dispatch 1 subagent（M2.1→M2.2→M2.3 顺序）
  ↓
Wave W3 → dispatch 7 subagents 并行
  ↓ 等全部完成后由 dispatcher 跑 M3.9 同步
Wave W4 → dispatch 4+4+7+1 = 16 subagents 并行
  ↓ 4 个服务各自的 sync 独立完成
Wave W5 + W7 → dispatch 9 + 5 = 14 subagents 并行（W5 需要 W4 服务级 sync 完成，W7 完全无依赖）
  ↓ M5.10 同步
Wave W6 → dispatch 3 subagents 并行
  ↓ M6.4 同步
Final Verification → 串行
```

**Inline 执行**：失去并行优势但仍可按 Wave 顺序推进，每个 Wave 内可批量修改多个文件（无并发，但保持依赖正确）。

---

## File Structure

### 新建文件（10）

```
patra-api/
  linqibin-spring-boot-starter-batch/src/main/resources/db/batch/
    schema-postgresql.sql                 # 从 Spring Batch 5 jar 抽取
  linqibin-spring-boot-starter-test/src/main/java/.../initializer/
    PostgreSQLContainerInitializer.java   # 重命名自 MySQLContainerInitializer

  patra-registry/patra-registry-infra/src/main/resources/db/migration/
    V1__init_registry_schema.sql          # 合并自 V1.0.0 + V1.0.1 + V1.0.2
    V2__seed_provenance_expr.sql          # 重写自 V1.1.0
    V3__seed_dict_country.sql             # 合并 V1.2.0 + V1.4.0(country) + V1.4.1
    V4__seed_dict_language.sql            # 合并 V1.3.0 + V1.4.0(lang) + V1.4.2

  patra-ingest/patra-ingest-infra/src/main/resources/db/migration/
    V1__init_ingest_scheduling.sql        # 拆自 V0.1.0 第 1-3 段
    V2__init_ingest_execution.sql         # 第 4-6 段 + partial index
    V3__init_ingest_cursor.sql            # 第 7-8 段
    V4__init_ingest_outbox.sql            # 第 9-10 段 + partial index

  patra-catalog/patra-catalog-infra/src/main/resources/db/migration/
    V1__create_venue.sql        # venue + rating, 11 张表
    V2__create_publication.sql  # publication 集大成, 13 张表
    V3__create_author.sql       # author + relation, 5 张表 + 函数索引
    V4__create_organization.sql # organization + investigator, 8 张表
    V5__create_mesh.sql         # mesh + mesh SCR, 15 张表
    V6__create_keyword.sql      # 2 张表
    V7__create_funding.sql      # 1 张表

  patra-object-storage/patra-object-storage-infra/src/main/resources/db/migration/
    V1__init_storage_schema.sql # 1 张表

patra-infra/docker/postgres/init-scripts/
  02-create-databases.sql       # 重写自 docker/mysql/init-scripts/02-create-batch-db.sql
```

### 修改文件（已知 30+ 个）

构建：`gradle/libs.versions.toml`、`linqibin-spring-boot-starter-{jpa,test,batch}/build.gradle.kts`（3 个）

代码：
- `linqibin-spring-boot-starter-jpa/src/main/java/.../JpaErrorMappingContributor.java`
- `linqibin-spring-boot-starter-jpa/src/main/java/.../HibernatePropertiesCustomizer.java`
- `linqibin-spring-boot-starter-batch/src/main/java/.../BatchSchemaInitializer.java`
- `linqibin-spring-boot-starter-test/src/main/java/.../ContainerType.java`
- 5 个 `*MySQLContainerInitializer.java`（catalog/ingest 各 1-2 个）
- 4 个 batch test 类（`BatchDataSourceConfigurationTest`、`BatchAutoConfigurationIT`、`DualDataSourceIT`、`BatchPropertiesTest`）
- `patra-catalog-infra/.../PublicationIdentifierEntity.java`
- `patra-catalog-infra/.../OrganizationNameEntity.java`
- `patra-catalog-infra/.../VenueDao.java`
- `patra-catalog-infra/.../PublicationDao.java`
- `patra-ingest-infra/.../TaskRunDao.java`
- 6 个 Java 文件注释更新（`BaseJpaEntity`/`MeshQualifierEntity`/`ProvExprRenderRuleEntity`/`SysDictItemEntity`/`PublicationRepositoryAdapter`/`RorOrganizationParser`/`PubMedComputedAuthorParser`/`StringUtils`）
- 2 个 collation IT（`MeshScrRepositoryAdapterIT`、`MeshDescriptorRepositoryAdapterIT`）

容器：`patra-infra/docker/docker-compose.core.yaml`、`.env.dev`

配置：12 个 `application*.yml`（4 boot × 3 profile 主要 main/dev + test）

文档：14+ Markdown 文件（多个 README + `.claude/agents/test-checker.md` + `.codex/agents/test-checker.toml` + `.claude/rules/project-info.md` + `patra-infra/.claude/CLAUDE.md`）

### 删除文件（22）

- registry: V1.0.0/V1.0.1/V1.0.2/V1.1.0/V1.2.0/V1.3.0/V1.4.0/V1.4.1/V1.4.2（9）
- ingest: V0.1.0__init_ingest_schema.sql（1）
- catalog: V1.0.0 ~ V1.5.1 共 15
- object-storage: V0.1.0__init_storage_schema.sql（1）
- starter-batch: `src/main/resources/db/batch/schema-mysql.sql`（1）
- patra-infra: `docker/mysql/init-scripts/02-create-batch-db.sql`（1）

---

## 工作前置

### Worktree

强烈建议在独立 worktree 内推进整个迁移：

```bash
cd /Users/linqibin/Projects/Products/Patra/patra-api
git worktree add ../patra-api-pg-migration -b feat/postgresql-migration
cd ../patra-api-pg-migration
```

### 前置查询

实施前先获取实际 BOM 锁定版本（spec §10.2 现场验证项）：

```bash
./gradlew :linqibin-spring-boot-starter-jpa:dependencies | grep -E "flyway|postgres" | head -20
```

记录实际 `postgresql:42.x.x` 和 `flyway-database-postgresql:11.x.x` 版本，确认 Boot 4.0.1 BOM 已托管。

---

## Milestone M1：基础设施层（Gradle 依赖 + 容器编排）

**目标**：把驱动/插件/容器全部换成 PG。完成后 `docker compose up postgres` 起得来，5 个业务库就位。

**M1 验证**：`docker compose -f patra-infra/docker/docker-compose.core.yaml up -d postgres && docker exec patra-postgres psql -U postgres -c '\l'` 列出 5 个数据库。

---

### Task M1.1 `[Track: W1-T1]`：libs.versions.toml 切换 Testcontainers Module

**Files:**
- Modify: `gradle/libs.versions.toml:153`

- [ ] **Step 1：替换 testcontainers-mysql 为 testcontainers-postgresql**

把 `gradle/libs.versions.toml` 第 153 行：

```toml
testcontainers-mysql = { module = "org.testcontainers:mysql" }
```

改成：

```toml
testcontainers-postgresql = { module = "org.testcontainers:postgresql" }
```

- [ ] **Step 2：验证 Gradle 解析无报错**

```bash
./gradlew help -q
```

期望：无 dependency 错误。本任务暂未引用新坐标，所以 Gradle 不会去 download，只验证 TOML 语法正确。

- [ ] **Step 3：Commit**

```bash
git add gradle/libs.versions.toml
git commit -m "build: libs.versions.toml 切换 Testcontainers MySQL 模块为 PostgreSQL"
```

---

### Task M1.2 `[Track: W1-T2]`：starter-jpa build.gradle.kts 切换驱动与 Flyway PG 模块

**Files:**
- Modify: `linqibin-spring-boot-starter-jpa/build.gradle.kts:30-35`

- [ ] **Step 1：替换 Flyway 与 JDBC 驱动**

把第 30-35 行：

```kotlin
// Flyway 数据库迁移
api("org.springframework.boot:spring-boot-starter-flyway")
api("org.flywaydb:flyway-mysql")

// MySQL 驱动
api("com.mysql:mysql-connector-j")
```

改成：

```kotlin
// Flyway 数据库迁移
api("org.springframework.boot:spring-boot-starter-flyway")
api("org.flywaydb:flyway-database-postgresql")

// PostgreSQL 驱动
api("org.postgresql:postgresql")
```

- [ ] **Step 2：编译该模块**

```bash
./gradlew :linqibin-spring-boot-starter-jpa:compileJava
```

期望：BUILD SUCCESSFUL。Gradle 会从 Boot 4.0.1 BOM 解析 `flyway-database-postgresql:11.x` 与 `postgresql:42.x`。

- [ ] **Step 3：Commit**

```bash
git add linqibin-spring-boot-starter-jpa/build.gradle.kts
git commit -m "build(starter-jpa): 切换 Flyway 与 JDBC 驱动到 PostgreSQL"
```

---

### Task M1.3 `[Track: W1-T1]`：starter-test build.gradle.kts 切换

**Files:**
- Modify: `linqibin-spring-boot-starter-test/build.gradle.kts:42, 59, 87`

- [ ] **Step 1：替换 Flyway / Testcontainers / JDBC 三处**

第 42 行：

```kotlin
api("org.flywaydb:flyway-mysql")
```

→

```kotlin
api("org.flywaydb:flyway-database-postgresql")
```

第 59 行：

```kotlin
api(libs.testcontainers.mysql)
```

→

```kotlin
api(libs.testcontainers.postgresql)
```

第 87 行：

```kotlin
api("com.mysql:mysql-connector-j")
```

→

```kotlin
api("org.postgresql:postgresql")
```

同时更新该文件 41 / 85 行附近的注释（"TestContainers MySQL 需要" → "TestContainers PostgreSQL 需要"）。

- [ ] **Step 2：编译该模块**

```bash
./gradlew :linqibin-spring-boot-starter-test:compileJava
```

期望：BUILD SUCCESSFUL。

- [ ] **Step 3：Commit**

```bash
git add linqibin-spring-boot-starter-test/build.gradle.kts
git commit -m "build(starter-test): 切换 Flyway/Testcontainers/JDBC 到 PostgreSQL"
```

---

### Task M1.4 `[Track: W1-T3]`：starter-batch build.gradle.kts 切换 compileOnly 驱动

**Files:**
- Modify: `linqibin-spring-boot-starter-batch/build.gradle.kts:32-33`

- [ ] **Step 1：替换 MySQL JDBC 驱动**

第 32-33 行：

```kotlin
// MySQL JDBC 驱动（可选，用于 JobRepository 元数据存储）
compileOnly("com.mysql:mysql-connector-j")
```

→

```kotlin
// PostgreSQL JDBC 驱动（可选，用于 JobRepository 元数据存储）
compileOnly("org.postgresql:postgresql")
```

- [ ] **Step 2：编译验证**

```bash
./gradlew :linqibin-spring-boot-starter-batch:compileJava
```

期望：BUILD SUCCESSFUL（compileOnly 不影响测试编译，测试中 BatchSchemaInitializer 引用的 `schema-mysql.sql` 暂时仍然存在，M3 才会删）。

- [ ] **Step 3：Commit**

```bash
git add linqibin-spring-boot-starter-batch/build.gradle.kts
git commit -m "build(starter-batch): compileOnly 切换到 PostgreSQL 驱动"
```

---

### Task M1.5 `[Track: W1-T4]`：重写 docker-compose.core.yaml mysql 服务为 postgres

**Files:**
- Modify: `../patra-infra/docker/docker-compose.core.yaml:8-33`（mysql 服务整段）

注意：`patra-infra` 是同级仓库，不在 `patra-api` 内。

- [ ] **Step 1：读取当前 mysql 服务定义**

参考 spec §5.5 的目标态 yaml。

- [ ] **Step 2：替换 mysql 服务整段为 postgres 服务**

把 `docker-compose.core.yaml` 第 8-33 行（mysql 服务块）：

```yaml
mysql:
  image: mysql:8.0.36
  container_name: patra-mysql
  restart: unless-stopped
  ports: [ "13306:3306" ]
  environment:
    MYSQL_ROOT_PASSWORD: ${MYSQL_ROOT_PASSWORD:-123456}
    TZ: Asia/Shanghai
  command: >
    --default-authentication-plugin=caching_sha2_password
    --character-set-server=utf8mb4
    --collation-server=utf8mb4_0900_ai_ci
    --lower_case_table_names=1
    --explicit_defaults_for_timestamp=ON
    --max_connections=300
  volumes:
    - ${HOME}/.patra/docker/mysql/data:/var/lib/mysql
    - ${HOME}/.patra/docker/mysql/conf.d:/etc/mysql/conf.d
    - ${HOME}/.patra/docker/mysql/init:/docker-entrypoint-initdb.d
  healthcheck:
    test: [ "CMD-SHELL", "mysqladmin ping -uroot -p$$MYSQL_ROOT_PASSWORD -h 127.0.0.1 --silent" ]
    interval: 10s
    timeout: 5s
    retries: 10
```

替换为：

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

- [ ] **Step 3：Commit**

```bash
cd ../patra-infra
git add docker/docker-compose.core.yaml
git commit -m "feat(docker): 替换 mysql 服务为 postgres:17"
cd -
```

---

### Task M1.6 `[Track: W1-T4]`：重写 .env.dev 为 POSTGRES_*

**Files:**
- Modify: `../patra-infra/docker/.env.dev`

- [ ] **Step 1：替换 MYSQL_* 变量**

把所有：

```
MYSQL_ROOT_PASSWORD=123456
MYSQL_PORT=13306
```

替换为：

```
POSTGRES_PASSWORD=123456
POSTGRES_PORT=15432
```

如果 `.env.example` 中有 MYSQL_* 示例也一并改。

- [ ] **Step 2：Commit**

```bash
cd ../patra-infra
git add docker/.env.dev docker/.env.example 2>/dev/null || git add docker/.env.dev
git commit -m "feat(docker): .env.dev MYSQL_* 切换为 POSTGRES_*"
cd -
```

---

### Task M1.7 `[Track: W1-T4]`：重写 init 脚本 → postgres/init-scripts/02-create-databases.sql

**Files:**
- Delete: `../patra-infra/docker/mysql/init-scripts/02-create-batch-db.sql`
- Create: `../patra-infra/docker/postgres/init-scripts/02-create-databases.sql`

- [ ] **Step 1：创建 PG init 脚本**

```bash
mkdir -p ../patra-infra/docker/postgres/init-scripts
```

写入 `../patra-infra/docker/postgres/init-scripts/02-create-databases.sql`：

```sql
-- Patra 业务库 + Spring Batch 元数据库初始化
-- 由 postgres 镜像 entrypoint 在容器首次创建时执行一次
CREATE DATABASE patra_registry;
CREATE DATABASE patra_ingest;
CREATE DATABASE patra_catalog;
CREATE DATABASE patra_storage;
CREATE DATABASE patra_batch;
```

- [ ] **Step 2：删除旧 MySQL init 脚本**

```bash
git rm ../patra-infra/docker/mysql/init-scripts/02-create-batch-db.sql
# 如果 docker/mysql/ 目录变空，一并删除
rmdir ../patra-infra/docker/mysql/init-scripts 2>/dev/null
rmdir ../patra-infra/docker/mysql 2>/dev/null
```

- [ ] **Step 3：Commit**

```bash
cd ../patra-infra
git add docker/postgres/init-scripts/02-create-databases.sql
git commit -m "feat(docker): 创建 postgres init 脚本（5 个业务库）+ 删除旧 mysql init"
cd -
```

---

### Task M1.8 `[Track: W1-sync]`：M1 smoke test — 起 postgres 容器验证 5 库就位

**Files:** 无文件改动，仅验证

- [ ] **Step 1：清理可能残留的旧数据卷**

```bash
docker stop patra-mysql 2>/dev/null
docker rm patra-mysql 2>/dev/null
# 注意：~/.patra/docker/mysql/data 由开发者按需手动 rm；自动化不要碰
```

- [ ] **Step 2：启动 postgres 容器**

```bash
cd ../patra-infra
docker compose -f docker/docker-compose.core.yaml up -d postgres
sleep 10  # 等首次启动 + initdb 完成
```

- [ ] **Step 3：验证 5 库已建**

```bash
docker exec patra-postgres psql -U postgres -c '\l'
```

期望输出包含：`patra_registry`、`patra_ingest`、`patra_catalog`、`patra_storage`、`patra_batch`。

- [ ] **Step 4：验证 healthcheck 通过**

```bash
docker inspect --format='{{.State.Health.Status}}' patra-postgres
```

期望：`healthy`。

如果验证失败，回退 commits 后排查。

---

## Milestone M2：starter-test 容器初始化器

**目标**：把 Testcontainers MySQL → PostgreSQL，让所有 IT 测试能跑在 PG 容器上。

**M2 验证**：`./gradlew :linqibin-spring-boot-starter-test:test` 通过。

---

### Task M2.1 `[Track: W2-T1]`：ContainerType 枚举 MYSQL → POSTGRESQL

**Files:**
- Modify: `linqibin-spring-boot-starter-test/src/main/java/dev/linqibin/starter/test/container/ContainerType.java`

- [ ] **Step 1：重命名枚举值**

打开 `ContainerType.java`，把 `MYSQL` 改为 `POSTGRESQL`（如还有其他容器类型如 ROCKETMQ、MINIO 保留不动）。

- [ ] **Step 2：编译检查（预期失败，因为 MySQLContainerInitializer 还引用旧名）**

```bash
./gradlew :linqibin-spring-boot-starter-test:compileJava
```

期望：编译报错 `MySQLContainerInitializer` 引用 `ContainerType.MYSQL` 找不到。这是预期，下一任务 M2.2 修复。

- [ ] **Step 3：暂不 commit**（与 M2.2 一并提交，保持 build 不断）

---

### Task M2.2 `[Track: W2-T1]`：重写 MySQLContainerInitializer → PostgreSQLContainerInitializer

**Files:**
- Delete: `linqibin-spring-boot-starter-test/src/main/java/dev/linqibin/starter/test/container/initializer/MySQLContainerInitializer.java`
- Create: `linqibin-spring-boot-starter-test/src/main/java/dev/linqibin/starter/test/container/initializer/PostgreSQLContainerInitializer.java`

- [ ] **Step 1：创建新文件 PostgreSQLContainerInitializer.java**

写入：

```java
package dev.linqibin.starter.test.container.initializer;

import dev.linqibin.starter.test.container.ContainerRegistry;
import dev.linqibin.starter.test.container.ContainerType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;

/// PostgreSQL 容器初始化器（配置化版本）。
///
/// 提供 PostgreSQL 17 容器的单例管理和动态配置注入。
/// 通过子类化支持不同服务使用不同的数据库名。
///
/// ### 核心特性
///
/// - **JVM 内单例**：同一 JVM 进程内所有测试共享同一个 PG 容器实例
/// - **配置化数据库名**：子类通过重写 `getDatabaseName()` 指定数据库名
/// - **动态配置注入**：自动注入 JDBC 连接配置到 Spring 测试上下文
/// - **线程安全**：使用双重检查锁模式确保并发安全
///
/// ### 使用方式
///
/// 方式一：直接使用（默认数据库名 `patra_test`）
///
/// ```java
/// @SpringBootTest
/// @ContextConfiguration(initializers = PostgreSQLContainerInitializer.class)
/// class SomeRepositoryIT {
///     // ...
/// }
/// ```
///
/// 方式二：子类化指定数据库名
///
/// ```java
/// public class CatalogPostgreSQLContainerInitializer extends PostgreSQLContainerInitializer {
///     @Override
///     protected String getDatabaseName() {
///         return "patra_catalog";
///     }
/// }
/// ```
///
/// ### 容器配置
///
/// - **镜像版本**：postgres:17（与生产环境一致）
/// - **用户名/密码**：postgres / 123456
/// - **容器复用策略**：JVM 内复用，JVM 间不复用
///
/// @author linqibin
/// @since 0.1.0
/// @see ContainerRegistry
/// @see ApplicationContextInitializer
public class PostgreSQLContainerInitializer
    implements ApplicationContextInitializer<ConfigurableApplicationContext> {

  private static final Logger log = LoggerFactory.getLogger(PostgreSQLContainerInitializer.class);

  /// PostgreSQL 镜像版本（与生产环境一致）。
  private static final String POSTGRES_IMAGE = "postgres:17";

  /// 默认用户名。
  private static final String DEFAULT_USERNAME = "postgres";

  /// 默认密码。
  private static final String DEFAULT_PASSWORD = "123456";

  /// 初始化状态标志。
  private static volatile boolean initialized = false;

  /// 同步锁对象。
  private static final Object LOCK = new Object();

  /// 获取数据库名称。
  ///
  /// 子类可重写此方法以使用不同的数据库名。
  /// 默认返回 `patra_test`。
  ///
  /// @return 数据库名称
  protected String getDatabaseName() {
    return "patra_test";
  }

  /// 初始化 PostgreSQL 容器（线程安全）。
  private void initializeContainer() {
    if (!initialized) {
      synchronized (LOCK) {
        if (!initialized) {
          String databaseName = getDatabaseName();

          log.info("========================================");
          log.info("初始化 PostgreSQL TestContainer (线程: {})", Thread.currentThread().getName());
          log.info("  - 数据库名: {}", databaseName);
          log.info("========================================");

          PostgreSQLContainer<?> postgres =
              new PostgreSQLContainer<>(POSTGRES_IMAGE)
                  .withDatabaseName(databaseName)
                  .withUsername(DEFAULT_USERNAME)
                  .withPassword(DEFAULT_PASSWORD)
                  .withReuse(false); // 不跨 JVM 进程复用，避免配置缓存污染

          postgres.start();

          ContainerRegistry.register(ContainerType.POSTGRESQL, postgres);

          log.info("PostgreSQL 容器已启动");
          log.info("  - JDBC URL: {}", postgres.getJdbcUrl());
          log.info("  - 用户名: {}", postgres.getUsername());
          log.info("  - 数据库名: {}", postgres.getDatabaseName());
          log.info("========================================");

          initialized = true;
        } else {
          log.debug("PostgreSQL 容器已由其他线程初始化，复用现有实例");
        }
      }
    }
  }

  /// 初始化 Spring 应用上下文，注入 PostgreSQL 动态配置。
  ///
  /// 注入的配置项：
  ///
  /// - `spring.datasource.url`: JDBC URL (包含动态端口)
  /// - `spring.datasource.username`: postgres
  /// - `spring.datasource.password`: 123456
  /// - `spring.datasource.driver-class-name`: org.postgresql.Driver
  ///
  /// @param applicationContext Spring 应用上下文
  @Override
  public void initialize(ConfigurableApplicationContext applicationContext) {
    initializeContainer();

    PostgreSQLContainer<?> postgres =
        ContainerRegistry.get(ContainerType.POSTGRESQL, PostgreSQLContainer.class);

    if (postgres == null) {
      throw new IllegalStateException("PostgreSQL 容器未正确注册");
    }

    log.info("注入 PostgreSQL 动态配置到 Spring 上下文");

    TestPropertyValues.of(
            "spring.datasource.url=" + postgres.getJdbcUrl(),
            "spring.datasource.username=" + postgres.getUsername(),
            "spring.datasource.password=" + postgres.getPassword(),
            "spring.datasource.driver-class-name=org.postgresql.Driver")
        .applyTo(applicationContext.getEnvironment());

    log.info("PostgreSQL 动态配置注入完成");
  }

  /// 获取 PostgreSQL 容器实例（供测试代码访问）。
  ///
  /// @return PostgreSQL 容器实例，如果未初始化则返回 null
  public static PostgreSQLContainer<?> getPostgreSQLContainer() {
    return ContainerRegistry.get(ContainerType.POSTGRESQL, PostgreSQLContainer.class);
  }
}
```

- [ ] **Step 2：删除旧文件**

```bash
git rm linqibin-spring-boot-starter-test/src/main/java/dev/linqibin/starter/test/container/initializer/MySQLContainerInitializer.java
```

- [ ] **Step 3：编译验证**

```bash
./gradlew :linqibin-spring-boot-starter-test:compileJava
```

期望：BUILD SUCCESSFUL。如果其他模块（catalog/ingest infra/boot）的测试代码引用了旧类名，会在 `compileTestJava` 时报错，下一任务 M2.3 修复。

- [ ] **Step 4：Commit（与 M2.1 合并）**

```bash
git add linqibin-spring-boot-starter-test/src/main/java/dev/linqibin/starter/test/container/
git commit -m "refactor(starter-test): MySQLContainerInitializer → PostgreSQLContainerInitializer"
```

---

### Task M2.3 `[Track: W2-T1]`：catalog/ingest 子类重命名

**Files:**
- Rename + 内容更新（5 个文件）：
  - `patra-catalog/patra-catalog-infra/src/test/java/dev/linqibin/patra/catalog/infra/config/CatalogMySQLContainerInitializer.java` → `CatalogPostgreSQLContainerInitializer.java`
  - `patra-catalog/patra-catalog-boot/src/test/java/.../config/CatalogMySQLContainerInitializer.java` → 同上
  - `patra-ingest/patra-ingest-infra/src/test/java/dev/linqibin/patra/ingest/infra/config/IngestMySQLContainerInitializer.java` → `IngestPostgreSQLContainerInitializer.java`
  - `patra-ingest/patra-ingest-boot/src/test/java/.../config/IngestMySQLContainerInitializer.java` → 同上
  - `patra-registry-infra/src/test/java/dev/linqibin/patra/registry/infra/config/RegistryMySQLContainerInitializer.java`（如存在）→ `RegistryPostgreSQLContainerInitializer.java`

- [ ] **Step 1：定位所有 MySQLContainerInitializer 子类**

```bash
grep -rln "extends MySQLContainerInitializer\|MySQLContainerInitializer\.class" --include="*.java" patra-*/src/test/
```

- [ ] **Step 2：对每个子类执行 3 处替换**

文件内：
- `extends MySQLContainerInitializer` → `extends PostgreSQLContainerInitializer`
- `import dev.linqibin.starter.test.container.initializer.MySQLContainerInitializer;` → `import dev.linqibin.starter.test.container.initializer.PostgreSQLContainerInitializer;`
- 类名 `XxxMySQLContainerInitializer` → `XxxPostgreSQLContainerInitializer`

引用方文件内（`@ContextConfiguration(initializers = XxxMySQLContainerInitializer.class)`）：同步改类名。

文件本身重命名（git mv）：

```bash
git mv patra-catalog/patra-catalog-infra/src/test/java/dev/linqibin/patra/catalog/infra/config/CatalogMySQLContainerInitializer.java patra-catalog/patra-catalog-infra/src/test/java/dev/linqibin/patra/catalog/infra/config/CatalogPostgreSQLContainerInitializer.java
# 对其他 4 个文件同样操作
```

- [ ] **Step 3：搜索所有引用方修正 import**

```bash
grep -rln "CatalogMySQLContainerInitializer\|IngestMySQLContainerInitializer\|RegistryMySQLContainerInitializer" --include="*.java" patra-*/src/test/
```

每个 IT 测试类都需要更新 `@ContextConfiguration(initializers = ...)` 与 `import` 语句。

- [ ] **Step 4：编译所有测试代码**

```bash
./gradlew compileTestJava
```

期望：BUILD SUCCESSFUL。

- [ ] **Step 5：Commit**

```bash
git add -A
git commit -m "refactor: catalog/ingest 子类与引用 MySQLContainerInitializer → PostgreSQL"
```

---

## Milestone M3：starter-jpa 错误映射 + starter-batch schema + Hibernate 优化

**目标**：清理 Java 层的 MySQL 强依赖（错误码/Batch schema/Hibernate 配置/测试 hardcoded URL）。

**M3 验证**：`./gradlew :linqibin-spring-boot-starter-jpa:test :linqibin-spring-boot-starter-batch:test`。

---

### Task M3.1 `[Track: W3-T1]`：JpaErrorMappingContributor 删除 MySQL 错误码 switch

**Files:**
- Modify: `linqibin-spring-boot-starter-jpa/src/main/java/dev/linqibin/starter/jpa/error/contributor/JpaErrorMappingContributor.java:188-225`

- [ ] **Step 1：定位 mapSqlExceptions 方法的 MySQL switch 块**

打开文件，找到 `private Optional<ErrorCodeLike> mapSqlExceptions(Throwable exception)` 方法。
该方法约第 188-225 行。删除 MySQL 错误码 switch 块（即 spec §4.29 描述的 1062/1451/1452 分支）。

把：

```java
if (exception instanceof SQLException sqlEx) {
  // MySQL 特定错误码映射
  Optional<ErrorCodeLike> mysqlResult =
      switch (sqlEx.getErrorCode()) {
        case 1062 -> { // ER_DUP_ENTRY: 唯一键的重复条目
          log.debug("将 MySQL 重复条目错误 ({}) 映射为冲突", sqlEx.getErrorCode(), sqlEx);
          yield Optional.of(http.CONFLICT());
        }
        case 1451, 1452 -> { // ER_ROW_IS_REFERENCED_2, ER_NO_REFERENCED_ROW_2: 外键约束
          log.debug("将 MySQL 外键约束错误 ({}) 映射为冲突", sqlEx.getErrorCode(), sqlEx);
          yield Optional.of(http.CONFLICT());
        }
        default -> Optional.empty();
      };
  if (mysqlResult.isPresent()) {
    return mysqlResult;
  }

  // SQLState 映射
  String sqlState = sqlEx.getSQLState();
  ...
```

替换为：

```java
if (exception instanceof SQLException sqlEx) {
  // SQLState 映射（PG 完整性错误已由 Spring Data 包装为 DataIntegrityViolationException
  // 并在 mapSpringDataExceptions() 中处理；这里只兜底连接/超时类错误）
  String sqlState = sqlEx.getSQLState();
  ...
```

- [ ] **Step 2：执行该模块测试**

```bash
./gradlew :linqibin-spring-boot-starter-jpa:test
```

期望：全部通过（现有测试不应依赖 MySQL 错误码分支的行为）。

- [ ] **Step 3：同步 README 错误码对照表**

修改 `linqibin-spring-boot-starter-jpa/README.md` 中错误码对照（搜索 `1062` / `1451` / `1452`）：
- `1062 ER_DUP_ENTRY` → `23505 unique_violation`
- `1451/1452 外键` → `23503 foreign_key_violation`
- 增加：`23502 not_null_violation`、`23514 check_violation`

- [ ] **Step 4：Commit**

```bash
git add linqibin-spring-boot-starter-jpa/src/main/java/.../JpaErrorMappingContributor.java linqibin-spring-boot-starter-jpa/README.md
git commit -m "refactor(starter-jpa): 删除 MySQL 错误码 switch，PG SQLState 由 Spring Data 包装"
```

---

### Task M3.2 `[Track: W3-T2]`：从 Spring Batch 5 jar 抽取 schema-postgresql.sql

**Files:**
- Create: `linqibin-spring-boot-starter-batch/src/main/resources/db/batch/schema-postgresql.sql`

- [ ] **Step 1：定位 Spring Batch 5 jar 中的 PG schema**

```bash
./gradlew :linqibin-spring-boot-starter-batch:dependencyInsight --dependency spring-batch-core | head -10
```

得到实际版本号（例如 `5.x.y`），然后从 Gradle cache 抽取：

```bash
find ~/.gradle/caches/modules-2/files-2.1/org.springframework.batch/spring-batch-core -name "*.jar" | head -1 | xargs -I {} unzip -p {} org/springframework/batch/core/schema-postgresql.sql > linqibin-spring-boot-starter-batch/src/main/resources/db/batch/schema-postgresql.sql
```

- [ ] **Step 2：验证内容**

```bash
head -30 linqibin-spring-boot-starter-batch/src/main/resources/db/batch/schema-postgresql.sql
```

期望：含 `CREATE TABLE BATCH_JOB_INSTANCE`、`CREATE SEQUENCE BATCH_JOB_SEQ` 等典型 Batch 5 PG schema。

- [ ] **Step 3：Commit**

```bash
git add linqibin-spring-boot-starter-batch/src/main/resources/db/batch/schema-postgresql.sql
git commit -m "feat(starter-batch): 从 Spring Batch 5 jar 抽取 schema-postgresql.sql"
```

---

### Task M3.3 `[Track: W3-T2]`：BatchSchemaInitializer 切换常量 + 删 schema-mysql.sql

**Files:**
- Modify: `linqibin-spring-boot-starter-batch/src/main/java/dev/linqibin/starter/batch/schema/BatchSchemaInitializer.java:42`
- Delete: `linqibin-spring-boot-starter-batch/src/main/resources/db/batch/schema-mysql.sql`

- [ ] **Step 1：替换 SCHEMA_RESOURCE 常量**

打开 `BatchSchemaInitializer.java`，找到第 42 行：

```java
private static final String SCHEMA_RESOURCE = "db/batch/schema-mysql.sql";
```

改为：

```java
private static final String SCHEMA_RESOURCE = "db/batch/schema-postgresql.sql";
```

同时更新该文件第 23 行附近注释（"`db/batch/schema-mysql.sql`" → `schema-postgresql.sql`）以及第 91 行附近 MySQL 大小写注释（改写为 PG 等价说明：PG 默认折叠未引号标识符为小写）。

- [ ] **Step 2：删除旧 MySQL schema 文件**

```bash
git rm linqibin-spring-boot-starter-batch/src/main/resources/db/batch/schema-mysql.sql
```

- [ ] **Step 3：编译验证**

```bash
./gradlew :linqibin-spring-boot-starter-batch:compileJava
```

期望：BUILD SUCCESSFUL。

- [ ] **Step 4：Commit**

```bash
git add linqibin-spring-boot-starter-batch/src/main/java/.../BatchSchemaInitializer.java
git commit -m "refactor(starter-batch): BatchSchemaInitializer 切换到 schema-postgresql.sql"
```

---

### Task M3.4 `[Track: W3-T3]`：batch starter test application-test.yml 切换 JDBC URL

**Files:**
- Modify: `linqibin-spring-boot-starter-batch/src/test/resources/application-test.yml:3-4`

- [ ] **Step 1：替换 Testcontainers JDBC URL**

把第 3-4 行：

```yaml
driver-class-name: org.testcontainers.jdbc.ContainerDatabaseDriver
url: jdbc:tc:mysql:8.0.40:///test_batch?TC_TMPFS=/testtmpfs:rw&TC_MY_CNF=/tc/mysql/conf.d
```

改为：

```yaml
driver-class-name: org.testcontainers.jdbc.ContainerDatabaseDriver
url: jdbc:tc:postgresql:17:///test_batch
```

注意：PG TC URL **不需要** `TC_TMPFS` 或 `TC_MY_CNF` 参数（这些是 MySQL 专属优化）。

- [ ] **Step 2：Commit**

```bash
git add linqibin-spring-boot-starter-batch/src/test/resources/application-test.yml
git commit -m "test(starter-batch): application-test.yml JDBC URL 切换到 PG TC"
```

---

### Task M3.5 `[Track: W3-T4]`：DualDataSourceIT.java 内嵌 URL 切换

**Files:**
- Modify: `linqibin-spring-boot-starter-batch/src/test/java/dev/linqibin/starter/batch/autoconfigure/DualDataSourceIT.java:28, 44`

- [ ] **Step 1：替换两处 hardcoded TC URL**

第 28 行：

```java
"linqibin.starter.batch.datasource.url=jdbc:tc:mysql:8.0.40:///batch_meta_db"
```

→

```java
"linqibin.starter.batch.datasource.url=jdbc:tc:postgresql:17:///batch_meta_db"
```

第 44 行：

```java
"spring.datasource.url=jdbc:tc:mysql:8.0.40:///test_batch"
```

→

```java
"spring.datasource.url=jdbc:tc:postgresql:17:///test_batch"
```

- [ ] **Step 2：运行该 IT**

```bash
./gradlew :linqibin-spring-boot-starter-batch:test --tests "*DualDataSourceIT*"
```

期望：通过。

- [ ] **Step 3：Commit**

```bash
git add linqibin-spring-boot-starter-batch/src/test/java/.../DualDataSourceIT.java
git commit -m "test(starter-batch): DualDataSourceIT TC URL 切换为 PG"
```

---

### Task M3.6 `[Track: W3-T5]`：BatchDataSourceConfigurationTest MySQLContainer → PostgreSQLContainer

**Files:**
- Modify: `linqibin-spring-boot-starter-batch/src/test/java/dev/linqibin/starter/batch/autoconfigure/BatchDataSourceConfigurationTest.java:13, 26-27`

- [ ] **Step 1：替换 import 与容器构造**

第 13 行：

```java
import org.testcontainers.containers.MySQLContainer;
```

→

```java
import org.testcontainers.containers.PostgreSQLContainer;
```

第 26-27 行：

```java
private static final MySQLContainer<?> MYSQL =
    new MySQLContainer<>("mysql:8.0.36")
```

→

```java
private static final PostgreSQLContainer<?> POSTGRES =
    new PostgreSQLContainer<>("postgres:17")
```

同时把后续引用 `MYSQL` 的变量全部改为 `POSTGRES`。

- [ ] **Step 2：编译 + 运行该测试**

```bash
./gradlew :linqibin-spring-boot-starter-batch:test --tests "*BatchDataSourceConfigurationTest*"
```

- [ ] **Step 3：Commit**

```bash
git add linqibin-spring-boot-starter-batch/src/test/java/.../BatchDataSourceConfigurationTest.java
git commit -m "test(starter-batch): BatchDataSourceConfigurationTest 容器切换为 PG"
```

---

### Task M3.7 `[Track: W3-T6]`：BatchPropertiesTest hardcoded URL 切换

**Files:**
- Modify: `linqibin-spring-boot-starter-batch/src/test/java/dev/linqibin/starter/batch/config/BatchPropertiesTest.java:80`

- [ ] **Step 1：替换 hardcoded URL**

第 80 行：

```java
properties.getDatasource().setUrl("jdbc:mysql://localhost:3306/batch_meta");
```

→

```java
properties.getDatasource().setUrl("jdbc:postgresql://localhost:5432/batch_meta");
```

- [ ] **Step 2：运行测试**

```bash
./gradlew :linqibin-spring-boot-starter-batch:test --tests "*BatchPropertiesTest*"
```

- [ ] **Step 3：Commit**

```bash
git add linqibin-spring-boot-starter-batch/src/test/java/.../BatchPropertiesTest.java
git commit -m "test(starter-batch): BatchPropertiesTest hardcoded URL 切换为 PG"
```

---

### Task M3.8 `[Track: W3-T7]`：HibernatePropertiesCustomizer 追加 metadata 探测优化

**Files:**
- Modify: `linqibin-spring-boot-starter-jpa/src/main/java/dev/linqibin/starter/jpa/autoconfig/HibernatePropertiesCustomizer.java:43-65`

- [ ] **Step 1：在 customize 方法中追加配置**

打开 `HibernatePropertiesCustomizer.java`，在 `customize` 方法体内（其他 `putIfAbsent` 语句之间）追加一行：

```java
// PG + HikariCP 公认优化：避免连接建立时 Hibernate 发起额外 JDBC metadata round-trip
hibernateProperties.putIfAbsent("hibernate.temp.use_jdbc_metadata_defaults", false);
```

建议放在二级缓存禁用配置（`USE_SECOND_LEVEL_CACHE`）之后、Jackson JSON 配置之前。

同步更新文件顶部 JavaDoc，在"二级缓存配置"一节后追加一节描述："PG + Hikari 元数据探测优化"。

- [ ] **Step 2：编译验证**

```bash
./gradlew :linqibin-spring-boot-starter-jpa:compileJava
```

- [ ] **Step 3：Commit**

```bash
git add linqibin-spring-boot-starter-jpa/src/main/java/.../HibernatePropertiesCustomizer.java
git commit -m "perf(starter-jpa): 追加 hibernate.temp.use_jdbc_metadata_defaults=false（PG+Hikari 优化）"
```

---

### Task M3.9 `[Track: W3-sync]`：M3 smoke test — starter-jpa + starter-batch 测试全绿

**Files:** 无文件改动

- [ ] **Step 1：跑两个 starter 模块的所有测试**

```bash
./gradlew :linqibin-spring-boot-starter-jpa:test :linqibin-spring-boot-starter-batch:test
```

期望：BUILD SUCCESSFUL，全部测试通过。

如果 Batch 测试失败：检查 Spring Batch 5 `schema-postgresql.sql` 内容（M3.2）是否与本项目 Boot 4.0.1 BOM 锁定版本一致；如果 jar 路径不对，重新执行 M3.2 抽取。

---

## Milestone M4：Flyway DDL 重写并合并（按服务）

**目标**：删除现有 26 个 V*.sql，按 §5.6 重新创建 16 个 PG-native baseline 文件。

**M4 验证（每服务）**：`./gradlew :patra-<service>-infra:test` + `./gradlew :patra-<service>-boot:bootRun`，确认 Flyway 全量 migration 成功。

**关键参考**（实施前必读）：
- spec §4.5-§4.18：类型/函数翻译规则总表
- spec §5.1：6 个 STORED 生成列的 PG 表达式
- spec §5.2：`set_updated_at()` 触发器函数定义
- spec §5.6.2-§5.6.5：每服务目标文件清单
- spec §5.6.8：3 个 partial index 应用

**M4 通用替换规则**（每个新 V*.sql 都要应用）：

| MySQL | PostgreSQL |
|---|---|
| `BIGINT UNSIGNED` | `BIGINT` |
| `INT UNSIGNED` | `INTEGER` |
| `TINYINT UNSIGNED` | `SMALLINT` |
| `TINYINT(1)` / `BOOLEAN` | `BOOLEAN` |
| `JSON` | `jsonb` |
| `VARBINARY(16)` | `bytea` |
| `DECIMAL(38, x)` | `NUMERIC(38, x)` |
| `TIMESTAMP(6)` | `timestamptz(6)` |
| `AUTO_INCREMENT` | （删除） |
| `ON UPDATE CURRENT_TIMESTAMP(6)` | （删除；改用 BEFORE UPDATE 触发器） |
| `ENGINE=InnoDB / DEFAULT CHARSET / COLLATE / ROW_FORMAT` | （全部删除） |
| `` ` `` 反引号 | `"` 双引号 或去掉 |
| `CHECK (REGEXP_LIKE(col, 'pat'))` | `CHECK (col ~ 'pat')` |
| `IFNULL(x, y)` | `COALESCE(x, y)` |
| `IF(x = 1, a, b)` | `CASE WHEN x THEN a ELSE b END`（PG `x` 已是 boolean） |
| `SUBSTRING_INDEX(s, d, 1)` | `split_part(s, d, 1)` |
| `JSON_ARRAY(...)` | `jsonb_build_array(...)` |
| `JSON_OBJECT(...)` | `jsonb_build_object(...)` |
| `JSON_EXTRACT(col, '$.k')` | `col -> 'k'`（保留 jsonb）或 `col->>'k'`（转 text） |
| `JSON_UNQUOTE(JSON_EXTRACT(col, '$.k'))` | `col ->> 'k'` |
| `NOW(6)` | `CURRENT_TIMESTAMP` |
| `INET6_ATON('192.168.1.10')` | `'\xC0A8010A'::bytea`（手工把 IP → 4 字节十六进制） |
| `INSERT IGNORE` | `INSERT ... ON CONFLICT DO NOTHING` |
| seed 中 `0` / `1` boolean | `false` / `true` |
| `CREATE TABLE IF NOT EXISTS` | `CREATE TABLE` |

---

### Task M4.1 `[Track: W4r-T1]`：创建 registry V1__init_registry_schema.sql（合并 3 DDL）

**Files:**
- Create: `patra-registry/patra-registry-infra/src/main/resources/db/migration/V1__init_registry_schema.sql`

合并来源：原 V1.0.0（sys_dict 4 表）+ V1.0.1（reg_provenance + reg_prov_*_cfg 7 表）+ V1.0.2（reg_expr_field_dict + reg_prov_expr_* 4 表）共 15 张表。

- [ ] **Step 1：文件头部 — 注入 set_updated_at() 函数**

文件顶部插入（spec §5.2）：

```sql
-- =====================================================================
-- Patra Registry — PostgreSQL 17 baseline schema
-- 合并自原 V1.0.0 / V1.0.1 / V1.0.2（MySQL）；已移除 6 条物理 FK、AUTO_INCREMENT、
-- ENGINE/CHARSET/COLLATE、DROP TABLE IF EXISTS、ROW_FORMAT、IF NOT EXISTS
-- =====================================================================

-- 公共触发器函数：仅当 UPDATE 未显式修改 updated_at 时（即非 JPA 路径），自动填充 now()
-- JPA 路径由 Hibernate @LastModifiedDate 显式赋值，会先于触发器执行，触发器跳过
CREATE OR REPLACE FUNCTION set_updated_at() RETURNS TRIGGER AS $$
BEGIN
  IF NEW.updated_at IS NOT DISTINCT FROM OLD.updated_at THEN
    NEW.updated_at = now();
  END IF;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;
```

- [ ] **Step 2：逐表翻译 sys_dict_type（V1.0.0 第 1 张）**

将原 MySQL DDL（参考 `git show HEAD:patra-registry/patra-registry-infra/src/main/resources/db/migration/V1.0.0__init_sys_dict_schema.sql`，sys_dict_type 段）翻译为 PG。范例 — 完成形：

```sql
CREATE TABLE sys_dict_type
(
    id                 BIGINT       NOT NULL,
    type_code          VARCHAR(64)  NOT NULL,
    type_name          VARCHAR(200) NOT NULL,
    description        VARCHAR(500) NULL,
    allow_custom_items BOOLEAN      NOT NULL DEFAULT false,
    is_system          BOOLEAN      NOT NULL DEFAULT true,
    reserved_json      jsonb        NULL,
    record_remarks     jsonb        NULL,
    created_at         timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by         BIGINT       NULL,
    created_by_name    VARCHAR(100) NULL,
    updated_at         timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by         BIGINT       NULL,
    updated_by_name    VARCHAR(100) NULL,
    version            BIGINT       NOT NULL DEFAULT 0,
    ip_address         bytea        NULL,
    deleted_at         timestamptz(6) NULL DEFAULT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_sys_dict_type__code UNIQUE (type_code),
    CONSTRAINT chk_sys_dict_type__code_format CHECK (type_code ~ '^[a-z0-9_]{1,64}$')
);
COMMENT ON TABLE sys_dict_type IS '系统字典 - 类型';
COMMENT ON COLUMN sys_dict_type.type_code IS '类型编码: 小写下划线格式';
-- ... (其他列 COMMENT 略，按需补全)

CREATE TRIGGER trg_sys_dict_type_updated_at
BEFORE UPDATE ON sys_dict_type
FOR EACH ROW EXECUTE FUNCTION set_updated_at();
```

- [ ] **Step 3：逐表翻译 sys_dict_item（含 STORED 生成列）**

特别注意 `default_key` 生成列（spec §5.1）：

```sql
CREATE TABLE sys_dict_item
(
    id              BIGINT       NOT NULL,
    type_id         BIGINT       NOT NULL,
    item_code       VARCHAR(64)  NOT NULL,
    item_name       VARCHAR(200) NOT NULL,
    short_name      VARCHAR(64)  NULL,
    description     VARCHAR(500) NULL,
    display_order   INTEGER      NOT NULL DEFAULT 100 CHECK (display_order >= 0),
    is_default      BOOLEAN      NOT NULL DEFAULT false,
    enabled         BOOLEAN      NOT NULL DEFAULT true,
    label_color     VARCHAR(32)  NULL,
    icon_name       VARCHAR(64)  NULL,
    attributes_json jsonb        NULL,
    -- 审计字段（同 sys_dict_type）
    record_remarks  jsonb        NULL,
    created_at      timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by      BIGINT       NULL,
    created_by_name VARCHAR(100) NULL,
    updated_at      timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      BIGINT       NULL,
    updated_by_name VARCHAR(100) NULL,
    version         BIGINT       NOT NULL DEFAULT 0,
    ip_address      bytea        NULL,
    deleted_at      timestamptz(6) NULL DEFAULT NULL,
    -- 生成列：仅当 default+enabled+未删除时等于 type_id，否则 NULL
    default_key     BIGINT GENERATED ALWAYS AS
        (CASE WHEN (is_default AND enabled AND deleted_at IS NULL) THEN type_id ELSE NULL END) STORED,
    PRIMARY KEY (id),
    CONSTRAINT uk_sys_dict_item__type_code UNIQUE (type_id, item_code),
    CONSTRAINT uk_sys_dict_item__default_per_type UNIQUE (default_key),
    CONSTRAINT chk_sys_dict_item__code_format CHECK (item_code ~ '^[A-Z0-9_]{1,64}$')
);
COMMENT ON TABLE sys_dict_item IS '系统字典 - 条目';

CREATE TRIGGER trg_sys_dict_item_updated_at
BEFORE UPDATE ON sys_dict_item
FOR EACH ROW EXECUTE FUNCTION set_updated_at();
```

- [ ] **Step 4：继续翻译其余 13 张表**

按以下顺序，每张表都附 BEFORE UPDATE 触发器：

1. `sys_dict_item_alias`（V1.0.0）— 无生成列、无触发器（无 updated_at）
2. `sys_reference_standard`（V1.0.0）— 含 `canonical_key` 生成列（spec §5.1）
3. `reg_provenance`（V1.0.1）— 含 BEFORE UPDATE 触发器
4. `reg_prov_window_offset_cfg`（V1.0.1）— **移除物理 FK**，改为列注释 `-- 逻辑外键 → reg_provenance.id`
5. `reg_prov_pagination_cfg`（V1.0.1）— 同上
6. `reg_prov_http_cfg`（V1.0.1）— 同上
7. `reg_prov_batching_cfg`（V1.0.1）— 同上
8. `reg_prov_retry_cfg`（V1.0.1）— 同上
9. `reg_prov_rate_limit_cfg`（V1.0.1）— 同上
10. `reg_expr_field_dict`（V1.0.2）— 无 FK
11. `reg_prov_api_param_map`（V1.0.2）— 含 effective_to CHECK 约束
12. `reg_prov_expr_capability`（V1.0.2）— 含 `DECIMAL(38,12)` → `NUMERIC(38,12)`、`ROW_FORMAT=DYNAMIC` 删除
13. `reg_prov_expr_render_rule`（V1.0.2）— 含 3 个生成列（`match_type_key`/`negated_key`/`value_type_key`，spec §5.1）

每张表后：`CREATE TRIGGER trg_<table>_updated_at BEFORE UPDATE ON <table> FOR EACH ROW EXECUTE FUNCTION set_updated_at();`（如该表无 `updated_at` 字段则跳过）。

- [ ] **Step 5：在 PG 容器上 dry-run 验证**

```bash
# 仅 dry-run，不真的应用到 patra_registry 库
docker exec -i patra-postgres psql -U postgres -d patra_test -f - < patra-registry/patra-registry-infra/src/main/resources/db/migration/V1__init_registry_schema.sql
```

如果有 IMMUTABLE 错误（spec §10.3 现场验证项），按 spec §5.1 公式调整表达式。

期望：所有 CREATE TABLE 与 CREATE TRIGGER 成功。

- [ ] **Step 6：Commit**

```bash
git add patra-registry/patra-registry-infra/src/main/resources/db/migration/V1__init_registry_schema.sql
git commit -m "feat(registry): V1 PG baseline 合并 sys_dict + reg_provenance + reg_expr（15 表）"
```

---

### Task M4.2 `[Track: W4r-T2]`：创建 registry V2__seed_provenance_expr.sql（重写自 V1.1.0）

**Files:**
- Create: `patra-registry/patra-registry-infra/src/main/resources/db/migration/V2__seed_provenance_expr.sql`

来源：原 V1.1.0__seed_all_data.sql（490 行，约 34 条业务配置 seed）。

- [ ] **Step 1：复制原 V1.1.0 内容到新文件**

```bash
cp patra-registry/patra-registry-infra/src/main/resources/db/migration/V1.1.0__seed_all_data.sql /tmp/v110_orig.sql
```

- [ ] **Step 2：按 M4 通用替换规则全文替换**

重点：
- `INET6_ATON('192.168.1.10')` → `'\xC0A8010A'::bytea`（3 处，全是 192.168.1.10 即 `0xC0A8010A`）
- `JSON_ARRAY(...)` → `jsonb_build_array(...)`
- `JSON_OBJECT(...)` → `jsonb_build_object(...)`
- `NOW(6)` → `CURRENT_TIMESTAMP`
- `TIMESTAMP('2025-10-14 ...')` → `TIMESTAMP '2025-10-14 ...'`（PG ANSI 格式）
- boolean 字面量：所有列名以 `is_/allow_/enabled/supports_/wrap_/sorting_direction/tls_verify_enabled/retry_on_network_error` 等开头的字段，值 `0`/`1` → `false`/`true`
- 反引号 `` ` `` 全部删除
- `INSERT INTO ... SELECT ... WHERE NOT EXISTS` → `INSERT INTO ... VALUES ... ON CONFLICT DO NOTHING`（或保留 SELECT 形态加 `ON CONFLICT DO NOTHING`）

- [ ] **Step 3：在 PG 容器上 dry-run**

```bash
docker exec -i patra-postgres psql -U postgres -d patra_test -f - < patra-registry/patra-registry-infra/src/main/resources/db/migration/V2__seed_provenance_expr.sql
```

期望：所有 INSERT 成功；行数与原 MySQL seed 一致。

- [ ] **Step 4：Commit**

```bash
git add patra-registry/patra-registry-infra/src/main/resources/db/migration/V2__seed_provenance_expr.sql
git commit -m "feat(registry): V2 PG seed — provenance 配置 + expr 渲染规则"
```

---

### Task M4.3 `[Track: W4r-T3]`：创建 registry V3__seed_dict_country.sql（合并 V1.2.0 + V1.4.0 country + V1.4.1）

**Files:**
- Create: `patra-registry/patra-registry-infra/src/main/resources/db/migration/V3__seed_dict_country.sql`

合并：原 V1.2.0（country dict + 249 items + 249 NAME_EN alias）+ V1.4.0 中 country NAME_ZH 标准记录 + V1.4.1（249 NAME_ZH alias）。

- [ ] **Step 1：拼接 3 个来源**

按以下顺序拼接，文件顶部加注释说明合并来源：

1. country `sys_dict_type` INSERT（来自 V1.2.0）
2. country 3 个 `sys_reference_standard` INSERT：ISO_3166_1_ALPHA2、NAME_EN（V1.2.0）+ NAME_ZH（V1.4.0）
3. 249 个 `sys_dict_item` INSERT（V1.2.0）
4. 249 个 `sys_dict_item_alias` NAME_EN INSERT（V1.2.0）
5. 249 个 `sys_dict_item_alias` NAME_ZH INSERT（V1.4.1）

- [ ] **Step 2：应用 M4 通用替换规则**

主要是 boolean 字面量 `0`/`1` → `false`/`true`。`INSERT ... WHERE NOT EXISTS` → `INSERT ... ON CONFLICT DO NOTHING`。

- [ ] **Step 3：在 PG 容器上 dry-run**

```bash
docker exec -i patra-postgres psql -U postgres -d patra_test -f - < patra-registry/patra-registry-infra/src/main/resources/db/migration/V3__seed_dict_country.sql
docker exec patra-postgres psql -U postgres -d patra_test -c "SELECT COUNT(*) FROM sys_dict_item WHERE type_id IN (SELECT id FROM sys_dict_type WHERE type_code = 'country');"
```

期望：249 行。

- [ ] **Step 4：Commit**

```bash
git add patra-registry/patra-registry-infra/src/main/resources/db/migration/V3__seed_dict_country.sql
git commit -m "feat(registry): V3 PG seed — country 字典（type + 3 标准 + 249 items + 498 alias）"
```

---

### Task M4.4 `[Track: W4r-T4]`：创建 registry V4__seed_dict_language.sql（合并 V1.3.0 + V1.4.0 language + V1.4.2）

**Files:**
- Create: `patra-registry/patra-registry-infra/src/main/resources/db/migration/V4__seed_dict_language.sql`

合并：V1.3.0（language dict + 3 reference_standard + 50 items + 60 ISO_639_3 alias + 50 NAME_EN alias）+ V1.4.0 language NAME_ZH 标准 + V1.4.2（50 NAME_ZH alias）。

- [ ] **Step 1-4：同 M4.3 模式**

拼接顺序：
1. language `sys_dict_type`
2. 4 个 `sys_reference_standard`：BCP_47、ISO_639_3、NAME_EN（来自 V1.3.0）+ NAME_ZH（V1.4.0）
3. 50 个 language `sys_dict_item`
4. 60 个 ISO_639_3 alias
5. 50 个 NAME_EN alias
6. 50 个 NAME_ZH alias（V1.4.2）

dry-run + commit。

```bash
git add patra-registry/patra-registry-infra/src/main/resources/db/migration/V4__seed_dict_language.sql
git commit -m "feat(registry): V4 PG seed — language 字典（type + 4 标准 + 50 items + 160 alias）"
```

---

### Task M4.5 `[Track: W4r-sync]`：删除 registry 原 9 个 V1.x.y 文件

**Files:**
- Delete 9 files in `patra-registry/patra-registry-infra/src/main/resources/db/migration/`

- [ ] **Step 1：删除旧文件**

```bash
git rm patra-registry/patra-registry-infra/src/main/resources/db/migration/V1.0.0__init_sys_dict_schema.sql
git rm patra-registry/patra-registry-infra/src/main/resources/db/migration/V1.0.1__init_provenance_config_schema.sql
git rm patra-registry/patra-registry-infra/src/main/resources/db/migration/V1.0.2__init_expr_config_schema.sql
git rm patra-registry/patra-registry-infra/src/main/resources/db/migration/V1.1.0__seed_all_data.sql
git rm patra-registry/patra-registry-infra/src/main/resources/db/migration/V1.2.0__seed_country_dictionary.sql
git rm patra-registry/patra-registry-infra/src/main/resources/db/migration/V1.3.0__seed_language_dictionary.sql
git rm patra-registry/patra-registry-infra/src/main/resources/db/migration/V1.4.0__add_name_zh_reference_standard.sql
git rm patra-registry/patra-registry-infra/src/main/resources/db/migration/V1.4.1__seed_country_name_zh.sql
git rm patra-registry/patra-registry-infra/src/main/resources/db/migration/V1.4.2__seed_language_name_zh.sql
```

- [ ] **Step 2：列出剩余的 4 个新文件**

```bash
ls patra-registry/patra-registry-infra/src/main/resources/db/migration/
```

期望：`V1__init_registry_schema.sql`、`V2__seed_provenance_expr.sql`、`V3__seed_dict_country.sql`、`V4__seed_dict_language.sql`。

- [ ] **Step 3：Commit**

```bash
git commit -m "chore(registry): 删除 9 个旧 V1.x.y MySQL Flyway 脚本"
```

---

### Task M4.6 `[Track: W4r-validate]`：registry M4 验证

**Files:** 无文件改动

- [ ] **Step 1：跑 infra 测试套件**

```bash
./gradlew :patra-registry-infra:test
```

- [ ] **Step 2：在干净 patra_registry 库上跑 Flyway**

```bash
# 重置 patra_registry 库
docker exec patra-postgres psql -U postgres -c "DROP DATABASE IF EXISTS patra_registry; CREATE DATABASE patra_registry;"
./gradlew :patra-registry-boot:bootRun &
sleep 30
curl -s http://localhost:6000/actuator/health | grep '"status":"UP"' || echo "FAIL"
kill %1
```

- [ ] **Step 3：检查 schema_history**

```bash
docker exec patra-postgres psql -U postgres -d patra_registry -c "SELECT version, description, success FROM flyway_schema_history;"
```

期望：4 行，全部 `success = t`。

---

### Task M4.7 `[Track: W4i-T1]`：创建 ingest V1__init_ingest_scheduling.sql（拆自 V0.1.0 第 1-3 段）

**Files:**
- Create: `patra-ingest/patra-ingest-infra/src/main/resources/db/migration/V1__init_ingest_scheduling.sql`

来源：原 `V0.1.0__init_ingest_schema.sql` 第 1-3 段 —— `ing_schedule_instance` + `ing_plan`。

- [ ] **Step 1：文件顶部注入 set_updated_at()**

复用 M4.1 Step 1 的函数定义（每服务独立一份，spec §5.6.6 方案 A）。

- [ ] **Step 2：逐表翻译 ing_schedule_instance、ing_plan**

按 M4 通用替换规则。`ing_plan` 含 JSON 列、`TIMESTAMP(6)` 字段、生成的 `window_from_ts`/`window_to_ts` 反规范化字段；注意 `JSON` → `jsonb`。

每张表后追加 BEFORE UPDATE 触发器。

- [ ] **Step 3：dry-run + Commit**

```bash
docker exec -i patra-postgres psql -U postgres -d patra_test -f - < patra-ingest/patra-ingest-infra/src/main/resources/db/migration/V1__init_ingest_scheduling.sql
git add patra-ingest/patra-ingest-infra/src/main/resources/db/migration/V1__init_ingest_scheduling.sql
git commit -m "feat(ingest): V1 PG baseline 拆分 — scheduling root (2 表)"
```

---

### Task M4.8 `[Track: W4i-T2]`：创建 ingest V2__init_ingest_execution.sql（含 partial index）

**Files:**
- Create: `patra-ingest/patra-ingest-infra/src/main/resources/db/migration/V2__init_ingest_execution.sql`

来源：V0.1.0 第 4-6 段 —— `ing_plan_slice` + `ing_task` + `ing_task_run` + `ing_task_run_batch`（4 张表）。

- [ ] **Step 1：翻译 4 张表 DDL**

按通用规则。`ing_task` 字段较多（含租约 / 心跳 / 优先级 / 各种时间戳）。

- [ ] **Step 2：把 ing_task.idx_task_queue 改为 partial index（spec §5.6.8）**

把原索引：

```sql
KEY `idx_task_queue` (`status_code`, `leased_until`, `priority`, `scheduled_at`, `id`)
```

改为 PG partial index（写在表外）：

```sql
CREATE INDEX idx_task_queue ON ing_task (leased_until, priority, scheduled_at, id)
  WHERE status_code IN ('PENDING', 'QUEUED');
```

- [ ] **Step 3：添加触发器 + dry-run + Commit**

```bash
git add patra-ingest/patra-ingest-infra/src/main/resources/db/migration/V2__init_ingest_execution.sql
git commit -m "feat(ingest): V2 PG baseline — execution chain (4 表) + idx_task_queue partial index"
```

---

### Task M4.9 `[Track: W4i-T3]`：创建 ingest V3__init_ingest_cursor.sql

**Files:**
- Create: `patra-ingest/patra-ingest-infra/src/main/resources/db/migration/V3__init_ingest_cursor.sql`

来源：V0.1.0 第 7-8 段 —— `ing_cursor` + `ing_cursor_event`。

- [ ] **Step 1：翻译 2 张表**

注意：`ing_cursor.normalized_numeric` 与 `ing_cursor_event.prev_numeric/new_numeric` 是 `DECIMAL(38,0)` → `NUMERIC(38,0)`。

- [ ] **Step 2：触发器 + dry-run + Commit**

```bash
git add patra-ingest/patra-ingest-infra/src/main/resources/db/migration/V3__init_ingest_cursor.sql
git commit -m "feat(ingest): V3 PG baseline — cursor 当前值 + cursor 事件（2 表）"
```

---

### Task M4.10 `[Track: W4i-T4]`：创建 ingest V4__init_ingest_outbox.sql（含 2 个 partial index）

**Files:**
- Create: `patra-ingest/patra-ingest-infra/src/main/resources/db/migration/V4__init_ingest_outbox.sql`

来源：V0.1.0 第 9-10 段 —— `ing_outbox_message` + `ing_outbox_relay_log`。

- [ ] **Step 1：翻译 2 张表 DDL**

- [ ] **Step 2：替换 ing_outbox_message 两个索引为 partial（spec §5.6.8）**

```sql
CREATE INDEX idx_pending_relay ON ing_outbox_message (channel, not_before, id)
  WHERE status_code = 'PENDING';

CREATE INDEX idx_publishing_lease ON ing_outbox_message (channel, pub_leased_until, id)
  WHERE status_code = 'PUBLISHING';
```

- [ ] **Step 3：触发器 + dry-run + Commit**

```bash
git add patra-ingest/patra-ingest-infra/src/main/resources/db/migration/V4__init_ingest_outbox.sql
git commit -m "feat(ingest): V4 PG baseline — outbox + relay log（2 表 + 2 partial index）"
```

---

### Task M4.11 `[Track: W4i-sync]`：删除 ingest 原 V0.1.0

```bash
git rm patra-ingest/patra-ingest-infra/src/main/resources/db/migration/V0.1.0__init_ingest_schema.sql
git commit -m "chore(ingest): 删除旧 V0.1.0 MySQL Flyway 脚本"
```

---

### Task M4.12 `[Track: W4i-validate]`：ingest M4 验证

- [ ] **Step 1：infra 测试 + boot 启动**

```bash
./gradlew :patra-ingest-infra:test
docker exec patra-postgres psql -U postgres -c "DROP DATABASE IF EXISTS patra_ingest; CREATE DATABASE patra_ingest;"
./gradlew :patra-ingest-boot:bootRun &
sleep 30
curl -s http://localhost:6100/actuator/health | grep '"status":"UP"' || echo "FAIL"
kill %1
```

- [ ] **Step 2：schema_history 应有 4 行**

```bash
docker exec patra-postgres psql -U postgres -d patra_ingest -c "SELECT version, description, success FROM flyway_schema_history;"
```

---

### Task M4.13 `[Track: W4c-T1]`：创建 catalog V1__create_venue.sql（venue 7 + rating 4 = 11 表）

**Files:**
- Create: `patra-catalog/patra-catalog-infra/src/main/resources/db/migration/V1__create_venue.sql`

来源：原 V1.0.0__create_venue_aggregate.sql（7 表）+ V1.0.1__create_venue_rating.sql（4 表）。

- [ ] **Step 1：文件顶部 set_updated_at() 函数**（catalog 自己的副本）

- [ ] **Step 2：翻译 11 张表**

按依赖顺序：
1. `cat_venue`（含 `CHECK (chk_venue_country_code)`，PG 改 `country_code ~ '^[A-Z]{2}$' OR country_code IS NULL`）
2. `cat_venue_identifier`（**删除冗余 `idx_venue_id`** spec §4.50）
3. `cat_venue_publication_stats`
4. `cat_venue_instance`
5. `cat_venue_mesh`
6. `cat_venue_relation`
7. `cat_venue_indexing_history`
8. `cat_venue_jcr_rating`
9. `cat_venue_cas_rating`
10. `cat_venue_scopus_rating`
11. `cat_venue_cas_warning`

每张表后追加触发器。

- [ ] **Step 3：dry-run + Commit**

```bash
git add patra-catalog/patra-catalog-infra/src/main/resources/db/migration/V1__create_venue.sql
git commit -m "feat(catalog): V1 PG baseline — venue + rating (11 表)"
```

---

### Task M4.14 `[Track: W4c-T2]`：创建 catalog V2__create_publication.sql（13 表 — 含 value→identifier_value 重命名 + FULLTEXT 删除 + 生成列）

**Files:**
- Create: `patra-catalog/patra-catalog-infra/src/main/resources/db/migration/V2__create_publication.sql`

来源：V1.1.0 + V1.1.1 + V1.1.2 + V1.4.3 + V1.5.1 共 5 个原文件，13 张表。

- [ ] **Step 1：翻译 cat_publication（含 language_base 生成列）**

特别注意：

```sql
language_base VARCHAR(5) GENERATED ALWAYS AS (split_part(language_code, '-', 1)) STORED
```

**删除 `CREATE FULLTEXT INDEX ft_title ON cat_publication (title) WITH PARSER ngram`**（spec §4.19）。

- [ ] **Step 2：翻译 cat_publication_identifier（含 value → identifier_value 重命名）**

字段定义：

```sql
identifier_value VARCHAR(255) NOT NULL  -- 原 MySQL value，PG 保留字冲突
```

索引也同步改名：

```sql
INDEX idx_type_value (type, identifier_value)
```

- [ ] **Step 3：翻译 cat_publication_abstract（删除 ft_plain_text FULLTEXT）**

- [ ] **Step 4：翻译其余 10 张表**

`cat_publication_date`、`cat_publication_metadata`、`cat_publication_alternative_abstract`、`cat_publication_oa_location`（来自 V1.1.1）；`cat_publication_reference`（V1.1.2）；`cat_publication_type`（V1.4.3）；`cat_publication_external_reference`、`cat_publication_related_item`、`cat_publication_supplemental_object`、`cat_publication_history`（V1.5.1）。

- [ ] **Step 5：dry-run + Commit**

```bash
git add patra-catalog/patra-catalog-infra/src/main/resources/db/migration/V2__create_publication.sql
git commit -m "feat(catalog): V2 PG baseline — publication 集大成 (13 表) + value→identifier_value + 删 FULLTEXT"
```

---

### Task M4.15 `[Track: W4c-T3]`：创建 catalog V3__create_author.sql（5 表 + 2 个函数索引）

**Files:**
- Create: `patra-catalog/patra-catalog-infra/src/main/resources/db/migration/V3__create_author.sql`

来源：V1.2.0（cat_author + cat_author_name_variant + cat_author_orcid，3 表）+ V1.2.1（cat_publication_author + cat_publication_author_affiliation，2 表）。

- [ ] **Step 1：翻译 5 张表**

`cat_publication_author` **删除冗余 idx_publication**（spec §4.50）。
`cat_publication_author_affiliation` **删除冗余 idx_pub_author**（spec §4.50）。

- [ ] **Step 2：追加 2 个函数索引（spec §4.53）**

文件末尾：

```sql
-- 函数索引：支持 Spring Data 派生方法 findByDisplayNameContainingIgnoreCase
-- PG 上 Hibernate 生成 LOWER(col) LIKE LOWER(?)，B-tree 索引仅在函数列上生效
CREATE INDEX idx_author_display_name_lower ON cat_author (lower(display_name));
CREATE INDEX idx_author_variant_last_name_lower ON cat_author_name_variant (lower(last_name));
```

- [ ] **Step 3：dry-run + Commit**

```bash
git add patra-catalog/patra-catalog-infra/src/main/resources/db/migration/V3__create_author.sql
git commit -m "feat(catalog): V3 PG baseline — author (5 表) + 2 函数索引（IgnoreCase 派生方法支持）"
```

---

### Task M4.16 `[Track: W4c-T4]`：创建 catalog V4__create_organization.sql（8 表，含 value → name_value 重命名）

**Files:**
- Create: `patra-catalog/patra-catalog-infra/src/main/resources/db/migration/V4__create_organization.sql`

来源：V1.3.0（5 表：cat_organization + cat_organization_name + cat_organization_external_id + cat_organization_relation + cat_organization_location）+ V1.3.1（3 表：cat_investigator + cat_publication_investigator + cat_publication_personal_name_subject）。

- [ ] **Step 1：翻译 8 张表**

`cat_organization_name` 字段 `value` → `name_value`（spec §4.25 / §5.3），同步唯一约束与索引：

```sql
name_value VARCHAR(500) NOT NULL,
-- ...
CONSTRAINT uk_org_name_value UNIQUE (org_id, name_value, lang),
-- ...
CREATE INDEX idx_org_name_value ON cat_organization_name (name_value);
```

- [ ] **Step 2：dry-run + Commit**

```bash
git add patra-catalog/patra-catalog-infra/src/main/resources/db/migration/V4__create_organization.sql
git commit -m "feat(catalog): V4 PG baseline — organization + investigator (8 表) + value→name_value"
```

---

### Task M4.17 `[Track: W4c-T5]`：创建 catalog V5__create_mesh.sql（15 表，删 3 FULLTEXT）

**Files:**
- Create: `patra-catalog/patra-catalog-infra/src/main/resources/db/migration/V5__create_mesh.sql`

来源：V1.4.0（mesh descriptor/qualifier 子系统 10 表）+ V1.4.1（mesh SCR 子系统 5 表）。

- [ ] **Step 1：翻译 15 张表**

按依赖顺序（mesh_descriptor / mesh_qualifier 先建，cat_mesh_scr 等子表后建）。**删除 3 个 FULLTEXT INDEX**：`ft_name_note`（cat_mesh_descriptor）、`ft_term`（cat_mesh_entry_term）、`ft_name_note`（cat_mesh_scr）。

- [ ] **Step 2：dry-run + Commit**

```bash
git add patra-catalog/patra-catalog-infra/src/main/resources/db/migration/V5__create_mesh.sql
git commit -m "feat(catalog): V5 PG baseline — mesh + mesh SCR (15 表, 删 3 FULLTEXT)"
```

---

### Task M4.18 `[Track: W4c-T6]`：创建 catalog V6__create_keyword.sql（2 表，删 FULLTEXT + 1 冗余索引）

**Files:**
- Create: `patra-catalog/patra-catalog-infra/src/main/resources/db/migration/V6__create_keyword.sql`

来源：V1.4.2（cat_keyword + cat_publication_keyword）。

- [ ] **Step 1：翻译 2 张表**

`cat_keyword` 删除 `ft_keyword_term` FULLTEXT。
`cat_publication_keyword` 删除 `idx_major` 冗余索引（spec §4.50；它被 `idx_keyword_pub` 前缀覆盖）。

- [ ] **Step 2：dry-run + Commit**

```bash
git add patra-catalog/patra-catalog-infra/src/main/resources/db/migration/V6__create_keyword.sql
git commit -m "feat(catalog): V6 PG baseline — keyword (2 表, 删 1 FULLTEXT + 1 冗余索引)"
```

---

### Task M4.19 `[Track: W4c-T7]`：创建 catalog V7__create_funding.sql（1 表）

**Files:**
- Create: `patra-catalog/patra-catalog-infra/src/main/resources/db/migration/V7__create_funding.sql`

来源：V1.5.0（cat_publication_funding）。

- [ ] **Step 1：翻译 + dry-run + Commit**

```bash
git add patra-catalog/patra-catalog-infra/src/main/resources/db/migration/V7__create_funding.sql
git commit -m "feat(catalog): V7 PG baseline — funding (1 表)"
```

---

### Task M4.20 `[Track: W4c-sync]`：删除 catalog 旧 15 个 V*.sql

```bash
cd patra-catalog/patra-catalog-infra/src/main/resources/db/migration/
git rm V1.0.0__create_venue_aggregate.sql V1.0.1__create_venue_rating.sql
git rm V1.1.0__create_publication_aggregate.sql V1.1.1__create_publication_auxiliary.sql V1.1.2__create_publication_reference.sql
git rm V1.2.0__create_author_aggregate.sql V1.2.1__create_author_publication_relation.sql
git rm V1.3.0__create_organization_aggregate.sql V1.3.1__create_investigator.sql
git rm V1.4.0__create_mesh_system.sql V1.4.1__create_mesh_scr_system.sql V1.4.2__create_keyword.sql V1.4.3__create_publication_type.sql
git rm V1.5.0__create_funding_system.sql V1.5.1__create_publication_supplemental.sql
cd -
git commit -m "chore(catalog): 删除 15 个旧 V1.x.y MySQL Flyway 脚本"
```

---

### Task M4.21 `[Track: W4c-validate]`：catalog M4 验证

```bash
./gradlew :patra-catalog-infra:test
docker exec patra-postgres psql -U postgres -c "DROP DATABASE IF EXISTS patra_catalog; CREATE DATABASE patra_catalog;"
docker exec patra-postgres psql -U postgres -c "DROP DATABASE IF EXISTS patra_batch; CREATE DATABASE patra_batch;"
./gradlew :patra-catalog-boot:bootRun &
sleep 60
curl -s http://localhost:6200/actuator/health | grep '"status":"UP"' || echo "FAIL"
kill %1
docker exec patra-postgres psql -U postgres -d patra_catalog -c "SELECT version, description, success FROM flyway_schema_history;"
```

期望：7 行，全部 success。

---

### Task M4.22 `[Track: W4s-T1]`：创建 object-storage V1__init_storage_schema.sql

**Files:**
- Create: `patra-object-storage/patra-object-storage-infra/src/main/resources/db/migration/V1__init_storage_schema.sql`

来源：原 V0.1.0__init_storage_schema.sql（1 表：storage_file_metadata）。

- [ ] **Step 1：文件顶部 set_updated_at() 函数 + 翻译该表 + 触发器**

按 M4 通用规则。文件较短（约 50 行 PG）。

- [ ] **Step 2：dry-run + Commit**

```bash
git add patra-object-storage/patra-object-storage-infra/src/main/resources/db/migration/V1__init_storage_schema.sql
git commit -m "feat(object-storage): V1 PG baseline — storage_file_metadata (1 表)"
```

---

### Task M4.23 `[Track: W4s-sync]`：删除 object-storage 旧 V0.1.0 + M4 验证

```bash
git rm patra-object-storage/patra-object-storage-infra/src/main/resources/db/migration/V0.1.0__init_storage_schema.sql
git commit -m "chore(object-storage): 删除旧 V0.1.0 MySQL Flyway 脚本"

./gradlew :patra-object-storage-infra:test
docker exec patra-postgres psql -U postgres -c "DROP DATABASE IF EXISTS patra_storage; CREATE DATABASE patra_storage;"
./gradlew :patra-object-storage-boot:bootRun &
sleep 30
curl -s http://localhost:6300/actuator/health | grep '"status":"UP"' || echo "FAIL"
kill %1
```

---

## Milestone M5：Java 应用代码修复

**目标**：让 Java 业务代码与新 PG schema 一致；修复 native query 方言。

**M5 验证**：`./gradlew test` 全量通过。

---

### Task M5.1 `[Track: W5-T1]`：PublicationIdentifierEntity.java value → identifier_value

**Files:**
- Modify: `patra-catalog/patra-catalog-infra/src/main/java/dev/linqibin/patra/catalog/infra/persistence/entity/PublicationIdentifierEntity.java:49-53`

- [ ] **Step 1：修改 @Column**

第 49 行 `@Column(name = "value", ...)` 改为 `@Column(name = "identifier_value", ...)`。Java 属性名 `value` 不动。

- [ ] **Step 2：编译 + Commit**

```bash
./gradlew :patra-catalog-infra:compileJava
git add patra-catalog/patra-catalog-infra/src/main/java/.../PublicationIdentifierEntity.java
git commit -m "fix(catalog): PublicationIdentifierEntity.value → identifier_value（PG 保留字）"
```

---

### Task M5.2 `[Track: W5-T2]`：OrganizationNameEntity.java value → name_value（含 @UniqueConstraint + @Index）

**Files:**
- Modify: `patra-catalog/patra-catalog-infra/src/main/java/dev/linqibin/patra/catalog/infra/persistence/entity/OrganizationNameEntity.java:46, 50, 59`

- [ ] **Step 1：三处同步修改**

第 46 行 `@UniqueConstraint(columnNames = {"org_id", "value", "lang"})` 改 `"value"` → `"name_value"`。
第 50 行 `@Index(name = "idx_org_name_value", columnList = "value")` 改 `columnList = "name_value"`。
第 59 行 `@Column(name = "value", nullable = false, length = 500)` 改 `name = "name_value"`。Java 属性名 `value` 不动。

- [ ] **Step 2：编译 + Commit**

```bash
./gradlew :patra-catalog-infra:compileJava
git add patra-catalog/patra-catalog-infra/src/main/java/.../OrganizationNameEntity.java
git commit -m "fix(catalog): OrganizationNameEntity.value → name_value（PG 保留字，含 @UniqueConstraint/@Index）"
```

---

### Task M5.3 `[Track: W5-T3]`：VenueDao 改 JSON 函数为 PG 操作符

**Files:**
- Modify: `patra-catalog/patra-catalog-infra/src/main/java/dev/linqibin/patra/catalog/infra/persistence/dao/VenueDao.java:196, 205, 228`

- [ ] **Step 1：三处替换**

第 196 行：

```sql
JSON_UNQUOTE(JSON_EXTRACT(v.open_access, '$.oaType'))
```

→

```sql
v.open_access ->> 'oaType'
```

第 205 行：

```sql
COALESCE(JSON_EXTRACT(v.citation_metrics, '$.hIndex'), 0)
```

→

```sql
COALESCE((v.citation_metrics ->> 'hIndex')::numeric, 0)
```

第 228 行（countQuery 中）：同 196 行。

- [ ] **Step 2：运行 VenueDao IT**

```bash
./gradlew :patra-catalog-infra:test --tests "*VenueRepositoryAdapterIT*"
```

- [ ] **Step 3：Commit**

```bash
git add patra-catalog/patra-catalog-infra/src/main/java/.../VenueDao.java
git commit -m "fix(catalog): VenueDao JSON 函数 → PG ->> 操作符（3 处）"
```

---

### Task M5.4 `[Track: W5-T4]`：VenueDao + PublicationDao LIKE → ILIKE

**Files:**
- Modify: `patra-catalog/patra-catalog-infra/src/main/java/dev/linqibin/patra/catalog/infra/persistence/dao/VenueDao.java:94`
- Modify: `patra-catalog/patra-catalog-infra/src/main/java/dev/linqibin/patra/catalog/infra/persistence/dao/PublicationDao.java:125`

- [ ] **Step 1：VenueDao:94 改 LIKE → ILIKE**

找到第 94 行附近的 `LIKE :keyword` 模式 + 同行注释"依赖 MySQL `utf8mb4_0900_ai_ci`"。改为 `ILIKE :keyword` + 注释改为"用 PG ILIKE 实现 case-insensitive，与 DB collation 解耦"。

- [ ] **Step 2：PublicationDao:125 同样改**

- [ ] **Step 3：测试 + Commit**

```bash
./gradlew :patra-catalog-infra:test
git add patra-catalog/patra-catalog-infra/src/main/java/.../VenueDao.java patra-catalog/patra-catalog-infra/src/main/java/.../PublicationDao.java
git commit -m "fix(catalog): VenueDao + PublicationDao LIKE → ILIKE（PG 大小写不敏感）"
```

---

### Task M5.5 `[Track: W5-T5]`：PublicationDao ORDER BY 修正

**Files:**
- Modify: `patra-catalog/patra-catalog-infra/src/main/java/dev/linqibin/patra/catalog/infra/persistence/dao/PublicationDao.java:210`

- [ ] **Step 1：替换 ORDER BY 子句**

第 210 行：

```sql
ORDER BY citation_count IS NULL, citation_count DESC, publication_year DESC
```

→

```sql
ORDER BY citation_count DESC NULLS LAST, publication_year DESC
```

- [ ] **Step 2：测试 + Commit**

```bash
./gradlew :patra-catalog-infra:test --tests "*PublicationReadAdapter*"
git add patra-catalog/patra-catalog-infra/src/main/java/.../PublicationDao.java
git commit -m "fix(catalog): PublicationDao ORDER BY 使用 NULLS LAST 显式语义"
```

---

### Task M5.6 `[Track: W5-T6]`：TaskRunDao CAST 修正

**Files:**
- Modify: `patra-ingest/patra-ingest-infra/src/main/java/dev/linqibin/patra/ingest/infra/persistence/dao/TaskRunDao.java:55`

- [ ] **Step 1：替换 CAST 语法**

第 55 行：

```sql
SET checkpoint = CAST(:checkpointJson AS JSON), ...
```

→

```sql
SET checkpoint = CAST(:checkpointJson AS jsonb), ...
```

- [ ] **Step 2：测试 + Commit**

```bash
./gradlew :patra-ingest-infra:test
git add patra-ingest/patra-ingest-infra/src/main/java/.../TaskRunDao.java
git commit -m "fix(ingest): TaskRunDao CAST AS JSON → AS jsonb"
```

---

### Task M5.7 `[Track: W5-T7]`：Java 注释批量更新（6 文件）

**Files:**
- Modify: `linqibin-spring-boot-starter-jpa/src/main/java/.../BaseJpaEntity.java`（JSON 字段注释简化）
- Modify: `patra-catalog/patra-catalog-infra/src/main/java/.../MeshQualifierEntity.java:36, 101`
- Modify: `patra-registry/patra-registry-infra/src/main/java/.../ProvExprRenderRuleEntity.java:66, 73, 80`
- Modify: `patra-registry/patra-registry-infra/src/main/java/.../SysDictItemEntity.java:78`
- Modify: `linqibin-commons-core/src/main/java/.../StringUtils.java:11, 40`

- [ ] **Step 1：搜索所有"MySQL"字样**

```bash
grep -rln "MySQL\|mysql" patra-*/src/main/java/ linqibin-*/src/main/java/ | xargs grep -n "MySQL\|mysql"
```

- [ ] **Step 2：逐个文件更新注释**

- `BaseJpaEntity.recordRemarks` JavaDoc："PostgreSQL → JSONB，MySQL → JSON" → "PostgreSQL → JSONB（Hibernate 7 PG dialect 自动映射 SqlTypes.JSON）"
- `MeshQualifierEntity` 第 36/101 行：删除 MySQL JSON 字样
- `ProvExprRenderRuleEntity` 第 66/73/80 行：把"MySQL 生成列，由数据库自动计算"改为"PG 生成列（GENERATED ALWAYS AS ... STORED），表达式见 V1__init_registry_schema.sql"
- `SysDictItemEntity` 第 78 行：把"MySQL GENERATED ALWAYS AS 列"改为"PG GENERATED ALWAYS AS (...) STORED 列"
- `StringUtils.java` 第 11/40 行 LIKE ESCAPE 注释：去掉"MySQL"字样，改为通用 ANSI 描述（PG 行为与 MySQL 一致）

- [ ] **Step 3：Commit**

```bash
git add -A
git commit -m "docs: 6 个 Java 文件注释从 MySQL 描述改为 PG / 通用描述"
```

---

### Task M5.8 `[Track: W5-T8]`：ICU4J 注释更新（3 文件）

**Files:**
- Modify: `patra-catalog/patra-catalog-infra/src/main/java/.../PublicationRepositoryAdapter.java:109, 452, 1196`
- Modify: `patra-catalog/patra-catalog-infra/src/main/java/.../batch/organization/RorOrganizationParser.java:353, 370, 392, 402, 408`
- Modify: `patra-catalog/patra-catalog-infra/src/main/java/.../batch/author/PubMedComputedAuthorParser.java:55, 64, 137, 144, 199`

- [ ] **Step 1：批量替换注释**

把"匹配 MySQL `utf8mb4_0900_ai_ci`"、"模拟 MySQL collation"等字样改为：

> "用于解析后内存对象的 collation-aware 去重，与 DB 无关；ICU4J PRIMARY 强度对齐外部数据源的 accent-/case-insensitive 期望（spec §4.24、§4.55）"

- [ ] **Step 2：Commit**

```bash
git add -A
git commit -m "docs(catalog): ICU4J Collator 注释更新（与 DB collation 解耦说明）"
```

---

### Task M5.9 `[Track: W5-T9]`：MeshScr/MeshDescriptor IT 测试断言更新

**Files:**
- Modify: `patra-catalog/patra-catalog-infra/src/test/java/.../MeshScrRepositoryAdapterIT.java:346-348`
- Modify: `patra-catalog/patra-catalog-infra/src/test/java/.../MeshDescriptorRepositoryAdapterIT.java:322-324`

- [ ] **Step 1：先跑这两个测试看实际行为**

```bash
./gradlew :patra-catalog-infra:test --tests "*MeshScrRepositoryAdapterIT*" --tests "*MeshDescriptorRepositoryAdapterIT*"
```

记录实际匹配数。

- [ ] **Step 2：把"依赖 collation"注释改为"PG C collation 下确定单匹配"**

把：

```java
// 如果 MySQL 使用 utf8mb4_0900_ai_ci（大小写不敏感），则可能匹配多个
// 如果使用 utf8mb4_bin（大小写敏感），则只匹配一个
// 此测试记录实际行为
```

改为：

```java
// PG `C` collation（spec §4.22）大小写敏感，确定匹配单个
```

并把断言从可能多个改为确定单个（如 `assertEquals(1, result.size())`）。

- [ ] **Step 3：Commit**

```bash
git add -A
git commit -m "test(catalog): MeshScr/MeshDescriptor IT 断言更新为 PG C collation 单匹配语义"
```

---

### Task M5.10 `[Track: W5-sync]`：M5 全量测试

```bash
./gradlew test
```

期望：全模块测试通过（含 ArchUnit、IT、Unit）。

---

## Milestone M6：application*.yml 配置切换

**目标**：4 个 boot 模块的 main/dev profile 配置全部指向 PG。

**M6 验证**：4 个 boot 模块依次 bootRun，`/actuator/health` UP。

---

### Task M6.1 `[Track: W6-T1]`：4 个 application.yml driver-class-name 切换

**Files:**
- Modify: `patra-ingest/patra-ingest-boot/src/main/resources/application.yml:29`
- Modify: `patra-registry/patra-registry-boot/src/main/resources/application.yml:66`
- Modify: `patra-catalog/patra-catalog-boot/src/main/resources/application.yml:48`
- Modify: `patra-object-storage/patra-object-storage-boot/src/main/resources/application.yml:24`

- [ ] **Step 1：4 处统一替换**

```yaml
driver-class-name: com.mysql.cj.jdbc.Driver
```

→

```yaml
driver-class-name: org.postgresql.Driver
```

- [ ] **Step 2：Commit**

```bash
git add patra-*/patra-*-boot/src/main/resources/application.yml
git commit -m "feat(config): 4 boot 模块 driver-class-name → org.postgresql.Driver"
```

---

### Task M6.2 `[Track: W6-T2]`：4 个 application-dev.yml JDBC URL 切换（含 catalog 独立 batch DS）

**Files:**
- Modify: `patra-ingest/patra-ingest-boot/src/main/resources/application-dev.yml:7`
- Modify: `patra-registry/patra-registry-boot/src/main/resources/application-dev.yml:14`
- Modify: `patra-catalog/patra-catalog-boot/src/main/resources/application-dev.yml:19, 50`（5 处：业务库 + batch 独立库）
- Modify: `patra-object-storage/patra-object-storage-boot/src/main/resources/application-dev.yml:3`

- [ ] **Step 1：URL 替换 + 移除 MySQL 专用 URL 参数**

把：

```yaml
url: jdbc:mysql://127.0.0.1:13306/patra_ingest?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC&rewriteBatchedStatements=true
```

改为：

```yaml
url: jdbc:postgresql://127.0.0.1:15432/patra_ingest
```

对其他文件按 db 名同样替换：
- registry → `patra_registry`
- catalog 业务库 → `patra_catalog`
- catalog batch 独立 DS → `patra_batch`
- object-storage → `patra_storage`

对 object-storage 第 3 行 `${STORAGE_DB_URL:jdbc:mysql://localhost:13306/patra_storage}`：默认值改为 `jdbc:postgresql://localhost:15432/patra_storage`，保留 env var 占位。

- [ ] **Step 2：账号 root → postgres**

同时把同一文件中数据库账号 `root` → `postgres`（密码 `123456` 保持）。

- [ ] **Step 3：Commit**

```bash
git add patra-*/patra-*-boot/src/main/resources/application-dev.yml
git commit -m "feat(config): 5 处 dev profile JDBC URL → PG; 账号 root → postgres"
```

---

### Task M6.3 `[Track: W6-T3]`：删除 Flyway baseline-on-migrate 与 baseline-version

**Files:**
- Modify: 所有 boot 模块 `application.yml` 中 `spring.flyway` 块

- [ ] **Step 1：定位 4 个 application.yml 中 Flyway 配置**

```bash
grep -rn "baseline-on-migrate\|baseline-version" patra-*/patra-*-boot/src/main/resources/
```

- [ ] **Step 2：删除两行（每个 boot 各一处）**

每个 boot 的 application.yml flyway 块：

```yaml
flyway:
  enabled: true
  locations: classpath:db/migration
  baseline-on-migrate: true    # ← 删除
  baseline-version: 0.1.0      # ← 删除
```

→

```yaml
flyway:
  enabled: true
  locations: classpath:db/migration
```

- [ ] **Step 3：Commit**

```bash
git add patra-*/patra-*-boot/src/main/resources/application.yml
git commit -m "feat(config): 删除 Flyway baseline-on-migrate/baseline-version（绿地空库全量执行）"
```

---

### Task M6.4 `[Track: W6-sync]`：M6 验证 — 4 boot bootRun

```bash
# 重置所有业务库
for db in patra_registry patra_ingest patra_catalog patra_storage patra_batch; do
  docker exec patra-postgres psql -U postgres -c "DROP DATABASE IF EXISTS $db; CREATE DATABASE $db;"
done

# 依次启动每个 boot
for svc in registry ingest catalog object-storage; do
  ./gradlew :patra-$svc-boot:bootRun &
  PID=$!
  sleep 40
  curl -s http://localhost:$(case $svc in registry) echo 6000 ;; ingest) echo 6100 ;; catalog) echo 6200 ;; object-storage) echo 6300 ;; esac)/actuator/health
  kill $PID 2>/dev/null
done
```

期望：每个服务 `/actuator/health` 返回 `{"status":"UP"}`。

---

## Milestone M7：文档同步

**目标**：所有 MySQL 引用更新为 PG。

**M7 验证**：`grep -ri "mysql\|MySQL" --include="*.md" --include="*.toml" patra-*/  linqibin-*/ .claude/ .codex/` 应无意外残留（除显式说明 XXL-Job 仍用 MySQL 的描述）。

---

### Task M7.1 `[Track: W7-T1]`：核心规则文档与 Agent 配置

**Files:**
- Modify: `.claude/rules/project-info.md:8`
- Modify: `.claude/agents/test-checker.md:52`
- Modify: `.codex/agents/test-checker.toml:47`
- Modify: `../patra-infra/.claude/CLAUDE.md:15, 62, 72, 88, 124`

- [ ] **Step 1：.claude/rules/project-info.md 技术栈**

把 `MySQL 8.x` → `PostgreSQL 17`。

- [ ] **Step 2：两个 test-checker（.claude + .codex）**

`MySQLContainerInitializer` → `PostgreSQLContainerInitializer`（两文件分别 line 52 / 47）。

- [ ] **Step 3：patra-infra/.claude/CLAUDE.md 5 处**（按 spec §4.57）

- line 15：`MySQL/Redis/Consul` → `PostgreSQL/Redis/Consul`
- line 62：`MySQL | ~/.patra/docker/mysql/data` → `PostgreSQL | ~/.patra/docker/postgres/data`
- line 72：`MySQL | 13306 | root / 123456` → `PostgreSQL | 15432 | postgres / 123456`
- line 88：`lsof -i :13306  # 检查 MySQL 端口` → `lsof -i :15432  # 检查 PostgreSQL 端口`
- line 124：`MySQL + Redis + Consul` → `PostgreSQL + Redis + Consul`

- [ ] **Step 4：Commit**

```bash
git add .claude/rules/project-info.md .claude/agents/test-checker.md .codex/agents/test-checker.toml
git commit -m "docs: project-info + test-checker (.claude + .codex) 同步 PG"
cd ../patra-infra
git add .claude/CLAUDE.md
git commit -m "docs(infra): CLAUDE.md 5 处端口/路径/服务名 → PG"
cd -
```

---

### Task M7.2 `[Track: W7-T2]`：根 README + 服务 README

**Files:**
- Modify: `README.md`（根）
- Modify: `patra-ingest/README.md`
- Modify: `patra-object-storage/README.md`

- [ ] **Step 1：根 README**

`MySQL 8.x` / `MySQL 8.0+` → `PostgreSQL 17`。端口 13306 → 15432。`mysql` cli 命令 → `psql`。

- [ ] **Step 2：服务 README**

`patra-ingest/README.md` 第 240 行 `# 启动 MySQL + RocketMQ + MinIO` → `# 启动 PostgreSQL + RocketMQ + MinIO`。

`patra-object-storage/README.md` 第 251/284/294/301/353 行：JDBC URL、版本、CHARSET 描述全部改 PG。

- [ ] **Step 3：Commit**

```bash
git add README.md patra-ingest/README.md patra-object-storage/README.md
git commit -m "docs: 根 + ingest + object-storage README 同步 PG"
```

---

### Task M7.3 `[Track: W7-T3]`：Starter README（jpa/test/batch）

**Files:**
- Modify: `linqibin-spring-boot-starter-jpa/README.md`
- Modify: `linqibin-spring-boot-starter-test/README.md`
- Modify: `linqibin-spring-boot-starter-batch/README.md`

- [ ] **Step 1：starter-jpa README**

错误码对照表：1062 → 23505，1451/1452 → 23503（增加 23502、23514）。`flyway-mysql` → `flyway-database-postgresql`。`mysql-connector-j` → `postgresql`。

- [ ] **Step 2：starter-test README**

所有 `MySQLContainerInitializer` 用例 → `PostgreSQLContainerInitializer`。`mysql:8.0.36` → `postgres:17`。

- [ ] **Step 3：starter-batch README**

`schema-mysql.sql` → `schema-postgresql.sql`。`com.mysql.cj.jdbc.Driver` → `org.postgresql.Driver`。`jdbc:mysql://batch-db:3306` → `jdbc:postgresql://batch-db:5432`。

- [ ] **Step 4：Commit**

```bash
git add linqibin-spring-boot-starter-*/README.md
git commit -m "docs(starter): jpa/test/batch README 同步 PG"
```

---

### Task M7.4 `[Track: W7-T4]`：patra-infra/docker/README.md（14 处）

**Files:**
- Modify: `../patra-infra/docker/README.md`

- [ ] **Step 1：批量替换**

约 14 处 MySQL 引用（端口 13306 / 路径 mysql / 命令 `mysql` / 服务名）全部更新为 PG 对应项。

- [ ] **Step 2：Commit**

```bash
cd ../patra-infra
git add docker/README.md
git commit -m "docs(infra): docker README 14 处 MySQL 引用同步 PG"
cd -
```

---

### Task M7.5 `[Track: W7-T5]`：catalog docs（pubmed-parsing-gap-analysis.md）

**Files:**
- Modify: `patra-catalog/docs/pubmed-parsing-gap-analysis.md:12, 777`

- [ ] **Step 1：描述性更新**

`MySQL 表 (34 张 publication 相关表)` → `PostgreSQL 表`；`MySQL / Consul / Redis` → `PostgreSQL / Consul / Redis`。

- [ ] **Step 2：Commit**

```bash
git add patra-catalog/docs/pubmed-parsing-gap-analysis.md
git commit -m "docs(catalog): pubmed-parsing-gap-analysis 同步 PG 描述"
```

---

## Final Verification（M4-M7 完成后）

### Task FV.1 `[Track: FV-step1]`：端到端 4 服务启动

```bash
# 全量重置
for db in patra_registry patra_ingest patra_catalog patra_storage patra_batch; do
  docker exec patra-postgres psql -U postgres -c "DROP DATABASE IF EXISTS $db; CREATE DATABASE $db;"
done

# 顺序启动（registry → ingest → catalog → object-storage）
for svc in registry ingest catalog object-storage; do
  echo "Starting patra-$svc-boot..."
  ./gradlew :patra-$svc-boot:bootRun &
  PID=$!
  sleep 45
  HEALTH=$(curl -s http://localhost:$(case $svc in registry) echo 6000;; ingest) echo 6100;; catalog) echo 6200;; object-storage) echo 6300;; esac)/actuator/health)
  echo "$svc health: $HEALTH"
  [[ "$HEALTH" =~ "UP" ]] || { echo "❌ $svc 启动失败"; kill $PID; exit 1; }
  kill $PID
done
echo "✅ 4 服务全部启动成功"
```

### Task FV.2 `[Track: FV-step2]`：./gradlew test 全量

```bash
./gradlew test
```

期望：所有 IT/Unit/ArchUnit 测试通过。

### Task FV.3 `[Track: FV-step3]`：JSONB native query 抽样验证（spec §10.6）

```bash
docker exec patra-postgres psql -U postgres -d patra_catalog <<'SQL'
-- 准备一条 venue 测试数据
INSERT INTO cat_venue (id, ..., open_access, citation_metrics)
VALUES (1, ..., '{"oaType": "gold"}'::jsonb, '{"hIndex": 42}'::jsonb);

-- 验证 ->> 操作符
SELECT open_access ->> 'oaType', (citation_metrics ->> 'hIndex')::numeric FROM cat_venue WHERE id = 1;
-- 期望: gold | 42
SQL
```

### Task FV.4 `[Track: FV-step4]`：触发器抽样验证（spec §7.5）

```bash
docker exec patra-postgres psql -U postgres -d patra_registry <<'SQL'
-- 插入一条 sys_dict_type
INSERT INTO sys_dict_type (id, type_code, type_name) VALUES (1, 'test_type', 'Test');
-- 不动 updated_at 的 UPDATE，应被触发器刷新
SELECT updated_at FROM sys_dict_type WHERE id = 1;  -- 记录 t1
UPDATE sys_dict_type SET type_name = 'Test2' WHERE id = 1;
SELECT updated_at FROM sys_dict_type WHERE id = 1;  -- t2 应 > t1
-- 显式设 updated_at 的 UPDATE，触发器应跳过
UPDATE sys_dict_type SET type_name = 'Test3', updated_at = '2020-01-01' WHERE id = 1;
SELECT updated_at FROM sys_dict_type WHERE id = 1;  -- 期望 2020-01-01
SQL
```

### Task FV.5 `[Track: FV-step5]`：HikariCP pool size 现场观察（spec §10.7）

启动 catalog 服务（最高 pool=20），并发跑一份代表性 API 请求，观察 PG 活跃连接：

```bash
./gradlew :patra-catalog-boot:bootRun &
sleep 30
# 用 ab 或 wrk 压一份典型查询，并发 20-50
# ...
docker exec patra-postgres psql -U postgres -d patra_catalog -c "SELECT COUNT(*) FROM pg_stat_activity WHERE datname = 'patra_catalog' AND state = 'active';"
```

记录结果。如果 active connections 远低于 20，下调 `maximum-pool-size` 到 10 以减少 PG backend 压力。

### Task FV.6 `[Track: FV-step6]`：函数索引命中验证（spec §10.8）

```bash
docker exec patra-postgres psql -U postgres -d patra_catalog <<'SQL'
EXPLAIN ANALYZE
SELECT * FROM cat_author WHERE LOWER(display_name) LIKE LOWER('%smith%');
SQL
```

期望 plan 中出现 `Bitmap Index Scan on idx_author_display_name_lower` 或类似走函数索引的节点。

### Task FV.7 `[Track: FV-step7]`：标识 git log 完整提交链

```bash
git log --oneline main..HEAD | head -80
```

应见 M1-M7 + FV 全部 commits。

---

## 风险与回滚

每个里程碑后 commit 即留 rollback point。任一里程碑失败：

```bash
git log --oneline -20  # 找最后绿色 commit
git reset --hard <commit-hash>
```

不需要回滚 PG 容器（spec §8）。

如需短期回退到 MySQL：

```bash
git checkout main -- patra-infra/docker/docker-compose.core.yaml gradle/libs.versions.toml
# 然后 docker compose up mysql
```

---

## 现场验证项汇总（spec §10）

| # | 项 | 验证里程碑 |
|---|---|---|
| 10.1 | Java 25 + virtual threads + pgjdbc pinning | FV.1 之后压测 |
| 10.2 | Boot 4 BOM 实际锁定的 Flyway/pgjdbc 版本 | 实施前置 |
| 10.3 | STORED 生成列 + `split_part` IMMUTABLE | M4.1 / M4.14 dry-run 时 |
| 10.4 | Spring Batch 5 实际 schema-postgresql.sql | M3.2 抽取时 |
| 10.5 | XXL-Job 容器与 postgres 容器无冲突 | M1.8 之后 |
| 10.6 | catalog ILIKE 与 MySQL collation 行为对齐 | FV.3 之后 |
| 10.7 | HikariCP pool size 在 PG 进程模型下合理性 | FV.5 |
| 10.8 | 函数索引 ContainingIgnoreCase 命中 | FV.6 |

---

## 执行模式回顾

本计划已**并行化拆分**为 7 个 Wave，每个 Wave 内最多 16 个 Track 并行。Subagent-driven 执行时 dispatcher 按 Wave 顺序推进，每个 Wave 内一次性 fan out 该 Wave 全部 Track 的 subagents（参见上文"并行执行编排"章节）。

**每个 task 标题已附 `[Track: W?-T?]` 标签**，dispatcher 可直接用 `grep` 抽取每个 Wave 的全部 task：

```bash
PLAN=docs/superpowers/plans/2026-05-17-mysql-to-postgresql-migration.md
# 列出 W4-catalog 所有 track
grep "Track: W4c-" $PLAN
# 列出 W5 全部 9 个 track
grep "Track: W5-" $PLAN
```

---

*Plan covers 全部 spec §6 M1-M7 + spec §7 验证计划 + spec §10 现场验证项。共 7 milestones / 69 tasks / 16 个 W4 并行 track（峰值）。每个 task 自包含且有明确 commit boundary。*
