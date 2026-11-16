package dev.patra.tool.test.schema;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;

/// 一张表在系统目录里的样子，从 `pg_catalog` 一次读出，供 `SchemaStandard` 的各条规则使用。
///
/// @param name 表名
/// @param columns 列，按定义顺序
/// @param primaryKey 主键列，按主键里的顺序；没有主键时为空
/// @param partitionKey 分区键列；不是分区表时为空
/// @param constraints 主键以外的约束
/// @param auditTriggers 调用 `set_audit_times()` 的触发器定义（`pg_get_triggerdef` 的输出）
record TableShape(
    String name,
    List<Column> columns,
    List<String> primaryKey,
    List<String> partitionKey,
    List<Constraint> constraints,
    List<String> auditTriggers) {

  /// 防御性拷贝。
  TableShape {
    columns = List.copyOf(columns);
    primaryKey = List.copyOf(primaryKey);
    partitionKey = List.copyOf(partitionKey);
    constraints = List.copyOf(constraints);
    auditTriggers = List.copyOf(auditTriggers);
  }

  /// 一列。
  ///
  /// @param name 列名
  /// @param type `format_type` 给出的类型，如 `timestamp with time zone`
  /// @param notNull 是否 `NOT NULL`
  record Column(String name, String type, boolean notNull) {}

  /// 一个约束。
  ///
  /// @param name 约束名
  /// @param type `pg_constraint.contype`：`f` 外键、`u` 唯一、`c` 检查、`x` 排他
  record Constraint(String name, String type) {}

  /// 按名字取列。
  Optional<Column> column(String columnName) {
    return Optional.ofNullable(columnsByName().get(columnName));
  }

  boolean partitioned() {
    return !partitionKey.isEmpty();
  }

  private Map<String, Column> columnsByName() {
    return columns.stream().collect(Collectors.toMap(Column::name, Function.identity()));
  }

  /// 读 `public` 里的一张表。
  static TableShape load(JdbcTemplate jdbc, String table) {
    String relation = "public." + table;
    List<Column> columns =
        jdbc.query(
            """
            SELECT a.attname, format_type(a.atttypid, a.atttypmod) AS type, a.attnotnull
            FROM pg_attribute a
            WHERE a.attrelid = ?::regclass AND a.attnum > 0 AND NOT a.attisdropped
            ORDER BY a.attnum
            """,
            (rs, rowNum) ->
                new Column(
                    rs.getString("attname"), rs.getString("type"), rs.getBoolean("attnotnull")),
            relation);
    List<String> primaryKey =
        jdbc.queryForList(
            """
            SELECT a.attname
            FROM pg_constraint con
            JOIN LATERAL unnest(con.conkey) WITH ORDINALITY AS k(attnum, ord) ON true
            JOIN pg_attribute a ON a.attrelid = con.conrelid AND a.attnum = k.attnum
            WHERE con.conrelid = ?::regclass AND con.contype = 'p'
            ORDER BY k.ord
            """,
            String.class,
            relation);
    List<String> partitionKey =
        jdbc.queryForList(
            """
            SELECT a.attname
            FROM pg_partitioned_table p
            JOIN LATERAL unnest(p.partattrs::int2[]) WITH ORDINALITY AS k(attnum, ord) ON true
            JOIN pg_attribute a ON a.attrelid = p.partrelid AND a.attnum = k.attnum
            WHERE p.partrelid = ?::regclass
            ORDER BY k.ord
            """,
            String.class,
            relation);
    List<Constraint> constraints =
        jdbc.query(
            "SELECT conname, contype FROM pg_constraint"
                + " WHERE conrelid = ?::regclass AND contype <> 'p' ORDER BY conname",
            (rs, rowNum) -> new Constraint(rs.getString("conname"), rs.getString("contype")),
            relation);
    List<String> auditTriggers =
        jdbc.queryForList(
            """
            SELECT pg_get_triggerdef(t.oid)
            FROM pg_trigger t JOIN pg_proc p ON p.oid = t.tgfoid
            WHERE t.tgrelid = ?::regclass AND NOT t.tgisinternal AND p.proname = 'set_audit_times'
            """,
            String.class,
            relation);
    return new TableShape(table, columns, primaryKey, partitionKey, constraints, auditTriggers);
  }
}
