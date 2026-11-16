package dev.patra.tool.jpa.standard;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.tool.test.container.initializer.PostgreSQLContainerInitializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;

/// 库表规范推断 1：审计时间由数据库填，`updated_at` 只在行内容真的变了时刷新。
///
/// 规范要求应用不写 `created_at` / `updated_at`，由触发器保证：插入时两列都取当前时间；
/// 更新时先把两列恢复成旧值再比较整行，应用即使写了这两列也不算变化，其余列有变化才把
/// `updated_at` 设成当前时间。这里的函数体就是规范要各服务照抄的 `set_audit_times()`。
///
/// `now()` 是事务开始的时间，所以每条语句各自提交（`JdbcTemplate` 默认自动提交），
/// 中间再停 10 毫秒，保证前后两次的时间能比出先后。
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ContextConfiguration(initializers = PostgreSQLContainerInitializer.class)
@DisplayName("库表规范推断 1：审计时间触发器")
class AuditTimestampTriggerIT {

  private static final String CREATE_FUNCTION =
      """
      CREATE OR REPLACE FUNCTION it_touch_audit_times() RETURNS trigger
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
      $$
      """;

  @Autowired private JdbcTemplate jdbc;

  @BeforeEach
  void createTable() {
    jdbc.execute(CREATE_FUNCTION);
    jdbc.execute(
        """
        CREATE TABLE it_trigger_note
        (
            id         BIGINT      NOT NULL PRIMARY KEY,
            content    TEXT        NULL,
            created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
            updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
        )
        """);
    jdbc.execute(
        """
        CREATE TRIGGER trg_it_trigger_note_audit_times
            BEFORE INSERT OR UPDATE ON it_trigger_note
            FOR EACH ROW EXECUTE FUNCTION it_touch_audit_times()
        """);
    jdbc.update("INSERT INTO it_trigger_note (id, content) VALUES (1, 'a')");
  }

  @AfterEach
  void dropTable() {
    jdbc.execute("DROP TABLE it_trigger_note");
    jdbc.execute("DROP FUNCTION it_touch_audit_times()");
  }

  @Test
  @DisplayName("插入时应用不给值，两个时间由默认值填上且相等")
  void should_fill_both_times_with_default_on_insert() {
    assertThat(sameTimes()).isTrue();
  }

  @Test
  @DisplayName("插入时应用给了两个审计时间，一律换成数据库当前时间")
  void should_ignore_audit_times_written_by_application_on_insert() {
    jdbc.update(
        "INSERT INTO it_trigger_note (id, content, created_at, updated_at)"
            + " VALUES (2, 'a', '2000-01-01', '1999-01-01')");

    assertThat(
            jdbc.queryForObject(
                "SELECT created_at = updated_at AND created_at > '2001-01-01'"
                    + " FROM it_trigger_note WHERE id = 2",
                Boolean.class))
        .isTrue();
  }

  @Test
  @DisplayName("内容变了，updated_at 刷新为更晚的时间，created_at 不变")
  void should_refresh_updated_at_when_content_changes() {
    pause();
    jdbc.update("UPDATE it_trigger_note SET content = 'b' WHERE id = 1");

    assertThat(updatedAfterCreated()).isTrue();
  }

  @Test
  @DisplayName("更新语句没改任何值，updated_at 保持原样")
  void should_keep_updated_at_when_update_changes_nothing() {
    pause();
    jdbc.update("UPDATE it_trigger_note SET content = 'a' WHERE id = 1");

    assertThat(sameTimes()).isTrue();
  }

  @Test
  @DisplayName("应用写了两个审计时间但内容没变，两个时间都保持原样")
  void should_ignore_audit_times_written_by_application() {
    pause();
    jdbc.update(
        "UPDATE it_trigger_note SET created_at = '2000-01-01', updated_at = '2000-01-01'"
            + " WHERE id = 1");

    assertThat(sameTimes()).isTrue();
    assertThat(createdIn2000()).isFalse();
  }

  @Test
  @DisplayName("应用写了 updated_at 同时改了内容，updated_at 取数据库当前时间而不是应用给的值")
  void should_use_database_time_when_application_writes_updated_at_with_content() {
    pause();
    jdbc.update("UPDATE it_trigger_note SET content = 'b', updated_at = '2000-01-01' WHERE id = 1");

    assertThat(updatedAfterCreated()).isTrue();
  }

  private boolean sameTimes() {
    return Boolean.TRUE.equals(
        jdbc.queryForObject(
            "SELECT created_at = updated_at FROM it_trigger_note WHERE id = 1", Boolean.class));
  }

  private boolean updatedAfterCreated() {
    return Boolean.TRUE.equals(
        jdbc.queryForObject(
            "SELECT updated_at > created_at FROM it_trigger_note WHERE id = 1", Boolean.class));
  }

  private boolean createdIn2000() {
    return Boolean.TRUE.equals(
        jdbc.queryForObject(
            "SELECT created_at < '2001-01-01' FROM it_trigger_note WHERE id = 1", Boolean.class));
  }

  private void pause() {
    jdbc.execute("SELECT pg_sleep(0.01)");
  }
}
