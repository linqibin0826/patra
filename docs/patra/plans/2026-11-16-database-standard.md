# 库表规范实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task (Patra 约定内联执行，不派子代理). Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 六个服务的库表按同一份规范重建，规范由迁移后真实表结构的检查和 ArchUnit 守住。

**Architecture:** 先在 `patra-tool-test` 做结构检查 `SchemaStandard` 和写侧规则 `PersistenceTechnologyRules`，再改 JPA 基类。之后逐个服务重建：先登记表、加结构检查测试并看它失败，再重写迁移脚本、改代码，直到结构检查和原有测试都通过。迁移脚本检查（R6）放在最后加严，因为现有脚本里就有 `CREATE SCHEMA` 和外键。

**Tech Stack:** Java 25、Spring Boot 4.1.2、Hibernate 7.4、Spring Data JPA、`JdbcClient`、Flyway 12、PostgreSQL 17、ArchUnit、Testcontainers、Gradle Kotlin DSL。

**Spec:** [`docs/patra/specs/2026-11-16-database-standard-design.md`](../specs/2026-11-16-database-standard-design.md)；规范本身 [`.claude/rules/tech/database.md`](../../../.claude/rules/tech/database.md)；版本范围 [`docs/patra/release-specs/v0.21-database-standard.md`](../release-specs/v0.21-database-standard.md)。

## Global Constraints

- 分支 `feat/v0.21-database-standard`，在工作树 `.claude/worktrees/v0.21-database-standard` 里执行（主检出在 `feat/405-event-registry-update-mode` 上）。每个 Task 结束本地提交一次，不推送；只暂存本 Task 的路径，提交前看 `git diff --cached --stat`。
- JetBrains MCP 绑定的是主检出的 IDEA 项目，本会话又连不上，重命名改用文本替换，替换后必跑 `./gradlew compileJava compileTestJava` 确认没有漏改的引用。
- 提交信息：中文动词起头（commitlint 拒绝大写开头），body 每行不超过 100 字符，末尾带 `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`。spotless 改了文件就 `git add -u` 再提交。
- 规则以 `database.md` 为准，违规消息引用它的节号 `§1`～`§8`。前缀：registry `sys`、catalog `cat`、identity `idn`、interaction `itr`、notification `ntf`、analytics `anl`。
- 触发器函数 `set_audit_times()` 的函数体与设计文档 3.1 一字不差，每个服务的第一个迁移脚本里定义；每张表挂 `trg_{表名}_audit_times`（`BEFORE INSERT OR UPDATE … FOR EACH ROW`）。分区表只在父表上建。
- 迁移脚本原地重写：版本号 `V{yyyyMMddHHmmss}__{描述}.sql`，同一服务内按依赖顺序递增，函数脚本最早；按子域分目录；种子数据用 `R__` 脚本、`ON CONFLICT (id) DO UPDATE`、`id` 从 1 写死。
- 不建外键、不用 `UNIQUE` 约束（自然键 `CREATE UNIQUE INDEX`）、不建第二个 schema。约束和索引名 `uk_` / `ck_` / `idx_` + 完整表名 + 不超过 3 个词的用途短语，不超过 63 字节。表和业务列写中文 `COMMENT`。
- 接口返回的字段名不变，门户不改。业务时间列由应用用注入的 `Clock` 写。
- 写侧技术：registry、catalog、identity 用 JPA（不经实体的写入只能是 Dao 上带 `@Modifying(flushAutomatically = true, clearAutomatically = true)` 的 `@Query`）；interaction、notification、analytics 用 `JdbcClient`，主表 INSERT 写 `created_by`、UPDATE 写 `updated_by`。
- TDD：先写失败的测试再实现。注释按 `.claude/rules/code-style.md` 的注释标准，`///` 风格。
- 跑集成测试前确认 OrbStack 在运行（`open -a OrbStack`，等 `docker info` 成功）。验证命令按模块跑；全量门控 `./gradlew check integrationTest` 只在最后一个代码 Task 跑。
- 改了 `*.gradle.kts` 要告诉用户在 IDEA 里点「加载 Gradle 更改」。
- 改名：JPA 服务的实体字段随列改名，领域模型字段不动；原生 SQL 服务只改库和 SQL 字符串，Java 标识符不动（设计 4.4）。
- 测试里要把审计时间改成过去的值时，用 `ALTER TABLE … DISABLE TRIGGER trg_{表名}_audit_times` 包住那条 `UPDATE`，改完再 `ENABLE`；业务时间列直接改。
- 不经实体写主表时的操作人：JPA 服务取 JPA 审计用的同一个 `AuditorAware<Long>`（适配器注入，`getCurrentAuditor().orElse(null)` 作为参数传给 Dao）；原生 SQL 服务里，操作人必然是行的主人时直接用已有的用户 ID 参数，可能不是时端口加 `Long actorUserId`，消息消费、定时任务、匿名写入传 `null`。

## Review Focus

1. **值对象行改成 `Persistable` 后，更新路径重新 new 出带旧 `id` 的实体再保存**，会直接插入、撞主键。期望：聚合保存两次（第二次是修改）照常成功。测试放 Task 6 与 catalog 各 Task 的仓储集成测试。
2. **`@Modifying` 补上 `clearAutomatically = true` 后，批量语句之前拿到的托管实体变成游离态**：适配器在批量删除之后接着改父实体，改动不再被跟踪、懒加载报错。期望：「整组删再插」的保存往返照常。测试放 Task 9 与 Task 12 的仓储往返用例。
3. **去掉 `@SoftDelete` 后漏补「未删除」条件**：被删的文献、期刊、机构、作者、字典项仍出现在详情、搜索、列表、继承来的 `findById` 里。期望：删除后所有读路径都查不到，只有同步任务按设计读已删行。测试放 Task 7 与 Task 11。
4. **业务时间换列后，排序、编辑宽限期、提醒时间窗口仍按审计时间算**：行被别的修改刷新了 `updated_at`，评论的可编辑期、收藏夹的「更新于」、邮件提醒的窗口跟着变。期望：只认新的业务时间列。测试放 Task 15、Task 16 与 Task 18。
5. **identity 保存用户时清空 `record_remarks`**（验证场景 4）。期望：库里写的备注在改昵称后还在。测试放 Task 5（基类）与 Task 9（identity）。

---

## 第一阶段：规范和守护（PAP-200、PAP-201）

PAP-199 的交付物（规则文件、设计文档、五条推断的验证测试）已在工作树里，随 Task 1 一起提交。

### Task 1: 结构检查——登记文件、报告格式与 §1（PAP-200）

**Files:**
- Create: `patra-tool/patra-tool-test/src/main/java/dev/patra/tool/test/schema/SchemaStandard.java`、`TableRegistry.java`、`TableTier.java`、`SchemaViolation.java`、`SchemaStandardReport.java`（同包）
- Create: `patra-tool/patra-tool-test/src/integrationTest/resources/schema/tables.yml`、`schema/baseline.sql`
- Modify: `patra-tool/patra-tool-test/build.gradle.kts`（`implementation("org.yaml:snakeyaml")`，版本由 Spring Boot 管）
- Test: `patra-tool/patra-tool-test/src/integrationTest/java/dev/patra/tool/test/schema/SchemaStandardIT.java`
- 一并提交：`.claude/rules/tech/database.md`、`.claude/rules/tech/jpa.md`、`docs/patra/release-specs/v0.21-database-standard.md`、`docs/patra/specs/2026-11-16-database-standard-design.md`、本计划、`patra-tool/patra-tool-jpa/src/integrationTest/` 下 PAP-199 新增的验证测试与夹具

**Interfaces:**
- Produces:
  - `SchemaStandard.inspect(DataSource dataSource, String registryResource): SchemaStandardReport`——`registryResource` 是类路径资源名（服务里传 `"db/tables.yml"`）
  - `record TableRegistry(String prefix, Map<String, TableEntry> tables, Set<String> framework)`，`TableRegistry.load(String resource)`；`record TableEntry(TableTier tier, String note)`；`enum TableTier { MAIN, OTHER }`，YAML 里写 `main` / `other`
  - `record SchemaViolation(String section, String table, String message)`，`section` 形如 `"§2"`，`toString()` 输出 `[§2] itr_comment：…`
  - `record SchemaStandardReport(List<SchemaViolation> violations)`，紧凑构造器 `List.copyOf`

测试夹具：`tables.yml` 前缀 `tst`，登记 `tst_note`（main）、`tst_note_tag`（other）、`tst_event`（other，按 `user_id` 哈希分区，子表 `tst_event_p00`、`tst_event_p01`），框架表 `flyway_schema_history`。`baseline.sql` 建出完全合规的这几张表（含 `set_audit_times()`、触发器、注释、一个部分唯一索引、一个检查约束），框架表故意不带 `id` 和前缀。

- [ ] **Step 1: 写失败测试** `SchemaStandardIT`：用 `PostgreSQLContainerInitializer` 起容器（照 `RedisContainerInitializerIT` 的写法初始化 `GenericApplicationContext`，从环境取连接参数建 `DriverManagerDataSource`）。`@BeforeEach` 执行 `DROP SCHEMA public CASCADE; CREATE SCHEMA public;` 再执行 `baseline.sql`。断言统一写成 `extracting(SchemaViolation::section, SchemaViolation::table)`：
  - `should_pass_compliant_baseline`：`violations()` 为空（同时证明框架表不查主键和列、分区子表不算未登记）
  - `should_reject_extra_schema`：`CREATE SCHEMA stray` → `("§1", "stray")`
  - `should_reject_unregistered_table`：新建合规的 `tst_stray` → `("§1", "tst_stray")`
  - `should_reject_registered_table_missing`：`DROP TABLE tst_note_tag` → `("§1", "tst_note_tag")`
  - `should_reject_table_without_prefix`：把 `tst_note_tag` 改名 `note_tag` 并在测试用登记文件变体里登记 → `("§1", "note_tag")`（变体放 `schema/tables-unprefixed.yml`）
  - `should_report_all_violations_at_once`：同时建 schema 和未登记表 → 两条都在
- [ ] **Step 2:** `./gradlew :patra-tool:patra-tool-test:integrationTest --tests "*SchemaStandardIT"`。Expected: 编译失败（类不存在）。
- [ ] **Step 3:** 实现上面五个类。`SchemaStandard` 用 `JdbcTemplate` 查 `pg_namespace`（排除 `pg_%` 与 `information_schema`）、`pg_class`（`relkind IN ('r','p') AND NOT relispartition`）；每项检查一个私有方法返回违规列表，最后合并，不在第一条就停。框架表只参与 §1 的「登记」和「schema」检查，其余检查跳过。
- [ ] **Step 4:** 再跑同一命令。Expected: 6 个用例 PASS。
- [ ] **Step 5:** 先单独提交 PAP-199 的交付物（规则文件、release spec、设计文档、本计划、`patra-tool-jpa` 的验证测试与夹具）：`docs(db): 库表规范规则文件、工程设计与五条推断的验证测试 (PAP-199)`；再提交本 Task 的代码：`feat(test): 新增库表结构检查的登记文件与 schema 检查 (PAP-200)`。

### Task 2: 结构检查——主键、必备列与时间类型（§2～§4）

**Files:** Modify `SchemaStandard.java`；Test `SchemaStandardIT.java`

**Interfaces:**
- Consumes: Task 1 的全部类型。检查对象是已登记的非框架表；分区子表跳过。

- [ ] **Step 1: 写失败测试**（每个用例在 baseline 上做一处改动）：
  - `should_reject_primary_key_other_than_id`：新登记表 `tst_pair` 主键 `(note_id, tag_id)` → `("§2", "tst_pair")`
  - `should_reject_partitioned_primary_key_other_than_partition_key_and_id`：`tst_event` 的主键换成 `(user_id, created_at)`（PostgreSQL 要求分区表主键含分区键，所以不测 `(id)`）→ `("§2", "tst_event")`
  - `should_reject_foreign_key`：`tst_note_tag.note_id REFERENCES tst_note(id)` → `("§2", "tst_note_tag")`
  - `should_reject_unique_constraint`：`ALTER TABLE tst_note_tag ADD CONSTRAINT uk_tst_note_tag_pair UNIQUE (note_id, tag)` → `("§2", "tst_note_tag")`
  - `should_reject_missing_audit_times`：`ALTER TABLE tst_note_tag DROP COLUMN updated_at` → `("§3", "tst_note_tag")`
  - `should_reject_missing_audit_trigger`：`DROP TRIGGER trg_tst_note_tag_audit_times ON tst_note_tag` → `("§3", "tst_note_tag")`
  - `should_reject_main_table_missing_columns`：`tst_note` 删 `record_remarks` 并把 `ip_address` 改成 `BYTEA` → 两条 `("§3", "tst_note")`
  - `should_reject_main_columns_on_other_table`：`tst_note_tag` 加 `created_by BIGINT` 与 `version BIGINT` → `("§3", "tst_note_tag")`
  - `should_reject_username_column`：`tst_note_tag` 加 `username TEXT`、`created_by_name TEXT`，两条 `§3`（列名 `username`、`user_name` 精确匹配，`_by_name` 按后缀匹配，与 R6 一致）；`username` 只在登记表名为 `idn_user`、`idn_user_former_username` 时放行（用 `tables-identity.yml` 变体验证放行）
  - `should_reject_timestamp_without_time_zone`：`tst_note_tag` 加 `seen_at TIMESTAMP` → `("§4", "tst_note_tag")`
- [ ] **Step 2:** 跑 `*SchemaStandardIT`。Expected: 新用例 FAIL。
- [ ] **Step 3:** 在 `SchemaStandard` 加检查：主键列取 `pg_constraint`（`contype = 'p'`）的 `conkey` 按序转列名；分区键取 `pg_partitioned_table.partattrs`，期望主键 = 分区键列 + `id`，`id` 类型 `bigint`。外键 `contype = 'f'`、唯一约束 `contype = 'u'`。列类型用 `format_type(atttypid, atttypmod)`：审计时间 `timestamp with time zone` 且 `attnotnull`；主表四列分别 `bigint`、`bigint`、`inet`、`jsonb`。触发器用 `pg_trigger` + `pg_get_triggerdef`：函数 `set_audit_times`、含 `BEFORE INSERT OR UPDATE` 与 `FOR EACH ROW`。
- [ ] **Step 4:** 再跑。Expected: 全部 PASS。
- [ ] **Step 5:** 提交 `feat(test): 结构检查覆盖主键、外键、必备列与时间类型`。

### Task 3: 结构检查——命名、注释与失效索引（§6、§7）

**Files:** Modify `SchemaStandard.java`；Test `SchemaStandardIT.java`

- [ ] **Step 1: 写失败测试**：
  - `should_reject_misnamed_index`：`CREATE INDEX idx_tag_note ON tst_note_tag (note_id)` → `("§6", "tst_note_tag")`
  - `should_reject_index_kind_mismatch`：唯一索引用了 `idx_` 前缀 → `§6`
  - `should_flag_name_of_exactly_63_bytes`：建一个名字正好 63 字节、前缀正确的索引 → `§6`，消息含「可能被截断」
  - `should_reject_missing_comments`：`COMMENT ON COLUMN tst_note_tag.tag IS NULL` 与 `COMMENT ON TABLE tst_note IS NULL` → 两条 `§6`；标准列（`id`、两个审计时间、主表四列、`version`）没注释不报
  - `should_reject_invalid_index`：`UPDATE pg_index SET indisvalid = false WHERE indexrelid = 'idx_tst_note_tag_note'::regclass` → `("§7", "tst_note_tag")`
- [ ] **Step 2:** 跑 `*SchemaStandardIT`。Expected: 新用例 FAIL。
- [ ] **Step 3:** 实现：索引取 `pg_index`（跳过 `indisprimary`），唯一索引期望 `uk_{表名}_`、普通索引 `idx_{表名}_`；约束取 `contype IN ('c','x')`，检查约束期望 `ck_{表名}_`；`octet_length(name) = 63` 报可能截断。注释用 `obj_description(oid, 'pg_class')` 和 `col_description`。
- [ ] **Step 4:** 再跑。Expected: 全部 PASS。
- [ ] **Step 5:** 提交 `feat(test): 结构检查覆盖命名、注释与失效索引`。

### Task 4: 写侧技术的 ArchUnit 规则（PAP-200）

**Files:**
- Create: `patra-tool/patra-tool-test/src/main/java/dev/patra/tool/test/archunit/PersistenceTechnologyRules.java`
- Create: 夹具 `patra-tool/patra-tool-test/src/test/java/dev/patra/tool/test/archunit/fixtures/persistence/{jpacompliant,jpajdbcwrite,jpamodifying,jpanative,jpaentitybase,sqlcompliant,sqlentity,sqljdbcoutside}/…`
- Modify: `patra-tool/patra-tool-test/build.gradle.kts`（`testImplementation(project(":patra-tool:patra-tool-jpa"))`，夹具实体要继承真正的基类）
- Test: `patra-tool/patra-tool-test/src/test/java/dev/patra/tool/test/archunit/PersistenceTechnologyRulesTest.java`

**Interfaces:**
- Produces:
  - `PersistenceTechnologyRules.jpaService(String basePackage, Set<String> jdbcExemptClasses): List<ArchRule>`——`jdbcExemptClasses` 是全类名（catalog 传 `PgAdvisoryRunLockAdapter`）
  - `PersistenceTechnologyRules.sqlService(String basePackage): List<ArchRule>`
  - 「读适配器」= 实现了包名匹配 `..port.read..` 的接口的类
- 规则（JPA）：infra 里非读适配器、非豁免类不依赖 `JdbcClient` / `JdbcTemplate` / `NamedParameterJdbcTemplate`；`@Modifying` 方法必须带 `@Query` 且 `flushAutomatically`、`clearAutomatically` 都为 `true`；任何类不调用 `jakarta.persistence.Query#executeUpdate`；非读适配器不调用 `EntityManager#createNativeQuery`；`@Entity` 类必须可赋值给 `BaseJpaEntity` 或 `ValueObjectJpaEntity`（按全类名判断，规则类不依赖 JPA 工具模块）。
- 规则（SQL）：不依赖 `jakarta.persistence..`；依赖 `JdbcClient` 的类必须在 `{base}.infra..persistence..`。

- [ ] **Step 1: 写失败测试**（照 `TransactionRulesTest` 的 `violates(rule, scenario)` 写法，规则列表任一条违规即算违规）：`should_accept_compliant_jpa_service`、`should_reject_jdbc_write_in_jpa_service`、`should_accept_jdbc_in_read_adapter`、`should_accept_exempt_lock_adapter`、`should_reject_modifying_without_flush_and_clear`、`should_reject_modifying_without_query`、`should_reject_execute_update`、`should_reject_native_query_outside_read_adapter`、`should_reject_entity_without_standard_base`、`should_accept_compliant_sql_service`、`should_reject_jpa_in_sql_service`、`should_reject_jdbc_client_outside_persistence`。
- [ ] **Step 2:** `./gradlew :patra-tool:patra-tool-test:test --tests "*PersistenceTechnologyRulesTest"`。Expected: 编译失败。
- [ ] **Step 3:** 实现 `PersistenceTechnologyRules`，按 `HexagonalArchitectureRules` 的风格写 `ArchCondition`；每条规则 `.because(...)` 写明对应 `database.md` §8 的理由。
- [ ] **Step 4:** 再跑，并跑 `./gradlew :patra-tool:patra-tool-test:check`。Expected: PASS。
- [ ] **Step 5:** 提交 `feat(test): 新增按服务检查写侧技术的 ArchUnit 规则 (PAP-200)`。

各服务的 `*ArchitectureTest` 在各自的重建 Task 里接入这组规则（接早了会让还没改的服务一直失败）。

### Task 5: 主表基类——审计时间、操作人名、IP 与备注（PAP-201）

**Files:**
- Modify: `patra-tool/patra-tool-jpa/src/main/java/dev/patra/tool/jpa/entity/BaseJpaEntity.java`、`IdAwareEntity.java`（注释）、`autoconfigure/JpaAuditingConfiguration.java`
- Modify: `patra-tool/patra-tool-jpa/src/integrationTest/resources/db/migration/V1__create_jpa_it_audited_note.sql`（对齐新规范：`ip_address INET`，删两个 `*_by_name`，挂 `set_audit_times()` 触发器）、`JpaAuditingIT`、`JpaAuditingWithProviderIT`、`src/test/.../JpaAuditingConfigurationTest`
- Create: `patra-tool/patra-tool-jpa/src/integrationTest/java/dev/patra/tool/jpa/entity/BaseJpaEntityIT.java`
- Modify（让服务编译和测试保持通过）：
  - identity：7 个 `*JpaMapper` 的 `@Mapping(ignore)` 删掉已不存在的四个属性，`UserProfileJpaMapper` 里 builder 写两个时间的两行；聚合 `User`、`EmailToken`、`UserProfile`、`UserLoginRecord` 删 `createdAt` / `updatedAt` 及对应的 domain、app 单测断言；`EmailTokenRepositoryAdapter.CONSUME_SQL` 的 `RETURNING` 删两列；`UserEntity` 加只读字段 `createdAt`，`UserReadAdapter` 的 `joinedAt` 照旧读它；`UserLoginRecordRepositoryAdapterIT` 删 `getCreatedAt()` 断言
  - catalog：8 个 `*JpaMapper` 共 9 个方法的 `@Mapping(ignore)` 删四个属性
  - registry、catalog、identity 旧迁移脚本里的 `ip_address BYTEA` 改 `INET`（重建 Task 会整份重写，这里只为本 Task 结束时各服务测试仍通过）

**Interfaces:**
- Produces（`BaseJpaEntity` 字段）：`id`；`createdBy`（`@CreatedBy`，`updatable = false`）；`updatedBy`（`@LastModifiedBy`）；`ipAddress`（`java.net.InetAddress`，`@JdbcTypeCode(SqlTypes.INET)`）；`recordRemarks`（`String`，`@JdbcTypeCode(SqlTypes.JSON)`，`insertable = false, updatable = false`）；`version`（`@Version`）。删 `createdAt`、`updatedAt`、`createdByName`、`updatedByName`、`DEFAULT_SORT`。
- `JpaAuditingConfiguration`：`@EnableJpaAuditing(auditorAwareRef = "auditorAware")`，删 `dateTimeProvider` Bean。

- [ ] **Step 1: 写失败测试** `BaseJpaEntityIT`：
  - `should_keep_record_remarks_when_entity_merged`：保存一行，JDBC 写 `record_remarks = '{"note":"人工核对"}'`，再 new 一个同 `id`、同 `version`、`recordRemarks` 为空的实体 `saveAndFlush`，备注还在（Review Focus 5）
  - `should_round_trip_ip_address_as_inet`：存 `InetAddress.getByName("2001:db8::1")`，`SELECT host(ip_address)` 为 `2001:db8::1`，读回相等
  - `JpaAuditingIT` / `JpaAuditingWithProviderIT` 删掉时间两列的断言，只断言操作人
- [ ] **Step 2:** `./gradlew :patra-tool:patra-tool-jpa:integrationTest --tests "*BaseJpaEntityIT"`。Expected: FAIL（备注被覆盖为空；`byte[]` 字段编译不过）。
- [ ] **Step 3:** 按上面的接口改基类与审计配置；更新类注释（基类表格只剩两个）。
- [ ] **Step 4:** 改服务里受影响的代码（见 Files）。
- [ ] **Step 5:** `./gradlew :patra-tool:patra-tool-jpa:check :patra-tool:patra-tool-jpa:integrationTest`，再跑三个 JPA 服务：`./gradlew :patra-api:patra-registry:patra-registry-infra:integrationTest :patra-api:patra-identity:patra-identity-domain:test :patra-api:patra-identity:patra-identity-app:test :patra-api:patra-identity:patra-identity-infra:integrationTest :patra-api:patra-catalog:patra-catalog-infra:compileJava`。Expected: 全部 PASS。
- [ ] **Step 6:** 提交 `refactor(jpa): 主表基类不再映射审计时间，IP 改 INET，备注只读 (PAP-201)`。

### Task 6: 值对象基类直接插入，删掉子实体基类（PAP-201）

**Files:**
- Modify: `patra-tool/patra-tool-jpa/src/main/java/dev/patra/tool/jpa/entity/ValueObjectJpaEntity.java`
- Delete: `ChildJpaEntity.java`、`src/test/.../ChildJpaEntityTest.java`
- Modify: catalog 的 7 个子实体改继承 `ValueObjectJpaEntity`：`JcrRatingEntity`、`CasRatingEntity`、`CasWarningEntity`、`ScopusRatingEntity`、`VenueIndexingHistoryEntity`、`AuthorNameVariantEntity`、`AuthorOrcidEntity`
- Modify: `PersistableInsertIT` 与夹具：`JpaITPreassignedEntity` 改成不继承任何基类的普通实体（反例），删 `JpaITPersistableEntity` / `JpaITPersistableDao`，新建 `JpaITValueObjectEntity extends ValueObjectJpaEntity` 与它的 Dao；V2 测试脚本随之改表
- Test: `ValueObjectJpaEntityTest`、`PersistableInsertIT`、`PublicationFullTextRepositoryAdapterIT`、`PubmedFileRepositoryAdapterIT`

已排查（2026-11-16）：catalog 里值对象实体的保存都属于两类，改成直接插入后不会撞主键。registry 主代码不保存值对象实体，identity 没有值对象实体。

- 先删后插或按差异插入，新行都分配新的雪花 ID：机构子表、OA 位置、元数据补插、期刊标识符、LetPub / Scopus 评级。
- 先查出托管实体再改：全文、PubMed 文件。查出的实体经 `@PostLoad` 不再是新行。

**Interfaces:**
- Produces: `ValueObjectJpaEntity implements Persistable<Long>`。字段 `@Transient private boolean fresh = true`，带 `@Builder.Default`、`@EqualsAndHashCode.Exclude`、`@ToString.Exclude`、`@Getter(AccessLevel.NONE)`、`@Setter(AccessLevel.NONE)`；`isNew()` 返回它；`@PostLoad @PostPersist void markNotNew()` 置 `false`。

- [ ] **Step 1: 写失败测试**：`ValueObjectJpaEntityTest.should_be_new_until_loaded_or_persisted`；`PersistableInsertIT` 改成「普通预分配实体保存 10 行准备 11 条语句」「值对象基类保存 10 行准备 1 条」，加 `should_update_loaded_value_object_row`（查出来改字段再保存，只发 UPDATE、不插入）；两个「先查后改」的仓储各加一个保存两次的用例：`PublicationFullTextRepositoryAdapterIT.should_save_same_fulltext_twice`、`PubmedFileRepositoryAdapterIT.should_save_same_file_twice`（Review Focus 1）。
- [ ] **Step 2:** `./gradlew :patra-tool:patra-tool-jpa:test :patra-tool:patra-tool-jpa:integrationTest --tests "*PersistableInsertIT"`。Expected: FAIL。
- [ ] **Step 3:** 实现；删 `ChildJpaEntity`，改 7 个子实体（catalog 不读它们的时间和版本）。
- [ ] **Step 4:** `./gradlew :patra-tool:patra-tool-jpa:check :patra-tool:patra-tool-jpa:integrationTest :patra-api:patra-registry:patra-registry-infra:integrationTest :patra-api:patra-catalog:patra-catalog-infra:integrationTest`。Expected: PASS。若出现主键冲突，说明那条保存路径 new 出了带旧 `id` 的值对象实体（Review Focus 1）：改成先查后改，或整组删再插，并在该仓储的 IT 里加「保存两次」用例。
- [ ] **Step 5:** 提交 `refactor(jpa): 值对象实体直接插入，删除子实体基类 (PAP-201)`。

---

## 第二阶段：六个服务按规范重建（PAP-202 ～ PAP-206）

每个服务的第一步都一样：写 `db/tables.yml`、加 `{Module}SchemaStandardIT`，跑它拿到违规清单（这就是失败的测试），然后重写迁移脚本、改代码，直到它和原有测试都通过。`{Module}SchemaStandardIT` 放 infra 的 `src/integrationTest/java/dev/patra/{svc}/infra/`，用本服务已有的 ITBootstrap 和 `{Module}ITPostgreSQLContainerInitializer`，只断言：

```java
assertThat(SchemaStandard.inspect(dataSource, "db/tables.yml").violations()).isEmpty();
```

同一个 JVM 里的集成测试共用一个容器库，结构检查看的是整个库：别的测试临时建的表要在用例结束时删掉，否则会被报成「未登记」。

框架表登记在 `framework` 下：所有服务都有 `flyway_schema_history`；identity、interaction 加 `event_publication`；notification 加 `event_publication`、`processed_event`。

### Task 7: registry 重建（PAP-202）

**Files:**
- Create: `patra-api/patra-registry/patra-registry-infra/src/main/resources/db/tables.yml`、`db/migration/V{ts}__create_set_audit_times.sql`、`db/migration/dictionary/V{ts}__create_dictionary.sql`、`db/seed/R__seed_dict_country.sql`、`db/seed/R__seed_dict_language.sql`
- Delete: `db/migration/V1__init_registry_schema.sql`、`V2__seed_dict_country.sql`、`V3__seed_dict_language.sql`
- Modify: 实体 `SysDictTypeEntity`、`SysDictItemEntity`、`SysDictItemAliasEntity`；`SysDictTypeDao`、`SysDictItemDao`、`SysDictItemAliasDao`、`DictionaryRepositoryAdapter`；`patra-registry-boot/src/main/resources/application.yml`（`spring.flyway.locations: classpath:db/migration,classpath:db/seed`）；`patra-registry-infra/src/testFixtures/resources/application-test.yml`（删 `target`，`locations: classpath:db/migration`）；`RegistryArchitectureTest`
- Test: `RegistrySchemaStandardIT`（新）、`DictionaryRepositoryAdapterIT`、`ReferenceStandardRepositoryAdapterIT`、`DictionarySeedIT`

**Interfaces:**
- Consumes: Task 1～3 的 `SchemaStandard`，Task 4 的 `PersistenceTechnologyRules.jpaService`，Task 5～6 的基类。
- 档位：`sys_dict_type`、`sys_dict_item`、`sys_reference_standard` 主表；`sys_dict_item_alias` 其他表。

- [ ] **Step 1: 写失败测试**：`tables.yml` 登记上面 4 张表（框架表 `flyway_schema_history`）；`RegistrySchemaStandardIT.should_meet_database_standard`；`RegistryArchitectureTest` 加一个汇总 `PersistenceTechnologyRules.jpaService("dev.patra.registry", Set.of())` 的用例；`DictionaryRepositoryAdapterIT` 里「已软删除不应查到」的两个用例改用 `UPDATE … SET deleted_at` 后仍断言查不到。
- [ ] **Step 2:** `./gradlew :patra-api:patra-registry:patra-registry-infra:integrationTest --tests "*RegistrySchemaStandardIT"`。Expected: FAIL，违规清单含 §1（`sys_dict_item_alias` 缺审计列）、§2（6 条 `UNIQUE` 约束）、§6（`chk_` 前缀、双下划线、未命名检查约束）。
- [ ] **Step 3:** 重写迁移脚本：函数脚本照设计 3.1；4 张表照 `database.md`，`type_id` → `dict_type_id`、`item_id` → `dict_item_id`，6 条唯一约束改唯一索引，两个生成列 `default_key`、`canonical_key` 保留，中文注释。种子移到 `db/seed/` 的 `R__` 脚本：`id` = 原值减 `800000000000000000`（字典类型、字典项、别名）或减 `900000000000000000`（参考标准），即 1、2、1001～1249、3001～3050 等；`ON CONFLICT (id) DO UPDATE` 更新全部业务列；不写审计列和 `version`。
- [ ] **Step 4:** 两个软删实体改继承 `BaseJpaEntity`，加普通字段 `deletedAt`；`SysDictTypeDao` 2 个、`SysDictItemDao` 6 个派生查询改名带 `AndDeletedAtIsNull`，继承来的 `findById` 等若有调用也换成带条件的方法（Review Focus 3）；实体字段 `typeId` → `dictTypeId`、`itemId` → `dictItemId`，Dao 方法名与 JPQL 随之改。`SoftDeletableJpaEntity` 先不删，catalog 还在用（Task 11 删）。
- [ ] **Step 5:** 跑 `./gradlew :patra-api:patra-registry:patra-registry-infra:integrationTest :patra-api:patra-registry:patra-registry-boot:test`。Expected: 全部 PASS（含 `DictionarySeedIT`：它用 `@TestPropertySource` 把 `locations` 设成两个目录）。
- [ ] **Step 6:** `grep -rn "flyway" patra-infra/nacos/dev/patra-registry.yaml`，有 `locations` 覆盖就同步加上 `db/seed`。
- [ ] **Step 7:** 提交 `refactor(registry): 库表按规范重建，种子改为可重复脚本 (PAP-202)`。

### Task 8: identity 表结构重建（PAP-202）

**Files:**
- Create: `patra-api/patra-identity/patra-identity-infra/src/main/resources/db/tables.yml`、`db/migration/V{ts}__create_set_audit_times.sql`，以及按子域分目录的建表脚本（`account/`、`session/`、`events/`）
- Delete: 现有 4 个迁移脚本
- Modify: `IdentitySchemaConstraintsIT`、`ProfileSchemaConstraintsIT`、`UserReadAdapterIT`
- Test: `IdentitySchemaStandardIT`（新）

**Interfaces:**
- 9 张 `idn_` 表全部是主表（设计 4.1）；`event_publication`、`flyway_schema_history` 是框架表。
- 被代码按名字匹配的四个唯一索引名不变：`uk_idn_user_email`、`uk_idn_user_username`、`uk_idn_email_token_active`、`uk_idn_email_token_hash`（`UserRepositoryAdapter`、`EmailTokenRepositoryAdapter` 用 `getConstraintName()` 比对；唯一索引冲突时 PostgreSQL 报的约束名就是索引名）。

- [ ] **Step 1: 写失败测试**：`tables.yml`；`IdentitySchemaStandardIT.should_meet_database_standard`。
- [ ] **Step 2:** 跑 `./gradlew :patra-api:patra-identity:patra-identity-infra:integrationTest --tests "*IdentitySchemaStandardIT"`。Expected: FAIL，含 §2 的 8 条外键与 10 条 `UNIQUE` 约束、§3 的 `*_by_name` 与 `ip_address bytea`、§6 的英文注释。
- [ ] **Step 3:** 重写迁移脚本：删 8 条外键；10 条唯一约束改唯一索引（上面四个名字保留，其余按用途短语命名）；`set_updated_at()` 换成 `set_audit_times()`；两个 `COLLATE "C"` 前缀索引和 `event_publication` 原样保留；`idn_user.deleted_at` 与 `ck_idn_user_deleted_at` 保留（它是账号状态的一部分）；注释改中文。
- [ ] **Step 4:** 改测试：`IdentitySchemaConstraintsIT`、`ProfileSchemaConstraintsIT` 删掉 6 个断言外键名的用例，唯一约束名的断言改成新的索引名；`UserReadAdapterIT` 回填 `created_at` 时按本阶段开头的写法关触发器。
- [ ] **Step 5:** 跑 `./gradlew :patra-api:patra-identity:patra-identity-infra:integrationTest :patra-api:patra-identity:patra-identity-boot:integrationTest`。Expected: 全部 PASS。
- [ ] **Step 6:** 提交 `refactor(identity): 表结构按规范重建，删除外键 (PAP-202)`。

### Task 9: identity 不经实体的写入与备注保留（PAP-202）

**Files:**
- Modify: `EmailTokenDao`、`ExternalIdentityDao`、`UserPasswordCredentialDao`、`UserProfileLinkDao`、`UserResearchInterestDao`、`UserLoginRecordDao`；`EmailTokenRepositoryAdapter`、`ExternalIdentityRepositoryAdapter`；`IdentityArchitectureTest`
- Test: `EmailTokenRepositoryAdapterIT`、`ExternalIdentityRepositoryAdapterIT`、`UserRepositoryAdapterIT`、`UserProfileRepositoryAdapterIT`

**Interfaces:**
- 端口签名不变。操作人按 Global Constraints 取 `AuditorAware<Long>`：UPDATE 写 `updated_by`，INSERT 写 `created_by`、`updated_by`。

- [ ] **Step 1: 写失败测试**：
  - `IdentityArchitectureTest` 加汇总 `PersistenceTechnologyRules.jpaService("dev.patra.identity", Set.of())` 的用例（现在因 12 个 `@Modifying` 缺属性、`EmailTokenRepositoryAdapter` 用 `JdbcClient` 写库而失败）
  - `UserRepositoryAdapterIT.should_keep_record_remarks_when_user_saved`、`UserProfileRepositoryAdapterIT.should_keep_record_remarks_when_nickname_changed`：JDBC 写 `record_remarks = '{"note":"人工核对"}'`，经仓储保存一次修改，备注还在（Review Focus 5）
  - `EmailTokenRepositoryAdapterIT.should_not_consume_token_preloaded_before_another_consumer`：事务 A 先按哈希读出令牌，事务 B 消费并提交，A 再消费返回空
  - `EmailTokenRepositoryAdapterIT.should_record_actor_when_revoking`：带当前操作人作废令牌后 `updated_by` 为该用户；`ExternalIdentityRepositoryAdapterIT.should_record_actor_when_inserting`：`created_by` 有值（测试上下文照 `JpaAuditingWithProviderIT` 提供一个 `CurrentAuditorProvider`）
  - `UserProfileRepositoryAdapterIT.should_save_profile_twice_with_replaced_links`：同一事务里保存资料（整组删链接和研究兴趣再插）两次，第二次的改动都落库（Review Focus 2）
- [ ] **Step 2:** 跑 `./gradlew :patra-api:patra-identity:patra-identity-boot:test --tests "*IdentityArchitectureTest" :patra-api:patra-identity:patra-identity-infra:integrationTest`。Expected: 架构用例与新增仓储用例 FAIL（备注用例应已因 Task 5 通过，若失败说明基类改动没生效，停下排查）。
- [ ] **Step 3:** 12 个 `@Modifying` 补 `flushAutomatically = true, clearAutomatically = true`；作废令牌的 3 条 JPQL `UPDATE` 加 `SET t.updatedBy = :actorId`；`insertIfAbsent` 写 `created_by`、`updated_by`。删掉调用方现在多余的手动 `flush()`（`UserPasswordCredentialRepositoryAdapter`、`UserProfileRepositoryAdapter`）。
- [ ] **Step 4:** 令牌消费改成经实体：按哈希和用途查出实体，`EntityManager.refresh(entity, LockModeType.PESSIMISTIC_WRITE)` 加锁重读（同一事务里之前查过的实例在一级缓存里，普通加锁查询会返回旧状态），在锁内判断未消费、未作废、未过期，调用聚合的消费方法后保存；不满足返回空。删掉 `CONSUME_SQL` 和 `JdbcTemplate` 字段，更新类注释。
- [ ] **Step 5:** 再跑 Step 2 的命令和 `./gradlew :patra-api:patra-identity:patra-identity-boot:integrationTest`。Expected: 全部 PASS。
- [ ] **Step 6:** 提交 `refactor(identity): 不经实体的写入改成合规写法 (PAP-202)`。

### Task 10: catalog 迁移脚本重写与表结构对齐（PAP-203）

**Files:**
- Create: `patra-catalog-infra/src/main/resources/db/tables.yml`、`db/migration/V{ts}__create_set_audit_times.sql`（同时 `CREATE EXTENSION IF NOT EXISTS pg_trgm`），`publication/`、`venue/`、`author/`、`organization/`、`mesh/`、`fulltext/` 下按子域重写的建表脚本；`CatalogSchemaStandardIT`
- Delete: `V1`～`V14` 共 14 个脚本
- Modify: `R__cat_organization_alias_seed.sql`（保留开头按来源删除的语句，插入写死 `id`，按行序从 1 起，`ON CONFLICT (id) DO UPDATE`）
- Modify: 50 个实体删掉 `@Table(indexes / uniqueConstraints)` 元数据；列改名涉及的实体字段与 Dao：`OrganizationNameEntity`、`OrganizationExternalIdEntity`、`OrganizationRelationEntity`、`OrganizationLocationEntity`、`PublicationAuthorAffiliationEntity`，4 个机构子表 Dao 的 `deleteAllByOrgId(In)`、`PublicationAuthorAffiliationDao` 的 `deleteAllByPubAuthorId(In)`、`OrganizationRelationDao` 的 JPQL；原生 SQL `OrganizationSuggestSql`、`portal/persistence/PublicationDetailReadAdapter`
- Modify: 主键改成 `id` 的三张表的写入：`PublicationSyncRepositoryAdapter` 的原文缓存 upsert 与墓碑插入、`PublicationFullTextRepositoryAdapter` 的正文 upsert，挪进各自的 Dao（新建 `PublicationSourceDao`、`PublicationFullTextBodyDao`，都是带 `@Modifying(flushAutomatically = true, clearAutomatically = true)` 的原生 `@Query`）
- Test: `CatalogSchemaStandardIT`（新）；`VenueRepositoryAdapterIT`、`PublicationSourceGuardIT`、`PublicationSourceSchemaIT` 里的约束名、索引名断言；`PublicationAuthorRoundTripIT`、`AuthorOrcidDaoIT`、`PublicationDaoTopByVenueIT` 等用原生 SQL 写表的测试按新列调整；`patra-catalog-boot/src/e2eTest` 里用原生 SQL 写 `cat_*` 表的 3 个类（含 `MeshScrImportE2E`）同样调整

**Interfaces:**
- 档位：主表 11 张见设计 4.1（含 `cat_publication_fulltext`）；其余 49 张其他表，包括建表时带全套审计列、但实体从没写过它们的 14 张（设计 5.2）和 5 张没有代码读写的表。
- 迁移脚本按表所属子域分目录（`cat_venue_instance`、`cat_publication_author*`、`cat_publication_investigator`、`cat_publication_personal_name_subject`、`cat_publication_mesh_*`、`cat_publication_suppl_mesh`、`cat_keyword`、`cat_investigator`、`cat_pubmed_file`、`cat_publication_source` 都归 publication）。
- 三张改主键的表：原主键改唯一索引 `uk_cat_publication_source_pmid`、`uk_cat_publication_fulltext_body_publication`、机构别名去掉 `IDENTITY`；`cat_publication_source.raw_xml` 保留 `COMPRESSION lz4`；生成列 `language_base` 保留。
- 墓碑插入原来按条件拼接候选子查询，改成两个固定的候选查询（Dao 方法），应用拿到候选 PMID 后带雪花 `id` 用 `unnest` 批量插入。正文 upsert 不再写 `updated_at`。
- 代码里若按约束名比对（`grep -rn "getConstraintName\|\"uk_" patra-api/patra-catalog/*/src/main`），新索引名与比对处一起改。

- [ ] **Step 1: 写失败测试**：`tables.yml`；`CatalogSchemaStandardIT.should_meet_database_standard`。
- [ ] **Step 2:** `./gradlew :patra-api:patra-catalog:patra-catalog-infra:integrationTest --tests "*CatalogSchemaStandardIT"`。Expected: FAIL，§2 的 45 条 `UNIQUE` 约束与 3 张主键不是 `id` 的表、§3 的 `*_by_name` 与 `bytea`、§6 的 226 个不合规名字和缺失注释都在清单里。
- [ ] **Step 3:** 按子域重写建表脚本（每张表、每个业务列中文注释；约束和索引按用途短语重命名，最长表名 35 字节，短语控制在 23 字节内）。
- [ ] **Step 4:** 改实体元数据、列改名、三张表的写入（见 Files 与 Interfaces）；改测试里的名字和原生 SQL。
- [ ] **Step 5:** `./gradlew :patra-api:patra-catalog:patra-catalog-infra:integrationTest :patra-api:patra-catalog:patra-catalog-adapter:integrationTest :patra-api:patra-catalog:patra-catalog-app:integrationTest`。Expected: 全部 PASS，结构检查通过。
- [ ] **Step 6:** 提交 `refactor(catalog): 迁移脚本按规范重写，三张例外表改用雪花主键 (PAP-203)`。

### Task 11: catalog 软删除改显式列（PAP-203）

**Files:**
- Modify: `PublicationEntity`、`VenueEntity`、`AuthorEntity`、`OrganizationEntity` 改继承 `BaseJpaEntity`，加普通字段 `deletedAt`
- Delete: `patra-tool/patra-tool-jpa/src/main/java/dev/patra/tool/jpa/entity/SoftDeletableJpaEntity.java`、`src/test/.../SoftDeletableJpaEntityTest.java`（最后的使用方）
- Modify: `PublicationDao`（17）、`AuthorDao`（14）、`VenueDao`（9）、`OrganizationDao`（9）、`PublicationResolveDao`、`VenueResolveDao`、`VenueIdentifierDao`、`AuthorOrcidDao`；`FullTextCandidateReadAdapter`、`VenueEnrichmentReadAdapter` 的 JPQL；`OrganizationRepositoryAdapter`、`PublicationRepositoryAdapter`、`AuthorRepositoryAdapter`、`VenueRepositoryAdapter` 里继承来的 `findById` / `findAllById` / `existsById` / `count` 与 `em.find`
- Test: 4 个软删实体的仓储 IT、`PublicationResolveReadAdapterIT`、`VenueResolveReadAdapterIT`、`VenueDetailReadAdapterIT`、`PublicationDetailReadAdapterIT`、`VenueBrowseReadAdapterIT`、`AuthorOrcidDaoIT`

**Interfaces:**
- 原来靠自动过滤的查询补「未删除」条件（派生方法带 `AndDeletedAtIsNull`，JPQL 写 `e.deletedAt IS NULL`）；原来为读已删行而写成原生 SQL 的（`findOccupiedPmids`、`findOccupiedDois`、两个 Resolve Dao 各 2 条、`VenueIdentifierDao` 1 条）改成普通 JPQL，不加条件。
- 继承方法与 `em.find` 换成带条件的 Dao 方法，语义保持「已删视为不存在」。同步任务里本来就要读已删行的两处（`PublicationSyncRepositoryAdapter` 的同步目标与 DOI 查询）在 Task 12 挪进 Dao，不加条件。
- 删除 app 层没有调用方、去掉注解后会从软删变成物理删除的方法，连同端口方法：`PublicationDao.deleteByVenueInstanceId`、`deleteByVenueId` 两条 JPQL 批量删除及其适配器方法、`PublicationRepositoryAdapter` 的 `deleteById` 路径、`AuthorRepositoryAdapter` 的删除方法；没有调用方的后台列表查询 `findPublicationPage` 一并删。
- 顺带发现、本版不改：`VenueInstanceDao` 数文献数时不过滤已删（原来就如此），写进 PR 描述。

- [ ] **Step 1: 写失败测试**（Review Focus 3）：`PublicationRepositoryAdapterIT.should_not_find_soft_deleted_publication`、`VenueRepositoryAdapterIT.should_not_find_soft_deleted_venue`、`AuthorRepositoryAdapterIT.should_not_find_soft_deleted_author`、`OrganizationRepositoryAdapterIT.should_not_find_soft_deleted_organization`——都用 `UPDATE … SET deleted_at = now()` 标删，再经仓储查；`PublicationResolveReadAdapterIT.should_report_pmid_occupied_by_deleted_publication`（PMID 全表唯一，已删的也算占用）。两处 `em.remove` 的测试改成标删后保存。
- [ ] **Step 2:** 去掉 4 个实体的 `SoftDeletableJpaEntity` 继承后跑 `./gradlew :patra-api:patra-catalog:patra-catalog-infra:integrationTest`。Expected: 新用例 FAIL（已删行被查出）。
- [ ] **Step 3:** 按 Interfaces 补条件、改写原生查询、替换继承方法、删掉列出的方法。
- [ ] **Step 4:** 再跑 Step 2 的命令和 `:patra-api:patra-catalog:patra-catalog-adapter:integrationTest`、`:patra-tool:patra-tool-jpa:check`。Expected: 全部 PASS。
- [ ] **Step 5:** 提交 `refactor(catalog): 软删除改成显式 deleted_at 列 (PAP-203)`。

### Task 12: catalog 不经实体的写入与写侧规则（PAP-203）

**Files:**
- Modify: 35 个带 `@Query` 的 `@Modifying` 补两个属性；11 个挂 `@Modifying` 的派生删除改成带两个属性的 JPQL 批量删除（`PublicationMetadataDao`、`PublicationAlternativeAbstractDao`、`PublicationIdentifierDao`、`PublicationOaLocationDao`、`PublicationAbstractDao`、`PublicationDateDao`、`VenueIndexingHistoryDao`、`VenueMeshDao`、`VenueRelationDao`、`VenueIdentifierDao`、`VenuePublicationStatsDao`）
- Modify: `PublicationSyncRepositoryAdapter` 余下的 4 条 `EntityManager` 写入（标删、复活、`updatePublicationColumns`、`updateMetadataColumns`）与 3 处原生读（`:106`、`:136`、`:262`）挪进 `PublicationDao` / `PublicationSyncDao`（新）；`VenueInstanceRepositoryAdapter` 的原生插入挪进 `VenueInstanceDao`；`PublicationFullTextRepositoryAdapter.setHasFullText` 挪进 `PublicationDao`
- Modify: `CatalogArchitectureTest`
- Test: `PublicationSyncRepositoryAdapterIT`、`VenueInstanceRepositoryAdapterIT`、`PublicationRepositoryAdapterIT`、`PublicationAuthorRoundTripIT`

**Interfaces:**
- `updatePublicationColumns` 原来按 `syncedAt != null` 决定改不改 `lastSyncedAt`，改成固定语句 `last_synced_at = COALESCE(:syncedAt, last_synced_at)`；手写的 `version = version + 1` 保留。
- 写主表（`cat_publication`、`cat_venue`、`cat_venue_instance`）的批量语句按 Global Constraints 写 `updated_by`（插入再写 `created_by`）；catalog 的写入都来自任务，取到的操作人为空。
- `PersistenceTechnologyRules.jpaService("dev.patra.catalog", Set.of("dev.patra.catalog.infra.kernel.lock.PgAdvisoryRunLockAdapter"))`：它只执行 advisory lock，不改数据。

- [ ] **Step 1: 写失败测试**：`CatalogArchitectureTest` 加汇总这组规则的用例；`PublicationRepositoryAdapterIT.should_save_publication_twice_with_replaced_children`（同一事务里两次保存、子表整组替换，第二次的改动都落库，Review Focus 2）；`PublicationRepositoryAdapterIT.should_not_increase_statements_when_saving_batch`：改动前先用 Hibernate 统计量出保存 50 篇文献准备的语句数 B，断言写成 `≤ B`（补 `clearAutomatically` 后若引出重复查询，导入会变慢，要在 mini 停机重导之前发现）。
- [ ] **Step 2:** `./gradlew :patra-api:patra-catalog:patra-catalog-boot:test --tests "*CatalogArchitectureTest"`。Expected: FAIL，列出缺属性的 `@Modifying`、没有 `@Query` 的 11 个方法、适配器里的 `createNativeQuery` 与 `executeUpdate`。
- [ ] **Step 3:** 按 Files 与 Interfaces 改。补了 `clearAutomatically` 之后，适配器在批量语句之后还要用的托管实体要重新查或在语句之前保存。
- [ ] **Step 4:** 再跑 Step 2 的命令和 `:patra-api:patra-catalog:patra-catalog-infra:integrationTest :patra-api:patra-catalog:patra-catalog-app:integrationTest`。Expected: 全部 PASS。
- [ ] **Step 5:** 提交 `refactor(catalog): 不经实体的写入改成合规写法 (PAP-203)`。

### Task 13: catalog 全文升为主表（PAP-203）

**Files:** Modify `PublicationFullTextEntity`（改继承 `BaseJpaEntity`）、`PublicationFullTextRepositoryAdapter` 与它的 Mapper；Test `PublicationFullTextRepositoryAdapterIT`

**Interfaces:**
- 升为主表后实体带 `@Version`，`version` 为空时 Spring Data 判为新行。保存路径若是 new 出实体再保存，改成先查后改，或把版本号经聚合带回。

- [ ] **Step 1: 写失败测试**：`should_update_fulltext_status_twice`（先插入，再两次更新抓取与解析状态，`version` 递增、行数仍为 1）；`should_reject_stale_update`（拿旧版本保存抛乐观锁异常）。
- [ ] **Step 2:** `./gradlew :patra-api:patra-catalog:patra-catalog-infra:integrationTest --tests "*PublicationFullTextRepositoryAdapterIT"`。Expected: FAIL。
- [ ] **Step 3:** 改实体与保存路径。查抓取、解析两条流水线会不会同时改同一行：会的话在 IT 里复现，按 `transaction.md` 第 3 节锁内重读；不会的话在实体注释里写明为什么。
- [ ] **Step 4:** `./gradlew :patra-api:patra-catalog:patra-catalog-domain:test :patra-api:patra-catalog:patra-catalog-app:test :patra-api:patra-catalog:patra-catalog-app:integrationTest :patra-api:patra-catalog:patra-catalog-infra:test :patra-api:patra-catalog:patra-catalog-infra:integrationTest :patra-api:patra-catalog:patra-catalog-adapter:integrationTest :patra-api:patra-catalog:patra-catalog-boot:test :patra-api:patra-catalog:patra-catalog-boot:e2eTest`。Expected: 全部 PASS。
- [ ] **Step 5:** 提交 `refactor(catalog): 文献全文升为主表 (PAP-203)`。

### Task 14: interaction 公共部分、对象登记与有帮助（PAP-204）

interaction 按子域分三段做。结构检查在 Task 16 结束时才全绿；Task 14、15 结束时，它的违规清单里不应再有已完成子域的表。

**Files:**
- Create: `patra-interaction-infra/src/main/resources/db/tables.yml`（16 张表一次登记全）、`db/migration/V{ts}__create_set_audit_times.sql`、`subject/`、`reaction/` 下重写的脚本；`InteractionSchemaStandardIT`
- Delete: `subject/` 5 个、`reaction/` 2 个旧脚本，`notification/` 目录整个删掉（它只为保留已删 schema 的 Flyway 历史）
- Modify: `infra/subject/persistence/` 的 `SubjectRepositoryAdapter`、`PublishQuotaRepositoryAdapter`、`SubjectReadAdapter`、`SubjectRows`；`infra/reaction/persistence/` 的 `HelpfulRepositoryAdapter`、`HelpfulReadAdapter`；跨子域读这些表的 `PostReadAdapter`、`CommentRows`、`DiscussionReadAdapter`（只改涉及 `itr_subject`、`itr_helpful*` 的部分）
- Modify: `SubjectRepository.updateLocal`、`SubjectGateway.updateLocal` 加 `Long actorUserId`；`SubjectGatewayImpl`、`EditExperienceHandler`、`DeleteExperienceHandler` 及其单测
- Modify: `bIT/support/InteractionFlowITSupport.resetWorld`：清空 `public` 里 `itr_` 开头的表，同时保留清空非 `public` schema 的分支（另两段的表还在旧 schema，Task 16 删这个分支）
- Test: `InteractionMigrationIT`（删 `should_create_one_schema_per_subdomain`、`should_drop_notification_schema` 和断言触发器名的用例，其余改新表名）、subject 与 reaction 的仓储和读适配器 IT 及支持类、引用这些表的 boot 流程测试

**Interfaces:**
- 档位与表名见设计 4.2：`itr_subject` 主表（主键 `subject_id` 改 `id`，其他表引用它的 `subject_id` 不改）；`itr_publish_quota`、`itr_helpful`、`itr_helpful_target_stats`、`itr_helpful_user_stats` 其他表。
- 每条 `ON CONFLICT` 的冲突目标要有列完全相同的唯一索引。
- `itr_helpful.created_at` 改由触发器填，`HelpfulRepositoryAdapter` 不再写它；集成事件里的 `markedAt` 本来就取 handler 的时钟。
- 操作人：从 catalog 解析来的行、释放占用、对账写入为空；本地发布的帖子对象 `created_by` = 作者；`updateLocal` 用传入的 `actorUserId`。

- [ ] **Step 1: 写失败测试**：`tables.yml`；`InteractionSchemaStandardIT.should_meet_database_standard`；`SubjectRepositoryAdapterIT.should_record_author_when_inserting_local_subject` 与 `should_record_actor_when_updating_local_subject`；`HelpfulRepositoryAdapterIT` 里断言 `created_at` 等于传入时刻的用例改成只断言行存在。
- [ ] **Step 2:** `./gradlew :patra-api:patra-interaction:patra-interaction-infra:integrationTest --tests "*InteractionSchemaStandardIT"`。Expected: FAIL，清单含全部 16 张表。
- [ ] **Step 3:** 重写 `subject/`、`reaction/` 脚本；改上面的适配器、端口与测试。
- [ ] **Step 4:** `./gradlew :patra-api:patra-interaction:patra-interaction-infra:integrationTest :patra-api:patra-interaction:patra-interaction-app:test :patra-api:patra-interaction:patra-interaction-boot:integrationTest`。Expected: 除 `InteractionSchemaStandardIT` 外全部 PASS；它的清单里没有 `itr_subject`、`itr_publish_quota`、`itr_helpful*`。
- [ ] **Step 5:** 提交 `refactor(interaction): 对象登记与有帮助的库表按规范重建 (PAP-204)`。

### Task 15: interaction 收藏（PAP-204）

**Files:**
- Modify: `collection/` 下 3 个脚本重写；`infra/collection/persistence/` 的 `CollectionRepositoryAdapter`、`LibraryRepositoryAdapter`、`LibraryReadAdapter`、`CollectionRows`；`SubjectStatsReadAdapter` 里收藏统计的一半
- Delete: `CollectionStatsMigrationIT`（重写后的脚本没有回填）
- Test: `CollectionRepositoryAdapterIT`、`LibraryReadAdapterIT`、`LibraryRepositoryAdapterIT`、`CollectionPersistenceITSupport`、`SubjectStatsReadAdapterIT`、`InteractionMigrationIT` 的分区用例、`bIT` 的 `CollectionFlowIT`、`StatsRecountFlowIT`、`DeletedUserCleanupFlowIT`

**Interfaces:**
- `itr_collection` 主表，加 `content_changed_at`：建夹、改名、改可见性、增减文献、从文献库删文献时由应用写；列表按 `(user_id, content_changed_at DESC)` 排序；接口的 `updatedAt` 取它，`createdAt` 取审计列 `created_at`。
- `itr_collection_item`、`itr_library_item` 按 `user_id` 哈希分 16 区，主键 `(user_id, id)`，唯一索引带 `user_id`；`itr_library_subject_stats` 其他表。
- 3 条 `INSERT … SELECT … unnest` 多传一个 `CAST(:ids AS bigint[])`，每行一个雪花 ID。
- 操作人就是收藏夹主人（方法签名里已有的 `userId`）。
- 建夹时 `content_changed_at` 取事务前的时钟，可能比数据库在事务开始时填的 `created_at` 早几毫秒。原脚本不给这张表挂触发器，就是怕这种倒挂；但门户只按天显示 `updatedAt`、不显示也不比较 `createdAt`，接口字段不变，所以接受。`CollectionRepositoryAdapterIT` 里「更新时间不早于创建时间」的断言改成「`content_changed_at` 等于注入的时刻」。

- [ ] **Step 1: 写失败测试**：`LibraryReadAdapterIT.should_order_collections_by_content_changed_at_not_updated_at`（直接 `UPDATE` 一个非内容列刷新 `updated_at`，排序和「更新于」不变，Review Focus 4）；`CollectionRepositoryAdapterIT.should_record_owner_as_actor`。
- [ ] **Step 2:** `./gradlew :patra-api:patra-interaction:patra-interaction-infra:integrationTest --tests "*LibraryReadAdapterIT" --tests "*CollectionRepositoryAdapterIT"`。Expected: FAIL。
- [ ] **Step 3:** 重写脚本，改适配器与测试。
- [ ] **Step 4:** 跑 Task 14 Step 4 的命令。Expected: 除结构检查外全部 PASS；结构检查清单里只剩讨论和投稿经验的表。
- [ ] **Step 5:** 提交 `refactor(interaction): 收藏的库表按规范重建 (PAP-204)`。

### Task 16: interaction 讨论、投稿经验与写侧规则（PAP-204）

**Files:**
- Modify: `discussion/` 4 个、`post/` 2 个脚本重写；`infra/discussion/persistence/` 的 `CommentRepositoryAdapter`、`DiscussionReadAdapter`、`CommentRows`；`infra/post/persistence/` 的 `PostRepositoryAdapter`、`PostReadAdapter`、`PostRows`；`SubjectStatsReadAdapter` 里讨论统计的一半
- Modify: `CommentRepository.markDeleted`、`PostRepository.markDeleted` 加 `Long actorUserId`；`DeleteCommentHandler`、`DeleteExperienceHandler` 及其单测
- Modify: `InteractionArchitectureTest`；`InteractionFlowITSupport.resetWorld` 删掉清非 `public` schema 的分支
- Delete: `DiscussionStatsMigrationIT`；`CommentRepositoryAdapterIT` 里靠 `fk_comment_parent` 证明「有回复的评论删不掉」的用例。没有外键后这条由应用保证：发回复与删评论都先 `lockThread(rootId)`，删除在锁内查 `hasReplies`，发回复在锁内重读父评论并 `requireRepliable`，两者串行，不会留下孤儿回复。保留或补一个流程级用例：先发回复再删父评论，父评论变成占位而不是被删掉
- Test: `CommentRepositoryAdapterIT`、`DiscussionReadAdapterIT`、`PostRepositoryAdapterIT`、`PostReadAdapterIT`、`DiscussionMigrationIT`、`PostMigrationIT`（删外键、schema、触发器断言）、`InteractionMigrationIT`（写死的版本号列表改成新的）、`bIT` 的讨论与投稿经验流程测试、`InteractionEventsFlowIT`

**Interfaces:**
- `itr_comment`、`itr_post` 主表，各加 `published_at`，接管原 `created_at` 的全部用途（排序与游标、编辑宽限期、事件里的发布时间、楼的最后回复时间、页面「发布于」）；Java 里的 `createdAt` 名字不变，值改从 `published_at` 取。
- 评论列改名：`author_id` → `author_user_id`、`root_id` → `root_comment_id`（评论表和 `itr_comment_thread_stats`）、`parent_id` → `parent_comment_id`、`version` → `body_version`。
- 评论修订改成显式删除：`CommentRepositoryAdapter.delete` 在同一事务里先删 `itr_comment_revision` 再删评论。
- 操作人：发布写作者，编辑写作者（已校验），`markDeleted` 用传入的 `actorUserId`。
- `PersistenceTechnologyRules.sqlService("dev.patra.interaction")`。

- [ ] **Step 1: 写失败测试**：`DiscussionReadAdapterIT.should_order_roots_by_published_at_not_updated_at`（Review Focus 4）；`DiscussionRevisionFlowIT` 与 `ExperienceFlowIT` 里回拨时间改成回拨 `published_at`，并加 `should_keep_edit_grace_by_published_at`：只刷新 `updated_at` 不影响能否免修订编辑；`CommentRepositoryAdapterIT.should_delete_revisions_with_comment`（保留原断言，去掉对级联的依赖）与 `should_record_actor_when_marking_deleted`；`InteractionArchitectureTest` 加汇总 `sqlService` 的用例。
- [ ] **Step 2:** `./gradlew :patra-api:patra-interaction:patra-interaction-infra:integrationTest --tests "*DiscussionReadAdapterIT" --tests "*CommentRepositoryAdapterIT"`。Expected: FAIL。
- [ ] **Step 3:** 重写脚本，改适配器、端口、handler 与测试。
- [ ] **Step 4:** `./gradlew :patra-api:patra-interaction:patra-interaction-infra:integrationTest :patra-api:patra-interaction:patra-interaction-app:test :patra-api:patra-interaction:patra-interaction-boot:test :patra-api:patra-interaction:patra-interaction-boot:integrationTest`。Expected: 全部 PASS，含 `InteractionSchemaStandardIT`。
- [ ] **Step 5:** 提交 `refactor(interaction): 讨论与投稿经验的库表按规范重建 (PAP-204)`。

### Task 17: notification 发信幂等键与偏好行预建（PAP-205）

库表重建的两个前置改动，先单独做（设计 5.4）：

- 提醒投递的 ID 要从 UUID 改成雪花 ID，而 `OutgoingMail.deliveryId` 同时承载安全邮件的上游事件 UUID，先把类型改成字符串幂等键。
- `EmailSubscriptionReadAdapter` 在读端口里插入默认偏好行，违反 §8。mail 子域调不到 preference 的仓储，改成 preference 订阅 inbox 的 `NotificationsCreatedEvent`，在写提醒的同一事务里为收件人预建默认偏好行；读端口只读。

（下文路径前缀 `patra-api/patra-notification/patra-notification-{层}/src/main/java/dev/patra/notification/`）

**Files:**
- Modify: `domain/mail/model/vo/OutgoingMail.java`、`SecurityMail.java`、`domain/mail/exception/MailDeliveryFailedException.java`、`app/mail/usecase/send/SendPendingDeliveriesHandler.java`、`infra/mail/delivery/SmtpMailDeliveryAdapter.java`
- Create: `app/preference/listener/EmailPreferenceCreationListener.java`
- Modify: `infra/preference/persistence/EmailSubscriptionReadAdapter.java`（删插入、`Clock`、`SecureRandom`）、`domain/preference/port/read/EmailSubscriptionReadPort.java`（契约注释）、`NotificationArchitectureTest`（加 `.allowingSubscription("preference", "inbox")`）
- Test: `SmtpMailDeliveryAdapterTest`、`SendPendingDeliveriesHandlerTest`、`EmailPreferenceCreationListenerTest`（新）、`EmailSubscriptionReadAdapterIT`、`bIT/mail/ReminderMailFlowIT`

**Interfaces:**
- `OutgoingMail.idempotencyKey(): String` 取代 `deliveryId(): UUID`；安全邮件传 `eventId.toString()`，提醒传投递 ID 的十进制串；`Resend-Idempotency-Key` 与 `Message-ID: <{idempotencyKey}@{发件域名}>` 都用它。
- `EmailPreferenceCreationListener`：同步 `@EventListener`，对事件里每个不同的收件人调 `EmailPreferenceRepository.insertIfAbsent`，默认偏好用 `ChangeEmailModeHandler` 首次插入时用的同一个领域工厂。会发邮件的只有回复、提及两类，都经 `NotificationWriter` 发这个事件。
- `EmailSubscriptionReadPort.subscriptionOf` 读不到行视为不变量被破坏，抛 `IllegalStateException`，走现有的单封失败路径。

- [ ] **Step 1: 写失败测试**：`SmtpMailDeliveryAdapterTest` 的幂等键与 Message-ID 断言改用字符串键；`EmailPreferenceCreationListenerTest.should_insert_default_preference_once_per_recipient`；`EmailSubscriptionReadAdapterIT.should_not_insert_when_reading` 与 `should_fail_when_preference_row_missing`。
- [ ] **Step 2:** `./gradlew :patra-api:patra-notification:patra-notification-infra:test :patra-api:patra-notification:patra-notification-app:test :patra-api:patra-notification:patra-notification-infra:integrationTest --tests "*EmailSubscriptionReadAdapterIT"`。Expected: 新用例 FAIL。
- [ ] **Step 3:** 按接口改 `OutgoingMail` 一路；加监听器；读适配器只读。
- [ ] **Step 4:** 再跑 Step 2 的命令，加 `:patra-api:patra-notification:patra-notification-boot:test --tests "*NotificationArchitectureTest"` 与 `:patra-api:patra-notification:patra-notification-boot:integrationTest --tests "*ReminderMailFlowIT"`。Expected: PASS，提醒邮件的退订链接照常带令牌。
- [ ] **Step 5:** 提交 `refactor(notification): 发信幂等键改用字符串，偏好行随提醒预建 (PAP-205)`。

### Task 18: notification 表结构与 SQL 重建（PAP-205）

**Files:**
- Create: `patra-notification-infra/src/main/resources/db/tables.yml`、`db/migration/V{ts}__create_set_audit_times.sql`，`inbox/`、`kernel/`、`mail/`、`preference/` 下重写的建表脚本；`NotificationSchemaStandardIT`
- Delete: 现有 11 个迁移脚本
- Modify: `infra/{inbox,kernel,mail,preference}/persistence/` 下 11 个适配器；`ReminderDelivery`、`MailDelivery` 聚合；`ReminderDeliveryRepository`（加 `nextId()`）；`FlushDueRemindersHandler`、`ScheduleDailyDigestHandler`；`EmailPreferenceRepository` 删只为写审计时间的 `Instant now` 参数，`ChangeEmailModeHandler`、`UnsubscribeHandler`、`EmailPreferenceCreationListener` 随之改；`NotificationMigrationIT` 删 `should_only_have_public_and_subdomain_schemas`
- Test: `iIT/` 下 11 个仓储 IT 与两个支持类；`bIT/` 下 13 个流程测试，其中 `support/NotificationFlowITSupport.resetWorld` 改成清空 `public` 里 `ntf_` 开头的表（父表 `TRUNCATE` 带上分区），`InboxEventFlowIT` 临时触发器的表名随之改

**Interfaces:**
- 表名、档位、补 `id` 见设计 4.2；主表 `ntf_notification`（按 `recipient_user_id` 哈希分区，主键 `(recipient_user_id, id)`）、`ntf_reminder_delivery`、`ntf_email_preference`。分区表上的唯一索引带 `recipient_user_id`；10 处 `ON CONFLICT` 的冲突目标都要有对应的唯一索引。
- `ntf_notification.notified_at` 接管原 `created_at` 的全部用途（应用写入、`RETURNING`、离站窗口、每日汇总窗口）；`ntf_comment.synced_at` 接管原 `updated_at`：插入、上游版本更新、`markDeleted` 时刷新，过期清理按它删。
- `ReminderDeliveryRepository.nextId(): long`（infra 用 `SnowflakeIdGenerator.getId()`），`ReminderDelivery.deliveryId` 改 `long`。
- `ntf_mail_delivery`：雪花 `id` 加 `event_id UUID` 唯一索引；`MailDeliveryRepository.exists(UUID eventId)` 签名不变，按 `event_id` 查。
- 「有帮助」参与人显式删除：`NotificationRepositoryAdapter` 的 4 条删除改成可写 CTE，同一条语句删 `ntf_notification` 后按 `RETURNING` 的 `(recipient_user_id, id)` 删 `ntf_notification_helpful_actor`，端口不变。
- 投递列表 `ORDER BY next_attempt_at, id`。

- [ ] **Step 1: 写失败测试**：`tables.yml`；`NotificationSchemaStandardIT.should_meet_database_standard`；`InboxReadAdapterIT` 的 `entries_carry_created_at`、`unread_between_filters_on_created_at_not_occurred_at` 改成按 `notified_at`，加 `should_filter_digest_by_notified_at_even_after_row_updated`（标已读刷新了 `updated_at`，窗口仍按 `notified_at`，Review Focus 4）；`CommentContentRepositoryAdapterIT.should_purge_by_synced_at`。
- [ ] **Step 2:** `./gradlew :patra-api:patra-notification:patra-notification-infra:integrationTest --tests "*NotificationSchemaStandardIT"`。Expected: FAIL，含 5 个 schema、外键、`UNIQUE` 约束、7 张表缺 `id`、英文注释、未命名检查约束。
- [ ] **Step 3:** 重写迁移脚本（照 `database.md` 与设计 4.2～4.4，注释中文）。
- [ ] **Step 4:** 改 11 个适配器与两个聚合、两个 handler（见 Interfaces）。
- [ ] **Step 5:** 改测试：断言审计时间等于传入时刻的用例改成断言业务时间列或删掉；`MailDeliveryRepositoryAdapterIT.should_only_have_four_columns` 删掉（结构检查接管）；`HelpfulNotificationRepositoryAdapterIT.deleting_parent_rows_cascades_to_actors` 改名 `deleting_parent_rows_removes_actors`，断言不变。
- [ ] **Step 6:** `./gradlew :patra-api:patra-notification:patra-notification-infra:integrationTest :patra-api:patra-notification:patra-notification-app:test :patra-api:patra-notification:patra-notification-boot:integrationTest`。Expected: 全部 PASS。
- [ ] **Step 7:** 提交 `refactor(notification): 库表按规范重建 (PAP-205)`。

### Task 19: notification 主表操作人与写侧规则（PAP-205）

**Files:** Modify `NotificationRepositoryAdapter`、`EmailPreferenceRepositoryAdapter`、`EmailPreferenceRepository`、`ChangeEmailModeHandler`、`UnsubscribeHandler`、`EmailPreferenceCreationListener`、`NotificationArchitectureTest`；Test `NotificationRepositoryAdapterIT`、`EmailPreferenceRepositoryAdapterIT`

**Interfaces:**
- 标已读、全部已读：`updated_by` = 收件人（已有参数）。
- `EmailPreferenceRepository.insertIfAbsent(EmailPreference preference, Long actorUserId)`、`updateModes(EmailPreference preference, Long actorUserId)`：改档位传当前用户，匿名退订和预建监听器传 `null`。
- 提醒的插入与「有帮助」更新、投递的插入与更新都来自消息消费或定时任务，两列为空。
- `PersistenceTechnologyRules.sqlService("dev.patra.notification")`。

- [ ] **Step 1: 写失败测试**：`NotificationRepositoryAdapterIT.should_record_reader_when_marking_read`；`EmailPreferenceRepositoryAdapterIT.should_record_actor_when_changing_modes`、`should_leave_actor_empty_when_unsubscribed_anonymously`；`NotificationArchitectureTest` 加汇总 `sqlService` 的用例。
- [ ] **Step 2:** `./gradlew :patra-api:patra-notification:patra-notification-infra:integrationTest --tests "*NotificationRepositoryAdapterIT" --tests "*EmailPreferenceRepositoryAdapterIT"`。Expected: 新用例 FAIL。
- [ ] **Step 3:** 实现。
- [ ] **Step 4:** `./gradlew :patra-api:patra-notification:patra-notification-domain:test :patra-api:patra-notification:patra-notification-app:test :patra-api:patra-notification:patra-notification-infra:test :patra-api:patra-notification:patra-notification-infra:integrationTest :patra-api:patra-notification:patra-notification-boot:test :patra-api:patra-notification:patra-notification-boot:integrationTest`。Expected: 全部 PASS。
- [ ] **Step 5:** 提交 `feat(notification): 主表写入记录操作人 (PAP-205)`。

### Task 20: analytics 重建（PAP-206）

**Files:**
- Create: `patra-analytics-infra/src/main/resources/db/tables.yml`、`db/migration/V{ts}__create_set_audit_times.sql`、`history/`、`insight/` 下重写的建表脚本；`AnalyticsSchemaStandardIT`
- Delete: `history/V20261102100000__create_history_schema.sql`、`insight/V20261102120000__create_insight_schema.sql`
- Modify: `infra/history/persistence/`、`infra/insight/persistence/` 下 8 个类；`RecordingSettingRepository.saveEnabled` 删只为写 `updated_at` 的 `Instant now`，`ChangeRecordingHandler` 随之改；`PurgeExpiredHistoryHandler` 的指标标签改成新表名；两个端口的类注释；`AnalyticsArchitectureTest`
- Test: `HistoryPersistenceITSupport`、`ReadingHistoryRepositoryAdapterIT`、`RecordingSettingRepositoryAdapterIT`、`InsightPersistenceITSupport`、`CoReadingSnapshotIT`、`ChangeRecordingHandlerTest`、`PurgeExpiredHistoryHandlerTest`、`bIT/history/HistoryFlowIT`、`bIT/insight/InsightFlowIT`

**Interfaces:**
- 表名见设计 4.2；`anl_recording_setting` 主表，其余 5 张其他表。ClickHouse 不动。
- 列改名只改 PG 的库和 SQL：`article_id` → `publication_id`、`related_article_id` → `related_publication_id`、墓碑 `deleted_at` → `removed_at`。
- 唯一索引：历史与墓碑 `(user_id, publication_id)`（必须以 `user_id` 开头，`findUsersAfter` 依赖）、设置 `(user_id)`、快照运行 `(kind)`、热读 `(position)` 与 `(publication_id)`、同读 `(publication_id, position)`；`anl_snapshot_run.kind` 加检查约束。
- 7 条 INSERT 补雪花 `id`：5 条 `ON CONFLICT` 各加一个参数；两条 `unnest` 批量插入多传 `CAST(:ids AS bigint[])`。
- 阅读记录设置的操作人就是该用户：插入写 `created_by`、`updated_by` = `user_id`，`DO UPDATE` 只写 `updated_by = EXCLUDED.updated_by`。

- [ ] **Step 1: 写失败测试**：`tables.yml`；`AnalyticsSchemaStandardIT.should_meet_database_standard`；`RecordingSettingRepositoryAdapterIT` 里断言 `updated_at` 等于传入时刻的两个用例改成 `should_record_user_as_actor`；`AnalyticsArchitectureTest` 加汇总 `PersistenceTechnologyRules.sqlService("dev.patra.analytics")` 的用例。
- [ ] **Step 2:** `./gradlew :patra-api:patra-analytics:patra-analytics-infra:integrationTest --tests "*AnalyticsSchemaStandardIT"`。Expected: FAIL，含 2 个 schema、6 张表缺 `id`、`UNIQUE` 约束、缺审计列、索引名与注释。
- [ ] **Step 3:** 重写迁移脚本；改 8 个类的 SQL、端口签名与测试里的表名。
- [ ] **Step 4:** `./gradlew :patra-api:patra-analytics:patra-analytics-infra:integrationTest :patra-api:patra-analytics:patra-analytics-app:test :patra-api:patra-analytics:patra-analytics-boot:test :patra-api:patra-analytics:patra-analytics-boot:integrationTest :patra-api:patra-analytics:patra-analytics-boot:e2eTest`。Expected: 全部 PASS。
- [ ] **Step 5:** 提交 `refactor(analytics): 库表按规范重建 (PAP-206)`。

### Task 21: 迁移脚本检查补拦 schema 与外键（PAP-200）

放在服务重建之后：现有脚本里有 `CREATE SCHEMA` 和外键，先加会让 `./gradlew check` 一路红到底。检查的范围从「列」扩大到「脚本」，类和任务按此改名。

**Files:**
- Rename: `build-logic/src/main/kotlin/MigrationColumnRules.kt` → `MigrationScriptRules.kt`、`MigrationColumnCheck.kt` → `MigrationScriptCheck.kt`、`build-logic/src/test/kotlin/MigrationColumnRulesTest.kt` → `MigrationScriptRulesTest.kt`
- Modify: `build-logic/src/main/kotlin/patra.hexagonal-infra.gradle.kts`（注册函数改名）、`.claude/rules/project-info.md`（`checkMigrationColumns`（R6）→ `checkMigrationScripts`）

**Interfaces:**
- `MigrationScriptRules.violations(sql: String): List<Violation>`，`data class Violation(val line: Int, val message: String)`；任务 `checkMigrationScripts`，注册函数 `registerMigrationScriptCheck()`。
- 新增两条：语句以 `create schema` 开头即违规；`create table` / `alter table` 语句里出现 `references` 词即违规。沿用现有分词器，注释、字符串、`$$` 函数体里的不算。

- [ ] **Step 1: 写失败测试**：现有用例改用新的 `Violation`；新增 `flags_create_schema`、`flags_references_in_create_table`、`flags_references_in_alter_table_add_constraint`、`ignores_references_in_comment_strings`（`COMMENT ON … IS '引用 references'`）、`ignores_create_schema_in_dollar_body`。
- [ ] **Step 2:** `./gradlew -p build-logic test --tests "MigrationScriptRulesTest"`。Expected: FAIL。
- [ ] **Step 3:** 实现并改名；Gradle 报错信息按违规类型给出对应 `database.md` 节号。
- [ ] **Step 4:** `./gradlew -p build-logic test` 和 `./gradlew checkMigrationScripts`。Expected: PASS（六个服务都已重建）。告诉用户在 IDEA 里点「加载 Gradle 更改」。
- [ ] **Step 5:** 提交 `build: 迁移脚本检查补拦 CREATE SCHEMA 与外键 (PAP-200)`。

---

## 收尾

### Task 22: 文档、全量门控与 PR

- [ ] **Step 1: 清理冲突的说法**（D11）：`grep -rln "SoftDeletableJpaEntity\|ChildJpaEntity\|@SoftDelete\|created_by_name\|DEFAULT_SORT\|CREATE SCHEMA" --include=*.md patra-api patra-tool .claude` 逐个改，`.claude/rules/tech/database.md` 与 `jpa.md` 里禁用它们的说明除外；六个服务的 README 里讲表结构的段落（interaction、notification、analytics 按 schema 描述）改成新表名；interaction 的 `infra/build.gradle.kts` 与 `boot/application.yml` 里提到 schema 的注释删掉。设计文档只改与实现不符的事实。
- [ ] **Step 2: D4 自查**：`grep -rn "@SoftDelete\|created_by_name\|@CreatedDate\|@LastModifiedDate" --include=*.java patra-api patra-tool` 只剩 `patra-tool-jpa` 里 `SoftDeleteBulkDeleteIT` 与它的夹具（它验证的就是这个注解的行为）；`grep -rln "CREATE SCHEMA\|REFERENCES" patra-api/*/*/src/main/resources/db` 为空。
- [ ] **Step 3: D2 反向验证留记录**：在 analytics 临时加一个脚本，依次试「没前缀的表」「没 `id` 的表」「没登记的表」「一条外键」「`CREATE SCHEMA`」，分别跑 `AnalyticsSchemaStandardIT` 与 `checkMigrationScripts`，删掉临时脚本。每种情况报出的规则节号写进 release spec 的 D2 行（「实际：」），完整输出留到 PR 描述里贴。
- [ ] **Step 4: 全量门控**：`./gradlew check integrationTest`，再跑 CI 里才有的端到端测试 `./gradlew :patra-api:patra-catalog:patra-catalog-boot:e2eTest :patra-api:patra-analytics:patra-analytics-boot:e2eTest`（只有这两个服务有 `e2eTest`）。Expected: 全绿（D1，D5 的本地部分）。
- [ ] **Step 5:** 派 `comment-reviewer`（指定 `model`）审本分支改动的注释，改完提交 `docs: 按注释标准修订本版注释`。
- [ ] **Step 6: 停下等本地验收**：向用户汇报完成情况与 D1～D4 的证据，等用户确认后再推送。
- [ ] **Step 7:** 用户确认后 `git push -u origin feat/v0.21-database-standard`，`gh pr create --draft`，描述逐行 `Closes PAP-199` ～ `Closes PAP-206`，附 D2 记录与顺带发现；随即转 ready，用 `ccd_pr` 的 `bind_pr` + `set_monitor` 盯 CI 与评审（D5），每条评审意见回复「已修复（SHA）」或「不修复（理由）」。

### Task 23: mini 上重建六个库并验收（PAP-207）

合并后的上线操作，不改代码。删库前再向用户确认一次。按 release spec 决策 G 的顺序：

- [ ] **Step 1:** 在 mini 上记下 `patra_catalog` 的文献、期刊、作者数。
- [ ] **Step 2:** 停 mini 上的六个服务；`DROP DATABASE` 再 `CREATE DATABASE` 六个业务库（`patra_batch`、`patra_nacos` 不动）。
- [ ] **Step 3:** 合并 PR（用户授权后），等自动部署完成，六个服务启动；查各库 `flyway_schema_history` 的版本号都是 14 位（D6）。
- [ ] **Step 4:** 六个库各查一次：除系统 schema 外只有 `public`、外键 0 条、业务表都带前缀（D7）。
- [ ] **Step 5:** 用 XXL-Job 接口重跑 catalog 导入任务，完成后记下三个数字，与 Step 1 对比（D10）。
- [ ] **Step 6:** 重新注册测试账号 B、C、E；直接在库里给一个用户的 `idn_user_profile.record_remarks` 写备注，从门户改一次昵称，备注还在（D8）。
- [ ] **Step 7:** 门户走一遍：注册登录、收藏文献、发评论、发投稿经验、收到提醒、看阅读历史（D9）。
- [ ] **Step 8:** 版本收尾：release spec 勾 Done、状态改完成并直推 main；Linear Project 改 Completed；手动关 PAP-207。
