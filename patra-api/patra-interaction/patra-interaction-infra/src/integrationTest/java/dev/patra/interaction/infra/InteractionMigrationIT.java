package dev.patra.interaction.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.patra.interaction.infra.config.InteractionITPostgreSQLContainerInitializer;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

/// Flyway 迁移：每个子域一个 schema，整个服务一份历史，版本号是时间戳。
@SpringBootTest(classes = InteractionITBootstrap.class)
@ContextConfiguration(initializers = InteractionITPostgreSQLContainerInitializer.class)
@ActiveProfiles("test")
@DisplayName("interaction 的 Flyway 迁移")
class InteractionMigrationIT {

  @Autowired private JdbcTemplate jdbcTemplate;

  /// 除 PG 自带的 schema 外，只有 public 与子域清单里的 schema（schema 名就是子域名）；subject、collection 一定在。
  /// 新子域随自己的迁移建 schema，不用改这条测试。
  @Test
  void should_create_one_schema_per_subdomain() {
    assertThat(
            jdbcTemplate.queryForList(
                "SELECT schema_name FROM information_schema.schemata "
                    + "WHERE schema_name NOT LIKE 'pg\\_%' AND schema_name <> 'information_schema'",
                String.class))
        .contains("public", "subject", "collection")
        .isSubsetOf("public", "subject", "collection", "discussion", "post", "reaction");
  }

  /// 提醒搬到通知服务后（PAP-143），旧的 `notification` schema 由前向迁移删掉，连同里面的表。
  @Test
  void should_drop_notification_schema() {
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM information_schema.schemata WHERE schema_name = 'notification'",
                Long.class))
        .isZero();
  }

  /// 事件发布登记表（PAP-141）在 public，不属于任何子域；列是 Modulith 固定的那几列。
  @Test
  void should_create_event_publication_in_public() {
    assertThat(
            jdbcTemplate.queryForList(
                "SELECT table_schema FROM information_schema.tables "
                    + "WHERE table_name = 'event_publication'",
                String.class))
        .containsExactly("public");
    assertThat(
            jdbcTemplate.queryForList(
                "SELECT column_name FROM information_schema.columns "
                    + "WHERE table_schema = 'public' AND table_name = 'event_publication'",
                String.class))
        .containsExactlyInAnyOrder(
            "id",
            "listener_id",
            "event_type",
            "serialized_event",
            "publication_date",
            "completion_date",
            "status",
            "completion_attempts",
            "last_resubmission_date");
  }

  /// Flyway 历史表只有一张，在 public。
  @Test
  void should_keep_a_single_flyway_history_in_public() {
    assertThat(
            jdbcTemplate.queryForList(
                "SELECT table_schema FROM information_schema.tables "
                    + "WHERE table_name = 'flyway_schema_history'",
                String.class))
        .containsExactly("public");
  }

  /// 各子域的脚本都执行成功，版本号就是它们的时间戳，按安装顺序递增。新增脚本不用改这条测试。
  @Test
  void should_record_migrations_with_timestamp_versions() {
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM public.flyway_schema_history WHERE NOT success", Long.class))
        .isZero();
    assertThat(
            jdbcTemplate.queryForList(
                "SELECT version FROM public.flyway_schema_history ORDER BY installed_rank",
                String.class))
        .allMatch(version -> version.matches("[0-9]{14}"))
        .isSorted()
        .contains(
            "20261024100000",
            "20261024100100",
            "20261024200000",
            "20261024210000",
            "20261027120000",
            "20261027120100");
  }

  /// 身份锚全表唯一；来源 ID 只对非空行唯一。
  @Test
  void should_create_registry_with_partial_unique_source_index() {
    assertThat(indexDefinition("uk_registry_natural_key"))
        .contains("UNIQUE")
        .contains("(target_type, natural_key)")
        .doesNotContain("WHERE");
    assertThat(indexDefinition("uk_registry_source_id"))
        .contains("UNIQUE")
        .contains("(target_type, source_id)")
        .contains("WHERE (source_id IS NOT NULL)");
  }

  /// 编码、状态、「无来源 ID 必须失效」、PMID 格式四条 CHECK（99 是不存在的类型编码）。
  @Test
  void should_enforce_registry_checks() {
    jdbcTemplate.update("DELETE FROM subject.registry");

    assertThatThrownBy(() -> insert(1L, 99, "pmid:1", 100L, "ACTIVE"))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(() -> insert(2L, 1, "pmid:2", 200L, "GONE"))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(() -> insert(3L, 1, "pmid:3", null, "ACTIVE"))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(() -> insert(4L, 1, "pmid:01", 400L, "ACTIVE"))
        .isInstanceOf(DataIntegrityViolationException.class);
    insert(5L, 1, "pmid:5", null, "UNAVAILABLE");

    assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM subject.registry", Long.class))
        .isEqualTo(1L);
  }

  /// 期刊的 `nlm:` 格式（前导零、`R` 后缀，区分大小写）；帖子的锚必须等于 `post:{source_id}`；所有者两列成对，
  /// 且只有本地对象（类型 3）有。
  @Test
  void should_enforce_journal_post_and_owner_checks() {
    jdbcTemplate.update("DELETE FROM subject.registry");

    insert(1L, 2, "nlm:0404401", 100L, "ACTIVE");
    insert(2L, 2, "nlm:2985213R", 200L, "ACTIVE");
    insertOwned(3L, 3, "post:7", 7L, 9L, true);
    assertThatThrownBy(() -> insert(4L, 2, "nlm:2985213r", 400L, "ACTIVE"))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(() -> insert(5L, 2, "pmid:1", 500L, "ACTIVE"))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(() -> insertOwned(6L, 3, "post:7", 8L, 9L, false))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(() -> insertOwned(7L, 3, "post:8", null, 9L, false))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(() -> insert(8L, 3, "post:8", 8L, "ACTIVE"))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(() -> insertOwned(9L, 1, "pmid:9", 900L, 9L, false))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(() -> insertOwned(10L, 3, "post:10", 10L, 9L, null))
        .isInstanceOf(DataIntegrityViolationException.class);

    assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM subject.registry", Long.class))
        .isEqualTo(3L);
  }

  /// 帖子的来源 ID 就是帖子 ID，永远不为空：失效状态也不例外（`ck_registry_source_present` 放行失效行，
  /// 这里只能靠 `ck_registry_post_key` 自己拦住）。
  @Test
  void should_reject_post_without_source_id_even_when_unavailable() {
    jdbcTemplate.update("DELETE FROM subject.registry");

    assertThatThrownBy(() -> insertOwned(11L, 3, "post:11", null, "UNAVAILABLE", 9L, false))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("ck_registry_post_key");
  }

  /// 发表计数：种类只有 COMMENT / POST / HELPFUL，当天条数大于 0，每个作者每种一行。
  @Test
  void should_enforce_publish_quota_checks() {
    jdbcTemplate.update("DELETE FROM subject.publish_quota");

    insertQuota(1L, "COMMENT", 1);
    insertQuota(1L, "HELPFUL", 1);
    assertThatThrownBy(() -> insertQuota(2L, "LIKE", 1))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(() -> insertQuota(3L, "POST", 0))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(() -> insertQuota(1L, "COMMENT", 2))
        .isInstanceOf(DataIntegrityViolationException.class);

    assertThat(
            jdbcTemplate.queryForObject("SELECT count(*) FROM subject.publish_quota", Long.class))
        .isEqualTo(2L);
  }

  /// 更新时触发器推进 `updated_at`。
  @Test
  void should_bump_updated_at_on_update() {
    jdbcTemplate.update("DELETE FROM subject.registry");
    insert(6L, 1, "pmid:6", 600L, "ACTIVE");
    Timestamp past = Timestamp.from(Instant.parse("2020-01-01T00:00:00Z"));
    jdbcTemplate.update("ALTER TABLE subject.registry DISABLE TRIGGER trg_registry_updated_at");
    jdbcTemplate.update("UPDATE subject.registry SET updated_at = ? WHERE subject_id = 6", past);
    jdbcTemplate.update("ALTER TABLE subject.registry ENABLE TRIGGER trg_registry_updated_at");

    jdbcTemplate.update("UPDATE subject.registry SET state = 'UNAVAILABLE' WHERE subject_id = 6");

    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT updated_at FROM subject.registry WHERE subject_id = 6", Timestamp.class))
        .isAfter(past);
  }

  /// 两张状态窄表按 `user_id` 哈希分 16 区（release spec 决策 B）。
  @Test
  void should_hash_partition_state_tables_by_user_into_16() {
    for (String table : List.of("library_item", "collection_item")) {
      assertThat(
              jdbcTemplate.queryForObject(
                  "SELECT count(*) FROM pg_inherits i JOIN pg_class p ON p.oid = i.inhparent"
                      + " JOIN pg_namespace n ON n.oid = p.relnamespace"
                      + " WHERE n.nspname = 'collection' AND p.relname = ?",
                  Integer.class,
                  table))
          .as(table)
          .isEqualTo(16);
      assertThat(
              jdbcTemplate.queryForObject(
                  "SELECT pg_get_partkeydef(('collection.' || ?)::regclass)", String.class, table))
          .isEqualTo("HASH (user_id)");
    }
  }

  /// 状态窄表的主键以分区键 `user_id` 打头。
  @Test
  void should_key_state_tables_by_user_first() {
    assertThat(primaryKeyColumns("library_item")).containsExactly("user_id", "subject_id");
    assertThat(primaryKeyColumns("collection_item"))
        .containsExactly("user_id", "collection_id", "subject_id");
  }

  /// 收藏夹名称在同一用户下唯一，不同用户可以同名。
  @Test
  void should_keep_collection_names_unique_per_user() {
    jdbcTemplate.update("DELETE FROM collection.collection");
    insertCollection(1L, 10L, "组会");
    assertThatThrownBy(() -> insertCollection(2L, 10L, "组会"))
        .isInstanceOf(DataIntegrityViolationException.class);
    insertCollection(3L, 11L, "组会");

    assertThat(
            jdbcTemplate.queryForObject("SELECT count(*) FROM collection.collection", Long.class))
        .isEqualTo(2L);
  }

  /// 可见性只有两种取值，篇数在 0 ~ 5000。
  @Test
  void should_enforce_collection_checks() {
    jdbcTemplate.update("DELETE FROM collection.collection");
    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "INSERT INTO collection.collection (collection_id, user_id, name, visibility)"
                        + " VALUES (1, 10, 'a', 'SHARED')"))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "INSERT INTO collection.collection (collection_id, user_id, name, visibility,"
                        + " item_count) VALUES (2, 10, 'b', 'PRIVATE', 5001)"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  /// 取主键列（按序）。
  ///
  /// @param table collection schema 下的表名
  /// @return 列名
  private List<String> primaryKeyColumns(String table) {
    return jdbcTemplate.queryForList(
        "SELECT k.column_name FROM information_schema.table_constraints c"
            + " JOIN information_schema.key_column_usage k"
            + " ON k.constraint_name = c.constraint_name AND k.table_schema = c.table_schema"
            + " WHERE c.table_schema = 'collection' AND c.table_name = ?"
            + " AND c.constraint_type = 'PRIMARY KEY' ORDER BY k.ordinal_position",
        String.class,
        table);
  }

  /// 直接插一个私有收藏夹。
  ///
  /// @param collectionId 收藏夹 ID
  /// @param userId 所有者
  /// @param name 名称
  private void insertCollection(long collectionId, long userId, String name) {
    jdbcTemplate.update(
        "INSERT INTO collection.collection (collection_id, user_id, name, visibility)"
            + " VALUES (?, ?, ?, 'PRIVATE')",
        collectionId,
        userId,
        name);
  }

  /// 取索引定义。
  ///
  /// @param name 索引名
  /// @return `pg_indexes.indexdef`
  private String indexDefinition(String name) {
    return jdbcTemplate.queryForObject(
        "SELECT indexdef FROM pg_indexes WHERE schemaname = 'subject' AND indexname = ?",
        String.class,
        name);
  }

  /// 直接插一行发表计数。
  ///
  /// @param userId 作者
  /// @param kind 种类
  /// @param dayCount 当天条数
  private void insertQuota(long userId, String kind, int dayCount) {
    jdbcTemplate.update(
        "INSERT INTO subject.publish_quota (user_id, kind, quota_day, day_count,"
            + " last_published_at) VALUES (?, ?, DATE '2026-10-27', ?, now())",
        userId,
        kind,
        dayCount);
  }

  /// 直接插一行带所有者的（ACTIVE）。
  ///
  /// @param subjectId 本地 ID
  /// @param type 类型编码
  /// @param naturalKey 身份锚
  /// @param sourceId 来源 ID，可空
  /// @param ownerUserId 所有者
  /// @param anonymous 是否匿名，可空
  private void insertOwned(
      long subjectId,
      int type,
      String naturalKey,
      Long sourceId,
      long ownerUserId,
      Boolean anonymous) {
    insertOwned(subjectId, type, naturalKey, sourceId, "ACTIVE", ownerUserId, anonymous);
  }

  /// 直接插一行带所有者的，状态自定。
  ///
  /// @param subjectId 本地 ID
  /// @param type 类型编码
  /// @param naturalKey 身份锚
  /// @param sourceId 来源 ID，可空
  /// @param state 状态
  /// @param ownerUserId 所有者
  /// @param anonymous 是否匿名，可空
  private void insertOwned(
      long subjectId,
      int type,
      String naturalKey,
      Long sourceId,
      String state,
      long ownerUserId,
      Boolean anonymous) {
    jdbcTemplate.update(
        "INSERT INTO subject.registry (subject_id, target_type, natural_key, source_id, state,"
            + " snapshot, snapshot_at, owner_user_id, owner_anonymous)"
            + " VALUES (?, ?, ?, ?, ?, '{}'::jsonb, now(), ?, ?)",
        subjectId,
        type,
        naturalKey,
        sourceId,
        state,
        ownerUserId,
        anonymous);
  }

  /// 直接插一行。
  ///
  /// @param subjectId 本地 ID
  /// @param type 类型编码
  /// @param naturalKey 身份锚
  /// @param sourceId 来源 ID，可空
  /// @param state 状态
  private void insert(long subjectId, int type, String naturalKey, Long sourceId, String state) {
    jdbcTemplate.update(
        "INSERT INTO subject.registry (subject_id, target_type, natural_key, source_id, state,"
            + " snapshot, snapshot_at) VALUES (?, ?, ?, ?, ?, '{}'::jsonb, now())",
        subjectId,
        type,
        naturalKey,
        sourceId,
        state);
  }
}
