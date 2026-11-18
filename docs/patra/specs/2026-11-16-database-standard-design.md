# 库表规范：工程设计

> **版本**：v0.21 Database Standard（[release spec](../release-specs/v0.21-database-standard.md)，Linear P-PAP-18）
> **覆盖 Issue**：PAP-199 ～ PAP-207
> **规则文件**：[`.claude/rules/tech/database.md`](../../../.claude/rules/tech/database.md)（规范本身，短）；本文写推导、各服务的具体改法和守护手段的实现
> **日期**：2026-11-16

---

## 1. 目标与约束

六个服务的库表按同一份规范重建，规范由测试守住。规范的每一项已在 2026-11-13 和用户逐条定下，取舍见 release spec 第 4 节；本文不重复讨论，只写怎么落地。

约束：

- 六个库原地重写迁移脚本、清空重建，不写增量迁移、不做数据迁移（release spec 决策 F）。
- 接口返回的字段名不变，门户不改（范围边界第 5 条）。
- 写侧技术按服务声明：registry、catalog、identity 用 JPA；interaction、notification、analytics 用 `JdbcClient` 原生 SQL（决策 B）。
- 一个后端 PR 承载 PAP-199 ～ PAP-206。

---

## 2. 五条推断的验证结果（PAP-199）

测试在 `patra-tool/patra-tool-jpa/src/integrationTest/java/dev/patra/tool/jpa/standard/`，用 Testcontainers 起真实 PostgreSQL 17，2026-11-16 全部通过（15 个用例）。

| # | 推断 | 测试 | 结果 | 对规则的影响 |
|---|---|---|---|---|
| 1 | 触发器能做到「行内容真的变了才刷新 `updated_at`，应用写的值一律忽略」 | `AuditTimestampTriggerIT`（6 例） | 成立 | 写进规则第 4 节。验证中补了插入分支：原先只在更新时触发，插入时应用硬写的时间会被接受，现在插入也取数据库时间 |
| 2 | 审计时间做只读映射时，Hibernate 批量写入仍然生效；`@Generated` 会让它退化成逐条 | `ReadOnlyAuditColumnBatchingIT`（3 例） | 成立。只读映射插入 10 行准备 1 条语句、更新 10 行准备 2 条（1 查询 + 1 批量更新）；`@Generated` 插入 10 行至少准备 10 条 | 写进规则第 4 节：实体默认不映射，查询要用时只读映射，禁止 `@Generated` / `@CreationTimestamp` |
| 3 | 普通表以后能把自然键的唯一索引提升为主键、删掉 `id`，不用重写整张表 | `UniqueIndexPromotionIT`（2 例） | 成立，但有前提：自然键必须建成**唯一索引**。数据文件编号前后不变、行数不变。建成唯一约束的，它名下的索引不能直接提升 | 写进规则第 2 节：自然键用 `CREATE UNIQUE INDEX`，不用 `UNIQUE` 约束。定主键时用户依据的「以后能低成本改回联合主键」在这个前提下成立 |
| 4 | 去掉 `@SoftDelete` 后，JPQL 批量删除从「标记删除」变成真删除 | `SoftDeleteBulkDeleteIT`（2 例） | 成立。映射了 `@SoftDelete` 时 `DELETE` 只写上 `deleted_at`；没有时行被删掉 | PAP-203 去掉注解前处理 catalog 的两条 JPQL 批量删除：查下来 app 层没有调用方，连同端口方法删掉，免得它们悄悄变成真删除 |
| 5 | 预分配主键、没有 version 的实体，`saveAll` 每行先查一次；实现 `Persistable` 后直接插入 | `PersistableInsertIT`（2 例） | 成立。现在的值对象基类保存 10 行准备 11 条语句（10 次查询 + 1 次批量插入）；实现 `Persistable` 后准备 1 条 | PAP-201 让值对象基类实现 `Persistable` |

这些测试随 PR 保留：它们守的是框架和数据库的行为，Hibernate、Spring Data 或 PostgreSQL 升级后行为变了，测试会先失败。

---

## 3. 公共部分

### 3.1 审计时间触发器

每个服务的第一个迁移脚本在 `public` 里定义这个函数，函数体与 `AuditTimestampTriggerIT` 里验证过的一致：

```sql
CREATE FUNCTION set_audit_times() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        NEW.created_at := now();
        NEW.updated_at := NEW.created_at;
        RETURN NEW;
    END IF;
    NEW.created_at := OLD.created_at;
    NEW.updated_at := OLD.updated_at;
    IF NEW IS DISTINCT FROM OLD THEN
        NEW.updated_at := now();
    END IF;
    RETURN NEW;
END
$$;
COMMENT ON FUNCTION set_audit_times() IS '审计时间由数据库维护：插入取当前时间；更新时行内容变了才刷新 updated_at；应用写的值一律忽略';
```

每张表建表后挂一个触发器：

```sql
CREATE TRIGGER trg_{表名}_audit_times
    BEFORE INSERT OR UPDATE ON {表名}
    FOR EACH ROW EXECUTE FUNCTION set_audit_times();
```

两点说明：

- `now()` 是事务开始的时间。一个长事务里写的所有行，审计时间都相同。审计列不参与业务规则（规则第 4 节），所以这不是问题。
- 列仍然写 `DEFAULT now()`。触发器才是真正的来源，默认值只是让表结构一眼看得懂。
- 分区表在父表上建触发器，PostgreSQL 会自动挂到每个分区上。

### 3.2 表登记文件

位置：每个服务 `patra-{svc}-infra/src/main/resources/db/tables.yml`，和迁移脚本放在一起，结构检查从类路径读。

```yaml
# 本服务库里的每一张表。档位：main 主表（带全套审计列）/ other 其他表。
# 新建表必须在这里登记，否则结构检查失败。
prefix: itr
tables:
  itr_comment:
    tier: main
    note: 文献讨论的评论和楼内回复，删除后变成占位
  itr_comment_revision:
    tier: other
    note: 评论正文的修订记录，只追加
framework:
  - flyway_schema_history
  - event_publication
```

`note` 写这张表是什么，拿不准档位的表顺便写一句为什么这样归。

### 3.3 结构检查（PAP-200）

放在 `patra-tool-test`，包 `dev.patra.tool.test.schema`。入口是一个可以在任何集成测试里调用的类：

```java
SchemaStandardReport report = SchemaStandard.inspect(dataSource, "db/tables.yml");
assertThat(report.violations()).isEmpty();
```

它查 `pg_catalog` 和 `information_schema`，**一次列出所有违规**，不在第一条就停，方便一个服务重建时对着清单改。每条违规带表名、规则文件的节号和一句说明，例如：

```
[§2] itr_library_item：主键是 (user_id, subject_id)，应为 (user_id, id)
[§3] itr_helpful：缺少 created_at、updated_at
[§6] cat_publication_author：索引 idx_pub_author_author 没有以 idx_cat_publication_author_ 开头
```

检查项：

| 规则节 | 检查 |
|---|---|
| §1 | 除系统 schema（`pg_catalog`、`information_schema`、`pg_toast`）外只有 `public` |
| §1 | `public` 里每张表都已登记，登记的每张表都存在；分区子表（`relispartition`）跳过 |
| §1 | 表名以 `{prefix}_` 开头，框架表除外 |
| §2 | 主键是 `id BIGINT`；分区表是分区键列加 `id` |
| §2 | 没有外键约束，没有 `UNIQUE` 约束（自然键用唯一索引） |
| §3 | 每张表有 `created_at`、`updated_at`（`TIMESTAMPTZ NOT NULL`）；主表另有 `created_by BIGINT`、`updated_by BIGINT`、`ip_address INET`、`record_remarks JSONB` |
| §4 | 每张表挂 `set_audit_times` 触发器（`BEFORE INSERT OR UPDATE`、逐行） |
| §3 | 其他表不得有 `created_by`、`updated_by`、`ip_address`、`record_remarks`、`version` |
| §3 | 不得出现 `username`、`user_name`、`*_by_name` 列（identity 两张用户名表除外） |
| §4 | 所有时间列都是 `TIMESTAMPTZ`，没有 `TIMESTAMP WITHOUT TIME ZONE` |
| §6 | 非主键约束和索引以 `uk_` / `ck_` / `idx_` + 完整表名开头；长度正好 63 字节的名字报为「可能被截断」 |
| §6 | 每张表、每个非标准列都有 `COMMENT` |
| §7 | 没有 `INVALID` 状态的索引 |

每个服务在 infra 的集成测试里加一个 `{Module}SchemaStandardIT`，跑完 Flyway 迁移后调用上面的入口。

现有的迁移脚本检查（R6，`build-logic` 里的 `MigrationColumnCheck`）补两条：拦 `CREATE SCHEMA`、拦 `REFERENCES`。它在 `./gradlew check` 里跑，比集成测试反馈快。

现有 `InteractionMigrationIT`、`NotificationMigrationIT` 里「每个子域一个 schema」的断言删掉，其余断言（Flyway 历史表在 `public`、版本号 14 位且递增、CHECK 生效等）保留。

### 3.4 ArchUnit 规则（PAP-200）

放在 `patra-tool-test`，新类 `PersistenceTechnologyRules`，分两组。各服务的 `*ArchitectureTest` 按自己声明的技术引用一组。

**JPA 服务**（registry、catalog、identity）：

- infra 里不实现 `..port.read..` 端口的类，不得依赖 `JdbcClient`、`JdbcTemplate`、`NamedParameterJdbcTemplate`。不改数据的锁适配器（`PgAdvisoryRunLockAdapter`）逐个登记豁免。
- 带 `@Modifying` 的方法，`flushAutomatically` 和 `clearAutomatically` 都必须为 `true`；`@Modifying` 必须和 `@Query` 一起出现。
- 任何类不得调用 `Query#executeUpdate`；读适配器以外的类不得调用 `EntityManager#createNativeQuery`（只读的原生查询放 Dao 或读适配器）。
- `@Entity` 必须继承 `BaseJpaEntity` 或 `ValueObjectJpaEntity`。

**SQL 服务**（interaction、notification、analytics）：

- 不得依赖 `jakarta.persistence..`。
- `JdbcClient` 只出现在 `..infra..persistence..`。

「读适配器」按它实现的端口判定，不按类名：现在有一个叫 `EmailSubscriptionReadAdapter` 的类在插入数据，PAP-205 把它改成只读（见 5.4）。

### 3.5 JPA 实体基类（PAP-201）

| 基类 | 用于 | 字段 |
|---|---|---|
| `BaseJpaEntity` | 主表 | `id`；`createdBy`（`@CreatedBy`，不可更新）；`updatedBy`（`@LastModifiedBy`）；`ipAddress`（`InetAddress`，映射 `INET`）；`recordRemarks`（只读）；`version`（`@Version`） |
| `ValueObjectJpaEntity` | 其他表 | `id`；实现 `Persistable`：新建对象为新行，`@PostLoad` / `@PostPersist` 后不再是新行 |

- 不再映射 `created_at`、`updated_at`。需要按它查询或排序的实体，自己加只读字段（`insertable = false, updatable = false`）。现在只有一处：identity 公开主页的「加入时间」读 `idn_user.created_at`。catalog 里按 `updated_at` 排序的后台文献列表查询没有调用方，随软删除改造删掉。领域聚合里只为承接这两列的 `createdAt` / `updatedAt` 字段一并删掉。
- 删掉 `ChildJpaEntity`、`SoftDeletableJpaEntity`。7 个子实体改继承 `ValueObjectJpaEntity`，去掉 `version`：查下来它们都是整组替换或只追加缺失的年份，乐观锁用不上。`SoftDeletableJpaEntity` 还被 registry 2 个、catalog 4 个实体继承，随它们改成显式列后删。
- 删掉 `created_by_name`、`updated_by_name` 和没有引用的 `DEFAULT_SORT`。
- `JpaAuditingConfiguration` 保留操作人提供者，去掉时间提供者。

**`record_remarks` 被清空的修法**：只读映射后，identity 用聚合重建实体再合并保存时，这一列不在 UPDATE 语句里，库里的值不会被覆盖。

**`ip_address` 的遗留风险**：它是可写映射。这一版没有代码填它，所以没有影响；以后开始填 IP 时，identity 的合并保存会用空值覆盖它，要一起处理。

---

## 4. 表的归类

### 4.1 老三个服务

用户 2026-11-13 定：老服务里原来带全套审计列的表继续算主表，不降档。按实体基类数，原来带全套的是继承 `BaseJpaEntity` 或 `SoftDeletableJpaEntity` 的 22 张。

| 服务 | 主表 |
|---|---|
| registry（3） | `sys_dict_type`、`sys_dict_item`、`sys_reference_standard` |
| catalog（11） | `cat_publication`、`cat_venue`、`cat_venue_instance`、`cat_author`、`cat_organization`、`cat_mesh_descriptor`、`cat_mesh_qualifier`、`cat_mesh_scr`、`cat_keyword`、`cat_investigator`，以及 `cat_publication_fulltext` |
| identity（9） | `idn_user`、`idn_user_password_credential`、`idn_user_login_record`、`idn_user_profile`、`idn_user_profile_link`、`idn_user_research_interest`、`idn_user_former_username`、`idn_email_token`、`idn_user_external_identity` |

其余表都是其他表。

### 4.2 新三个服务：表名和档位

表名按 `{前缀}_{聚合}[_{子表}]`。派生表（计数）用它统计的那个聚合的名字开头。

**interaction**

| 原表 | 新表 | 档位 | 补 `id` |
|---|---|---|---|
| `subject.registry` | `itr_subject` | 主表 | 改名（`subject_id` → `id`） |
| `subject.publish_quota` | `itr_publish_quota` | 其他 | 是 |
| `collection.collection` | `itr_collection` | 主表 | 改名 |
| `collection.collection_item` | `itr_collection_item`（按 `user_id` 分区） | 其他 | 是，主键 `(user_id, id)` |
| `collection.library_item` | `itr_library_item`（按 `user_id` 分区） | 其他 | 是，主键 `(user_id, id)` |
| `collection.subject_stats` | `itr_library_subject_stats` | 其他 | 是 |
| `discussion.comment` | `itr_comment` | 主表 | 改名 |
| `discussion.comment_revision` | `itr_comment_revision` | 其他 | 是 |
| `discussion.thread_stats` | `itr_comment_thread_stats` | 其他 | 是 |
| `discussion.subject_stats` | `itr_comment_subject_stats` | 其他 | 是 |
| `post.post` | `itr_post` | 主表 | 改名 |
| `post.submission_experience` | `itr_post_submission_experience` | 其他 | 是 |
| `post.revision` | `itr_post_revision` | 其他 | 是 |
| `reaction.helpful` | `itr_helpful` | 其他 | 是 |
| `reaction.target_stats` | `itr_helpful_target_stats` | 其他 | 是 |
| `reaction.user_stats` | `itr_helpful_user_stats` | 其他 | 是 |

**notification**

| 原表 | 新表 | 档位 | 补 `id` |
|---|---|---|---|
| `inbox.notification` | `ntf_notification`（按 `recipient_user_id` 分区） | 主表 | 改名，主键 `(recipient_user_id, id)` |
| `inbox.helpful_actor` | `ntf_notification_helpful_actor` | 其他 | 是 |
| `inbox.comment` | `ntf_comment` | 其他 | 是 |
| `inbox.subject_tombstone` | `ntf_subject_tombstone` | 其他 | 是 |
| `kernel.presence` | `ntf_presence` | 其他 | 是 |
| `mail.mail_delivery` | `ntf_mail_delivery` | 其他 | 是；上游事件 ID 存成 `event_id` 加唯一索引 |
| `mail.reminder_queue` | `ntf_reminder_queue` | 其他 | 是 |
| `mail.reminder_delivery` | `ntf_reminder_delivery` | 主表 | 改名；本地 UUID 改雪花 ID |
| `preference.email_preference` | `ntf_email_preference` | 主表 | 是 |

**analytics**

| 原表 | 新表 | 档位 | 补 `id` |
|---|---|---|---|
| `history.reading_history` | `anl_reading_history` | 其他 | 是 |
| `history.recording_setting` | `anl_recording_setting` | 主表 | 是 |
| `history.deleted_entry` | `anl_reading_history_tombstone` | 其他 | 是 |
| `insight.hot_reading` | `anl_hot_reading` | 其他 | 是 |
| `insight.co_reading` | `anl_co_reading` | 其他 | 是 |
| `insight.snapshot_run` | `anl_snapshot_run` | 其他 | 是 |

### 4.3 业务时间列

原来把审计时间当业务时间用的 5 张表，各加一个业务时间列，由应用用注入的 `Clock` 写：

| 表 | 新列 | 接管的用途 |
|---|---|---|
| `itr_comment` | `published_at` | 排序和游标、编辑宽限期、事件里的发布时间、楼的最后回复时间 |
| `itr_post` | `published_at` | 排序和游标、编辑宽限期、页面上的「发布于」 |
| `itr_collection` | `content_changed_at` | 改名、改可见性、增减文献时刷新；列表按它排序、页面上的「更新于」 |
| `ntf_notification` | `notified_at` | 邮件提醒的发送时间窗口、和最后在线时间比较 |
| `ntf_comment` | `synced_at` | 插入、上游版本更新、上游删除时刷新（原 `updated_at` 就是这样写的）；过期清理按它删 |

已经单独起名的业务时间列（`collected_at`、`added_at`、`queued_at`、`read_at` 等）保持原名。接口返回的字段名不变，比如评论仍然返回 `createdAt`，值改从 `published_at` 取。

### 4.4 列改名

| 服务 | 改名 |
|---|---|
| registry | `type_id` → `dict_type_id`、`item_id` → `dict_item_id` |
| catalog | `org_id` → `organization_id`（4 张表）、`related_org_id` → `related_organization_id`、`pub_author_id` → `publication_author_id` |
| interaction | 主表主键改回 `id`；`author_id` → `author_user_id`、`root_id` → `root_comment_id`（评论表和楼统计表）、`parent_id` → `parent_comment_id`；评论的 `version` → `body_version` |
| notification | 主表主键改回 `id`；`recipient_id` → `recipient_user_id`、`actor_id` → `actor_user_id`；评论副本的 `version` → `source_version`、`deleted_at` → `source_deleted_at`；墓碑的 `deleted_at` → `source_deleted_at` |
| analytics | `article_id` → `publication_id`、`related_article_id` → `related_publication_id`；墓碑的 `deleted_at` → `removed_at` |

改名同时改 Java 代码里对应的 SQL；JPA 服务的实体字段随列改名，领域模型的字段名不动。原生 SQL 服务只改库和 SQL 字符串，Java 标识符不动（接口不变，领域里原来的叫法照旧）。

---

## 5. 各服务的落地要点

每个服务按同一个顺序做：先跑它的结构检查拿到违规清单（PAP-200 已让它失败），重写迁移脚本，改代码和测试，直到结构检查和原有测试都通过。

### 5.1 registry 与 identity（PAP-202）

- registry 的两个种子脚本（V2 国家、V3 语言）改成可重复执行的 `R__` 脚本，用 `ON CONFLICT (id) DO UPDATE`。种子行的 `id` 写死为小整数（原值减去 `800000000000000000` 或 `900000000000000000`）：雪花 ID 生成器产生的数远大于此，不会冲突。种子脚本放 `db/seed/`，服务配置的 Flyway 位置是 `db/migration` 加 `db/seed`，集成测试只用 `db/migration`（替代现在的 `flyway.target: 1`）。
- registry 的字典类型、字典项两个实体也继承 `SoftDeletableJpaEntity`，同样改成显式的 `deletedAt`，8 个派生查询补「未删除」条件。
- identity 删 8 条外键。13 条不经实体的写入里，12 条已经是 Dao 上的 `@Query`，补上两个属性；剩下 1 条是邮件令牌的消费（`JdbcClient` 执行 `UPDATE … RETURNING`），改成「加锁查出实体再修改」。

### 5.2 catalog（PAP-203）

- 3 张例外表改回雪花 `id`：原文缓存（原主键 `pmid`）、全文正文（原主键 `publication_id`）、机构别名（原来是数据库自增，种子脚本改为写死 `id`）。原主键改为唯一索引。
- 所有 `UNIQUE` 约束改成唯一索引；约束和索引按用途短语重命名。
- 4 个软删除实体（文献、期刊、机构、作者）去掉 `@SoftDelete`，加普通字段 `deletedAt`。约 48 个查询方法补 `deleted_at IS NULL` 条件；为绕开自动过滤写成原生 SQL 的 9 处改回普通查询。去掉注解后会从软删变成真删除的方法（两条 JPQL 批量删除、文献和作者的按 ID 删除）app 层都没有调用方，连同端口方法删掉（推断 4）；没有调用方的后台文献列表查询一并删。
- 44 条不经实体的写入改成合规写法：35 条已经是 Dao 上的 `@Query`，补上两个属性；9 条在适配器里用 `EntityManager` 执行，挪进 Dao。其中两条要先改写：一条按条件拼接 SQL，拆成两个固定的方法；一条按参数决定改哪些列，改成 `COALESCE`。另有 11 个派生删除方法挂了 `@Modifying` 却没有 `@Query`，改成带两个属性的 JPQL 批量删除，和其余 32 条删除一致：批量语句立即执行，「整组删再插」时不会被 Hibernate 排到插入之后。
- 14 张表建表时带全套审计列，但实体继承值对象基类、从没写过这些列（文献的标识符、摘要等子表，研究者关联、人名主题、PubMed 文件），按 4.1 归其他表。
- 50 个实体上的 `@Table(indexes / uniqueConstraints)` 元数据全是旧名字，没开 `ddl-auto`、只是元数据，整体删掉。

### 5.3 interaction（PAP-204）

- 去掉 `CREATE SCHEMA`，表改名见 4.2，同名的两张 `subject_stats` 自然分开。
- 12 张表补 `id`；原主键改为唯一索引。分区表的唯一索引要带分区键 `user_id`。
- 4 张主表的写入语句显式写 `created_by` / `updated_by`，值是调用方传进来的操作人 ID（作者、收藏夹主人）。对象登记表里从 catalog 解析出来的行写空，它是外部数据的本地副本，不是谁创建的；本地发布的帖子对象写作者 ID；对账任务写空。
- 删 5 条外键；评论修订原来靠级联删除，改成删评论时在同一事务里显式删修订。

### 5.4 notification（PAP-205）

- 同 interaction 的做法。提醒表是分区表，主键 `(recipient_user_id, id)`。
- 提醒投递的 ID 从本地 UUID 改为雪花 ID；它同时是发信的幂等键，发信那边随之改用这个 ID。
- 「有帮助」参与人原来靠级联删除，改成显式删除。
- 消息消费、定时任务、匿名退订写入时操作人为空。
- `EmailSubscriptionReadAdapter` 在读端口里插入默认偏好行。mail 子域按分包规范只能用 preference 的读端口、读模型和事件，调不到它的仓储，所以不是「挪到仓储」，而是：preference 订阅 inbox 的 `NotificationsCreatedEvent`，在写提醒的同一事务里为收件人建默认偏好行（会发邮件的回复、提及两类都经这个事件）；读端口只读，读不到行视为不变量被破坏。
- 发信的幂等键 `OutgoingMail.deliveryId` 现在是 UUID，同时承载安全邮件的上游事件 ID，改成字符串，提醒投递传雪花 ID 的十进制串。

### 5.5 analytics（PAP-206）

- 6 张表全部补 `id` 和两个审计时间；阅读记录设置补主表的四列。ClickHouse 不动。

### 5.6 上线（PAP-207）

按 release spec 决策 G 的顺序：记下 catalog 的文献、期刊、作者数 → 停 mini 上的服务 → 删库重建 → 合并 PR → 等自动部署 → 重跑导入任务 → 重新注册测试账号 → 走验证场景。

---

## 6. 我自己拿的主意（请重点看）

下面几条是落地时必须定、但讨论里没有逐条定过的。不对的话直接改。

1. **identity 的曾用用户名、资料链接、研究兴趣三张表算主表。** 依据是你选的「原来带全套的不降档」。汇报页逐张归类那一节里曾写「曾用用户名 → 其他表」，和这条矛盾，以这条为准。
2. **`cat_publication_fulltext` 算主表。** 它的实体继承的是值对象基类，但它有自己的抓取和解析状态，会被反复更新。这是唯一一张由其他表升为主表的。
3. **触发器在插入时也生效。** 原来只说「应用写的值一律忽略」，验证时发现只管更新做不到这一点，所以插入时也把两个时间换成数据库时间（3.1）。
4. **自然键一律用唯一索引，不用唯一约束。** 推断 3 的验证结果带出来的。
5. **派生表的命名用它统计的聚合开头**，例如 `itr_comment_thread_stats`、`itr_helpful_target_stats`，而不是子域名 `discussion`、`reaction`。
6. **墓碑列的新名字**：上游删除的用 `source_deleted_at`，阅读历史的用户删除记录用 `removed_at`。
7. **种子数据的 `id` 写死为从 1 开始的小整数**，种子脚本统一改成可重复执行的 `R__` 脚本。
8. **表登记文件放在 infra 的 `db/tables.yml`**，和迁移脚本放在一起；结构检查一次列出所有违规。
9. **ArchUnit 按实现的端口区分读写适配器**，不按类名。

写计划时按代码现状补的决定（2026-11-16）：

10. **notification 的默认偏好行改成随提醒预建**（5.4），新增一条 preference 订阅 inbox 事件的依赖边；读端口读不到行就报错。
11. **去掉 `@SoftDelete` 后会变成真删除、又没有调用方的方法直接删掉**，不改写（5.2）。
12. **catalog 11 个挂了 `@Modifying` 的派生删除改成 JPQL 批量删除**，保证「整组删再插」时删除先执行（5.2）。
13. **catalog 14 张名义上带全套审计列、实体从没写过的表归其他表**（5.2）。
14. **identity 聚合删掉 `createdAt` / `updatedAt`**；公开主页的加入时间由 `UserEntity` 的只读映射提供（3.5）。
15. **registry 种子移到 `db/seed/`**，服务的 Flyway 位置随之加一项（5.1）。
16. **改名范围**：JPA 服务的实体字段随列改名；原生 SQL 服务只改库和 SQL，Java 标识符不动（4.4）。
17. **不经实体写主表时的操作人**：JPA 服务取 JPA 审计用的同一个 `AuditorAware`，端口不变；原生 SQL 服务里操作人必然是行主人时用已有参数，否则端口加 `actorUserId`。
18. **迁移脚本检查改名为 `checkMigrationScripts`**：它不再只查列。
19. **收藏夹建夹时 `content_changed_at` 可能比 `created_at` 早几毫秒**，接受：门户只按天显示「更新于」，不比较两者。

---

## 7. 风险

| 风险 | 后果 | 怎么守 |
|---|---|---|
| 合并 PR 和 mini 重建不在同一个时间窗 | 新版本连旧库，服务起不来 | 决策 G 的顺序；合并前先停服务 |
| 原生 SQL 里漏写操作人 | 主表的 `created_by` 为空，查不出是谁写的 | ArchUnit 看不到字符串里的 SQL，靠各服务的仓储集成测试断言这两列 |
| `@Modifying` 漏了 `flushAutomatically` | 原生语句读到还没落库的旧数据 | ArchUnit 检查两个属性都为 `true` |
| 去掉 `@SoftDelete` 后漏补「未删除」条件 | 已删除的文献、期刊被查出来 | 6 个实体的查询方法逐个补，PAP-203 的集成测试覆盖删除后查询 |
| 一个 PR 改动过大 | 评审和回归困难 | 按 Issue 分提交；每个服务的结构检查和原有测试都通过才进下一个 |
