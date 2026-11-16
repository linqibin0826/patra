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

  private static SchemaStandardReport inspect(String registry) {
    return SchemaStandard.inspect(dataSource, registry);
  }
}
