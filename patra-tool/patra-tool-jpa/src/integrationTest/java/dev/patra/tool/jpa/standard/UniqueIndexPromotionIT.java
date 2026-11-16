package dev.patra.tool.jpa.standard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.patra.tool.test.container.initializer.PostgreSQLContainerInitializer;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;

/// 库表规范推断 3：普通表以后能把自然键的唯一索引提升为主键、删掉 `id`，不用重写整张表。
///
/// 规范规定每张表都有雪花 `id`、自然键另建唯一约束。定这条时的一个前提是：哪张关系表以后
/// 真成了写入瓶颈，可以低成本改回联合主键。这里验证这个前提。
///
/// 是否重写整张表看 `pg_class.relfilenode`：重写会换一个新的数据文件，这个编号随之改变。
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ContextConfiguration(initializers = PostgreSQLContainerInitializer.class)
@DisplayName("库表规范推断 3：唯一索引提升为主键")
class UniqueIndexPromotionIT {

  @Autowired private JdbcTemplate jdbc;

  @AfterEach
  void dropTable() {
    jdbc.execute("DROP TABLE IF EXISTS it_promote");
  }

  @Test
  @DisplayName("自然键建成唯一索引：能直接提升为主键、删掉 id，数据文件不变，行数不变")
  void should_promote_unique_index_without_rewriting_table() {
    createTable(
        "CREATE UNIQUE INDEX uk_it_promote_owner_target ON it_promote (owner_id, target_id)");
    long before = relfilenode();

    jdbc.execute("ALTER TABLE it_promote DROP CONSTRAINT it_promote_pkey");
    jdbc.execute(
        "ALTER TABLE it_promote ADD CONSTRAINT it_promote_pkey"
            + " PRIMARY KEY USING INDEX uk_it_promote_owner_target");
    jdbc.execute("ALTER TABLE it_promote DROP COLUMN id");

    assertThat(relfilenode()).isEqualTo(before);
    assertThat(primaryKeyColumns()).containsExactly("owner_id", "target_id");
    assertThat(jdbc.queryForObject("SELECT count(*) FROM it_promote", Long.class)).isEqualTo(1000L);
  }

  @Test
  @DisplayName("自然键建成唯一约束：它名下的索引不能直接提升为主键")
  void should_reject_promoting_index_owned_by_unique_constraint() {
    createTable(
        "ALTER TABLE it_promote ADD CONSTRAINT uk_it_promote_owner_target"
            + " UNIQUE (owner_id, target_id)");
    jdbc.execute("ALTER TABLE it_promote DROP CONSTRAINT it_promote_pkey");

    assertThatThrownBy(
            () ->
                jdbc.execute(
                    "ALTER TABLE it_promote ADD CONSTRAINT it_promote_pkey"
                        + " PRIMARY KEY USING INDEX uk_it_promote_owner_target"))
        .isInstanceOf(DataAccessException.class);
  }

  private void createTable(String naturalKeyDdl) {
    jdbc.execute(
        """
        CREATE TABLE it_promote
        (
            id        BIGINT NOT NULL PRIMARY KEY,
            owner_id  BIGINT NOT NULL,
            target_id BIGINT NOT NULL,
            note      TEXT   NULL
        )
        """);
    jdbc.execute(naturalKeyDdl);
    jdbc.update(
        "INSERT INTO it_promote (id, owner_id, target_id, note)"
            + " SELECT g, g / 10, g % 10, 'n' FROM generate_series(1, 1000) AS g");
  }

  private long relfilenode() {
    Long node =
        jdbc.queryForObject(
            "SELECT relfilenode FROM pg_class WHERE oid = 'it_promote'::regclass", Long.class);
    return node == null ? -1 : node;
  }

  private List<String> primaryKeyColumns() {
    return jdbc.queryForList(
        """
        SELECT a.attname
        FROM pg_index i
        JOIN LATERAL unnest(i.indkey) WITH ORDINALITY AS k(attnum, ord) ON true
        JOIN pg_attribute a ON a.attrelid = i.indrelid AND a.attnum = k.attnum
        WHERE i.indrelid = 'it_promote'::regclass AND i.indisprimary
        ORDER BY k.ord
        """,
        String.class);
  }
}
