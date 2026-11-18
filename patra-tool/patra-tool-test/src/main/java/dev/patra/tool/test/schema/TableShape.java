package dev.patra.tool.test.schema;

import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;

/// 一张表在系统目录里的样子，从 `pg_catalog` 一次读出，供 `SchemaStandard` 的各条规则使用。
///
/// @param name 表名
/// @param comment 表注释，没有时为 `null`
/// @param columns 列，按定义顺序
/// @param primaryKey 主键列，按主键里的顺序；没有主键时为空
/// @param partitionKey 分区键列；不是分区表时为空
/// @param constraints 主键以外的约束
/// @param indexes 不属于任何约束的索引（主键、唯一约束背后的索引不在内）
/// @param auditTriggers 调用 `set_audit_times()` 的触发器定义（`pg_get_triggerdef` 的输出）
record TableShape(
    String name,
    String comment,
    List<Column> columns,
    List<String> primaryKey,
    List<String> partitionKey,
    List<Constraint> constraints,
    List<Index> indexes,
    List<String> auditTriggers) {

  /// 防御性拷贝。
  TableShape {
    columns = List.copyOf(columns);
    primaryKey = List.copyOf(primaryKey);
    partitionKey = List.copyOf(partitionKey);
    constraints = List.copyOf(constraints);
    indexes = List.copyOf(indexes);
    auditTriggers = List.copyOf(auditTriggers);
  }

  /// 一列。
  ///
  /// @param name 列名
  /// @param type `format_type` 给出的类型，如 `timestamp with time zone`
  /// @param notNull 是否 `NOT NULL`
  /// @param comment 列注释，没有时为 `null`
  record Column(String name, String type, boolean notNull, String comment) {}

  /// 一个约束。
  ///
  /// @param name 约束名
  /// @param type `pg_constraint.contype`：`f` 外键、`u` 唯一、`c` 检查、`x` 排他
  record Constraint(String name, String type) {}

  /// 一个索引。
  ///
  /// @param name 索引名
  /// @param unique 是否唯一索引
  /// @param valid 是否有效；`CREATE INDEX CONCURRENTLY` 中途失败会留下无效索引
  record Index(String name, boolean unique, boolean valid) {}

  /// 按名字取列。
  Optional<Column> column(String columnName) {
    return columns.stream().filter(column -> column.name().equals(columnName)).findFirst();
  }

  /// 读 `public` 里的一张表。
  static TableShape load(JdbcTemplate jdbc, String table) {
    String relation = "public." + table;
    String comment =
        jdbc.queryForObject(
            "SELECT obj_description(?::regclass, 'pg_class')", String.class, relation);
    List<Column> columns =
        jdbc.query(
            """
            SELECT a.attname, format_type(a.atttypid, a.atttypmod) AS type, a.attnotnull,
                   col_description(a.attrelid, a.attnum) AS comment
            FROM pg_attribute a
            WHERE a.attrelid = ?::regclass AND a.attnum > 0 AND NOT a.attisdropped
            ORDER BY a.attnum
            """,
            (rs, rowNum) ->
                new Column(
                    rs.getString("attname"),
                    rs.getString("type"),
                    rs.getBoolean("attnotnull"),
                    rs.getString("comment")),
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
    List<Index> indexes =
        jdbc.query(
            """
            SELECT ic.relname, i.indisunique, i.indisvalid
            FROM pg_index i JOIN pg_class ic ON ic.oid = i.indexrelid
            WHERE i.indrelid = ?::regclass
              AND NOT EXISTS (
                  SELECT 1 FROM pg_constraint c
                  WHERE c.conrelid = i.indrelid AND c.conindid = i.indexrelid)
            ORDER BY ic.relname
            """,
            (rs, rowNum) ->
                new Index(
                    rs.getString("relname"),
                    rs.getBoolean("indisunique"),
                    rs.getBoolean("indisvalid")),
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
    return new TableShape(
        table, comment, columns, primaryKey, partitionKey, constraints, indexes, auditTriggers);
  }
}
