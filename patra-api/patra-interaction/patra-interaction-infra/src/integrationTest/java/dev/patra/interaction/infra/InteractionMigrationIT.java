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

  /// 除 PG 自带的 schema 外，恰好是 public 与两个子域的 schema。
  @Test
  void should_create_one_schema_per_subdomain() {
    assertThat(
            jdbcTemplate.queryForList(
                "SELECT schema_name FROM information_schema.schemata "
                    + "WHERE schema_name NOT LIKE 'pg\\_%' AND schema_name <> 'information_schema'",
                String.class))
        .containsExactlyInAnyOrder("public", "subject", "collection");
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

  /// 各子域的脚本都执行成功，版本号就是它们的时间戳。
  @Test
  void should_record_migrations_with_timestamp_versions() {
    assertThat(
            jdbcTemplate.queryForList(
                "SELECT version FROM public.flyway_schema_history "
                    + "WHERE success ORDER BY installed_rank",
                String.class))
        .containsExactly("20261024100000", "20261024100100", "20261024200000", "20261024210000");
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

  /// 编码、状态、「无来源 ID 必须失效」、PMID 格式四条 CHECK。
  @Test
  void should_enforce_registry_checks() {
    jdbcTemplate.update("DELETE FROM subject.registry");

    assertThatThrownBy(() -> insert(1L, 2, "pmid:1", 100L, "ACTIVE"))
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
