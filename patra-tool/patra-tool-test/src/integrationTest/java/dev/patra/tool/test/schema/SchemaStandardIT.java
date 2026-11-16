package dev.patra.tool.test.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import dev.patra.tool.test.container.initializer.PostgreSQLContainerInitializer;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/// 库表结构检查 `SchemaStandard` 的集成测试，需要本机 Docker。
///
/// 每个用例先清空库、建出 `schema/baseline.sql` 里完全合规的一套表，再只做一处改动，断言检查报出的节号和表名。
/// 节号对应 `.claude/rules/tech/database.md` 的章节。
@DisplayName("库表结构检查")
class SchemaStandardIT {

  private static final String REGISTRY = "schema/tables.yml";

  private static DataSource dataSource;
  private static JdbcTemplate jdbc;

  @BeforeAll
  static void startDatabase() {
    try (GenericApplicationContext context = new GenericApplicationContext()) {
      new PostgreSQLContainerInitializer().initialize(context);
      ConfigurableEnvironment environment = context.getEnvironment();
      dataSource =
          new DriverManagerDataSource(
              environment.getRequiredProperty("spring.datasource.url"),
              environment.getRequiredProperty("spring.datasource.username"),
              environment.getRequiredProperty("spring.datasource.password"));
      jdbc = new JdbcTemplate(dataSource);
    }
  }

  /// 删掉 `public` 以外用户建的 schema，重建 `public`，再建基线表。
  @BeforeEach
  void resetToBaseline() throws IOException {
    List<String> schemas =
        jdbc.queryForList(
            "SELECT nspname FROM pg_namespace"
                + " WHERE nspname NOT LIKE 'pg\\_%' AND nspname <> 'information_schema'",
            String.class);
    schemas.forEach(schema -> jdbc.execute("DROP SCHEMA \"" + schema + "\" CASCADE"));
    jdbc.execute("CREATE SCHEMA public");
    jdbc.execute(
        new ClassPathResource("schema/baseline.sql").getContentAsString(StandardCharsets.UTF_8));
  }

  @Test
  @DisplayName("基线完全合规：框架表不查结构，分区子表不算未登记")
  void should_pass_compliant_baseline() {
    assertThat(inspect(REGISTRY).violations()).isEmpty();
  }

  @Test
  @DisplayName("§1 public 以外的 schema")
  void should_reject_extra_schema() {
    jdbc.execute("CREATE SCHEMA stray");

    assertThat(inspect(REGISTRY).violations())
        .extracting(SchemaViolation::section, SchemaViolation::table)
        .containsExactly(tuple("§1", "stray"));
  }

  @Test
  @DisplayName("§1 库里有、登记文件里没有的表")
  void should_reject_unregistered_table() {
    jdbc.execute(
        """
        CREATE TABLE tst_stray
        (
            id         BIGINT      NOT NULL PRIMARY KEY,
            created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
            updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
        );
        CREATE TRIGGER trg_tst_stray_audit_times
            BEFORE INSERT OR UPDATE ON tst_stray
            FOR EACH ROW EXECUTE FUNCTION set_audit_times();
        COMMENT ON TABLE tst_stray IS '未登记的表';
        """);

    assertThat(inspect(REGISTRY).violations())
        .extracting(SchemaViolation::section, SchemaViolation::table)
        .containsExactly(tuple("§1", "tst_stray"));
  }

  @Test
  @DisplayName("§1 登记了、库里却没有的表")
  void should_reject_registered_table_missing() {
    jdbc.execute("DROP TABLE tst_note_tag");

    assertThat(inspect(REGISTRY).violations())
        .extracting(SchemaViolation::section, SchemaViolation::table)
        .containsExactly(tuple("§1", "tst_note_tag"));
  }

  @Test
  @DisplayName("§1 表名没有服务前缀")
  void should_reject_table_without_prefix() {
    jdbc.execute("ALTER TABLE tst_note_tag RENAME TO note_tag");
    jdbc.execute("ALTER INDEX idx_tst_note_tag_note RENAME TO idx_note_tag_note");
    jdbc.execute(
        "ALTER TRIGGER trg_tst_note_tag_audit_times ON note_tag RENAME TO trg_note_tag_audit_times");

    assertThat(inspect("schema/tables-unprefixed.yml").violations())
        .extracting(SchemaViolation::section, SchemaViolation::table)
        .containsExactly(tuple("§1", "note_tag"));
  }

  @Test
  @DisplayName("一次列出所有违规，不在第一条就停")
  void should_report_all_violations_at_once() {
    jdbc.execute("CREATE SCHEMA stray");
    jdbc.execute("DROP TABLE tst_note_tag");

    assertThat(inspect(REGISTRY).violations())
        .extracting(SchemaViolation::section, SchemaViolation::table)
        .containsExactlyInAnyOrder(tuple("§1", "stray"), tuple("§1", "tst_note_tag"));
  }

  @Test
  @DisplayName("§2 主键不是 id")
  void should_reject_primary_key_other_than_id() {
    jdbc.execute("ALTER TABLE tst_note_tag DROP CONSTRAINT tst_note_tag_pkey");
    jdbc.execute("ALTER TABLE tst_note_tag ADD PRIMARY KEY (note_id, tag)");

    assertThat(inspect(REGISTRY).violations())
        .extracting(SchemaViolation::section, SchemaViolation::table)
        .containsExactly(tuple("§2", "tst_note_tag"));
  }

  @Test
  @DisplayName("§2 分区表主键不是分区键加 id")
  void should_reject_partitioned_primary_key_other_than_partition_key_and_id() {
    jdbc.execute("ALTER TABLE tst_event DROP CONSTRAINT tst_event_pkey");
    jdbc.execute("ALTER TABLE tst_event ADD PRIMARY KEY (user_id, created_at)");

    assertThat(inspect(REGISTRY).violations())
        .extracting(SchemaViolation::section, SchemaViolation::table)
        .containsExactly(tuple("§2", "tst_event"));
  }

  @Test
  @DisplayName("§2 外键")
  void should_reject_foreign_key() {
    jdbc.execute(
        "ALTER TABLE tst_note_tag ADD CONSTRAINT fk_tst_note_tag_note"
            + " FOREIGN KEY (note_id) REFERENCES tst_note (id)");

    assertThat(inspect(REGISTRY).violations())
        .extracting(SchemaViolation::section, SchemaViolation::table)
        .containsExactly(tuple("§2", "tst_note_tag"));
  }

  @Test
  @DisplayName("§2 唯一约束（自然键要建成唯一索引）")
  void should_reject_unique_constraint() {
    jdbc.execute(
        "ALTER TABLE tst_note_tag ADD CONSTRAINT uk_tst_note_tag_pair UNIQUE (note_id, tag)");

    assertThat(inspect(REGISTRY).violations())
        .extracting(SchemaViolation::section, SchemaViolation::table)
        .containsExactly(tuple("§2", "tst_note_tag"));
  }

  @Test
  @DisplayName("§3 缺审计时间")
  void should_reject_missing_audit_times() {
    jdbc.execute("ALTER TABLE tst_note_tag DROP COLUMN updated_at");

    assertThat(inspect(REGISTRY).violations())
        .extracting(SchemaViolation::section, SchemaViolation::table)
        .containsExactly(tuple("§3", "tst_note_tag"));
  }

  @Test
  @DisplayName("§3 缺审计时间触发器")
  void should_reject_missing_audit_trigger() {
    jdbc.execute("DROP TRIGGER trg_tst_note_tag_audit_times ON tst_note_tag");

    assertThat(inspect(REGISTRY).violations())
        .extracting(SchemaViolation::section, SchemaViolation::table)
        .containsExactly(tuple("§3", "tst_note_tag"));
  }

  @Test
  @DisplayName("§3 主表缺列或列类型不对")
  void should_reject_main_table_missing_columns() {
    jdbc.execute("ALTER TABLE tst_note DROP COLUMN record_remarks");
    jdbc.execute("ALTER TABLE tst_note ALTER COLUMN ip_address TYPE BYTEA USING NULL");

    assertThat(inspect(REGISTRY).violations())
        .extracting(SchemaViolation::section, SchemaViolation::table)
        .containsExactly(tuple("§3", "tst_note"), tuple("§3", "tst_note"));
  }

  @Test
  @DisplayName("§3 其他表带了主表才有的列")
  void should_reject_main_columns_on_other_table() {
    jdbc.execute(
        "ALTER TABLE tst_note_tag ADD COLUMN created_by BIGINT, ADD COLUMN version BIGINT");

    assertThat(inspect(REGISTRY).violations())
        .extracting(SchemaViolation::section, SchemaViolation::table)
        .containsExactly(tuple("§3", "tst_note_tag"), tuple("§3", "tst_note_tag"));
  }

  @Test
  @DisplayName("§3 用户名列")
  void should_reject_username_column() {
    jdbc.execute(
        "ALTER TABLE tst_note_tag ADD COLUMN username TEXT, ADD COLUMN created_by_name TEXT");
    jdbc.execute("COMMENT ON COLUMN tst_note_tag.username IS '用户名'");
    jdbc.execute("COMMENT ON COLUMN tst_note_tag.created_by_name IS '创建人名'");

    assertThat(inspect(REGISTRY).violations())
        .extracting(SchemaViolation::section, SchemaViolation::table)
        .containsExactly(tuple("§3", "tst_note_tag"), tuple("§3", "tst_note_tag"));
  }

  @Test
  @DisplayName("§3 identity 的用户表可以有 username 列")
  void should_allow_username_in_identity_user_table() {
    jdbc.execute(
        """
        CREATE TABLE idn_user
        (
            id         BIGINT      NOT NULL PRIMARY KEY,
            username   TEXT        NOT NULL,
            created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
            updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
        );
        CREATE TRIGGER trg_idn_user_audit_times
            BEFORE INSERT OR UPDATE ON idn_user
            FOR EACH ROW EXECUTE FUNCTION set_audit_times();
        COMMENT ON TABLE idn_user IS '用户';
        COMMENT ON COLUMN idn_user.username IS '用户名';
        """);

    assertThat(inspect("schema/tables-identity.yml").violations())
        .filteredOn(violation -> violation.section().equals("§3"))
        .isEmpty();
  }

  @Test
  @DisplayName("§4 不带时区的时间列")
  void should_reject_timestamp_without_time_zone() {
    jdbc.execute("ALTER TABLE tst_note_tag ADD COLUMN seen_at TIMESTAMP");
    jdbc.execute("COMMENT ON COLUMN tst_note_tag.seen_at IS '看到的时间'");

    assertThat(inspect(REGISTRY).violations())
        .extracting(SchemaViolation::section, SchemaViolation::table)
        .containsExactly(tuple("§4", "tst_note_tag"));
  }

  @Test
  @DisplayName("§6 索引名没有以 idx_ 加完整表名开头")
  void should_reject_misnamed_index() {
    jdbc.execute("CREATE INDEX idx_tag_note ON tst_note_tag (note_id)");

    assertThat(inspect(REGISTRY).violations())
        .extracting(SchemaViolation::section, SchemaViolation::table)
        .containsExactly(tuple("§6", "tst_note_tag"));
  }

  @Test
  @DisplayName("§6 唯一索引用了普通索引的前缀")
  void should_reject_index_kind_mismatch() {
    jdbc.execute("CREATE UNIQUE INDEX idx_tst_note_tag_pair ON tst_note_tag (note_id, tag)");

    assertThat(inspect(REGISTRY).violations())
        .extracting(SchemaViolation::section, SchemaViolation::table)
        .containsExactly(tuple("§6", "tst_note_tag"));
  }

  @Test
  @DisplayName("§6 检查约束没有以 ck_ 加完整表名开头")
  void should_reject_misnamed_check_constraint() {
    jdbc.execute(
        "ALTER TABLE tst_note_tag ADD CONSTRAINT chk_tag_length CHECK (length(tag) < 100)");

    assertThat(inspect(REGISTRY).violations())
        .extracting(SchemaViolation::section, SchemaViolation::table)
        .containsExactly(tuple("§6", "tst_note_tag"));
  }

  @Test
  @DisplayName("§6 名字正好 63 字节，可能已被 PostgreSQL 截断")
  void should_flag_name_of_exactly_63_bytes() {
    String name = "idx_tst_note_tag_" + "a".repeat(46);
    jdbc.execute("CREATE INDEX " + name + " ON tst_note_tag (tag)");

    assertThat(inspect(REGISTRY).violations())
        .singleElement()
        .satisfies(
            violation -> {
              assertThat(violation.section()).isEqualTo("§6");
              assertThat(violation.message()).contains("可能被截断");
            });
  }

  @Test
  @DisplayName("§6 表或业务列缺注释，标准列不用写")
  void should_reject_missing_comments() {
    jdbc.execute("COMMENT ON COLUMN tst_note_tag.tag IS NULL");
    jdbc.execute("COMMENT ON TABLE tst_note IS NULL");

    assertThat(inspect(REGISTRY).violations())
        .extracting(SchemaViolation::section, SchemaViolation::table)
        .containsExactlyInAnyOrder(tuple("§6", "tst_note_tag"), tuple("§6", "tst_note"));
  }

  @Test
  @DisplayName("§7 失效的索引")
  void should_reject_invalid_index() {
    jdbc.execute(
        "UPDATE pg_index SET indisvalid = false"
            + " WHERE indexrelid = 'idx_tst_note_tag_note'::regclass");

    assertThat(inspect(REGISTRY).violations())
        .extracting(SchemaViolation::section, SchemaViolation::table)
        .containsExactly(tuple("§7", "tst_note_tag"));
  }

  private static SchemaStandardReport inspect(String registry) {
    return SchemaStandard.inspect(dataSource, registry);
  }
}
