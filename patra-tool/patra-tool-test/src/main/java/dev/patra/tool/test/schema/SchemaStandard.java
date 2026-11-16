package dev.patra.tool.test.schema;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;

/// 库表规范的结构检查：迁移跑完后，查 PostgreSQL 系统目录里的真实表结构，对照 `.claude/rules/tech/database.md`
/// 和服务的表登记文件，列出所有违规。
///
/// 查的是迁移后的最终结构而不是脚本文本：后面的 `ALTER`、`DROP` 会改变前面脚本建出的东西，只有真实结构说了算。
/// 一次列出全部违规，不在第一条就停，方便重建一个服务时对着清单改。框架表（Flyway 历史表、事件登记表等）
/// 结构由框架决定，只查它在 `public` 且已登记。
///
/// 各服务在 infra 的集成测试里跑完 Flyway 后调用：
///
/// ```java
/// assertThat(SchemaStandard.inspect(dataSource, "db/tables.yml").violations()).isEmpty();
/// ```
public final class SchemaStandard {

  /// 用户建的 schema：排除 PostgreSQL 自带的 `pg_*` 与 `information_schema`。
  private static final String USER_SCHEMAS_SQL =
      "SELECT nspname FROM pg_namespace"
          + " WHERE nspname NOT LIKE 'pg\\_%' AND nspname <> 'information_schema'"
          + " ORDER BY nspname";

  /// `public` 里的普通表和分区父表；分区子表跟着父表走，不单独登记。
  private static final String PUBLIC_TABLES_SQL =
      "SELECT c.relname FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace"
          + " WHERE n.nspname = 'public' AND c.relkind IN ('r', 'p') AND NOT c.relispartition";

  /// 主表在 `id`、两个审计时间之外必备的四列和它们的类型。
  private static final Map<String, String> MAIN_COLUMNS =
      Map.of(
          "created_by", "bigint",
          "updated_by", "bigint",
          "ip_address", "inet",
          "record_remarks", "jsonb");

  /// 只放主表、其他表不该有的列；`version` 只用于主表的乐观锁。
  private static final List<String> MAIN_ONLY_COLUMNS =
      List.of("created_by", "updated_by", "ip_address", "record_remarks", "version");

  /// 用户名只存 identity 的这两张表，别处引用用户一律存 `user_id`。
  private static final Set<String> USERNAME_TABLES = Set.of("idn_user", "idn_user_former_username");

  private static final Set<String> USERNAME_COLUMNS = Set.of("username", "user_name");

  private SchemaStandard() {}

  /// 检查一个库。
  ///
  /// @param dataSource 已跑完迁移的库
  /// @param registryResource 表登记文件的类路径资源名，服务里传 `db/tables.yml`
  /// @return 检查结果
  public static SchemaStandardReport inspect(DataSource dataSource, String registryResource) {
    TableRegistry registry = TableRegistry.load(registryResource);
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    Set<String> tables = new TreeSet<>(jdbc.queryForList(PUBLIC_TABLES_SQL, String.class));

    List<SchemaViolation> violations = new ArrayList<>();
    violations.addAll(extraSchemas(jdbc));
    violations.addAll(registration(registry, tables));
    violations.addAll(prefixes(registry));
    for (String table : tables) {
      TableRegistry.TableEntry entry = registry.tables().get(table);
      if (entry == null) {
        continue; // 未登记的表上面已报；框架表不查结构
      }
      TableShape shape = TableShape.load(jdbc, table);
      violations.addAll(primaryKey(shape));
      violations.addAll(relationConstraints(shape));
      violations.addAll(auditColumns(shape, entry.tier()));
      violations.addAll(auditTrigger(shape));
      violations.addAll(userNameColumns(shape));
      violations.addAll(timeColumns(shape));
    }
    return new SchemaStandardReport(violations);
  }

  /// §2：主键是 `id BIGINT`；分区表是分区键列加 `id`（PostgreSQL 要求分区表主键含分区键）。
  private static List<SchemaViolation> primaryKey(TableShape shape) {
    List<String> expected = new ArrayList<>(shape.partitionKey());
    expected.add("id");
    List<SchemaViolation> violations = new ArrayList<>();
    if (!shape.primaryKey().equals(expected)) {
      String actual = shape.primaryKey().isEmpty() ? "没有主键" : "主键是 " + columns(shape.primaryKey());
      violations.add(new SchemaViolation("§2", shape.name(), actual + "，应为 " + columns(expected)));
    }
    shape
        .column("id")
        .filter(id -> !id.type().equals("bigint"))
        .ifPresent(
            id ->
                violations.add(
                    new SchemaViolation(
                        "§2", shape.name(), "id 的类型是 " + id.type() + "，应为 bigint（雪花 ID）")));
    return violations;
  }

  /// §2：不建外键；自然键用唯一索引，不用唯一约束（唯一索引以后能直接提升为主键）。
  private static List<SchemaViolation> relationConstraints(TableShape shape) {
    List<SchemaViolation> violations = new ArrayList<>();
    for (TableShape.Constraint constraint : shape.constraints()) {
      if (constraint.type().equals("f")) {
        violations.add(
            new SchemaViolation(
                "§2", shape.name(), "有外键 " + constraint.name() + "，引用关系由应用保证，不建外键"));
      } else if (constraint.type().equals("u")) {
        violations.add(
            new SchemaViolation(
                "§2", shape.name(), "唯一约束 " + constraint.name() + " 要改成唯一索引（CREATE UNIQUE INDEX）"));
      }
    }
    return violations;
  }

  /// §3：每张表有两个审计时间；主表另有四列，其他表不能有主表才有的列。
  private static List<SchemaViolation> auditColumns(TableShape shape, TableTier tier) {
    List<SchemaViolation> violations = new ArrayList<>();
    for (String name : List.of("created_at", "updated_at")) {
      shape
          .column(name)
          .ifPresentOrElse(
              column -> {
                if (!isTimestamptz(column.type()) || !column.notNull()) {
                  violations.add(
                      new SchemaViolation(
                          "§3",
                          shape.name(),
                          name + " 应为 TIMESTAMPTZ NOT NULL，现在是 " + column.type()));
                }
              },
              () -> violations.add(new SchemaViolation("§3", shape.name(), "缺少 " + name)));
    }
    if (tier == TableTier.MAIN) {
      new TreeSet<>(MAIN_COLUMNS.keySet())
          .forEach(
              name ->
                  shape
                      .column(name)
                      .ifPresentOrElse(
                          column -> {
                            if (!column.type().equals(MAIN_COLUMNS.get(name))) {
                              violations.add(
                                  new SchemaViolation(
                                      "§3",
                                      shape.name(),
                                      name
                                          + " 的类型是 "
                                          + column.type()
                                          + "，应为 "
                                          + MAIN_COLUMNS.get(name)));
                            }
                          },
                          () ->
                              violations.add(
                                  new SchemaViolation("§3", shape.name(), "主表缺少 " + name))));
    } else {
      MAIN_ONLY_COLUMNS.stream()
          .filter(name -> shape.column(name).isPresent())
          .forEach(
              name ->
                  violations.add(
                      new SchemaViolation("§3", shape.name(), "其他表不该有 " + name + "，它只放主表")));
    }
    return violations;
  }

  /// §3：审计时间由 `set_audit_times()` 触发器维护，插入和更新都要触发、逐行执行。
  private static List<SchemaViolation> auditTrigger(TableShape shape) {
    boolean present =
        shape.auditTriggers().stream()
            .anyMatch(
                def -> def.contains("BEFORE INSERT OR UPDATE") && def.contains("FOR EACH ROW"));
    return present
        ? List.of()
        : List.of(
            new SchemaViolation(
                "§3",
                shape.name(),
                "没有挂 set_audit_times() 触发器（BEFORE INSERT OR UPDATE … FOR EACH ROW）"));
  }

  /// §3：用户名只存 identity 的两张用户名表；任何表都不冗余操作人名字。
  private static List<SchemaViolation> userNameColumns(TableShape shape) {
    return shape.columns().stream()
        .map(TableShape.Column::name)
        .filter(
            name ->
                name.endsWith("_by_name")
                    || (USERNAME_COLUMNS.contains(name) && !USERNAME_TABLES.contains(shape.name())))
        .map(
            name ->
                new SchemaViolation(
                    "§3", shape.name(), "不能有用户名列 " + name + "，引用用户存 user_id，展示时按 ID 现读"))
        .toList();
  }

  /// §4：时间一律 `TIMESTAMPTZ`。
  private static List<SchemaViolation> timeColumns(TableShape shape) {
    return shape.columns().stream()
        .filter(column -> column.type().matches("timestamp(\\(\\d\\))? without time zone"))
        .map(
            column ->
                new SchemaViolation("§4", shape.name(), column.name() + " 不带时区，应为 TIMESTAMPTZ"))
        .toList();
  }

  /// `format_type` 会带精度，如 `timestamp(6) with time zone`。
  private static boolean isTimestamptz(String type) {
    return type.matches("timestamp(\\(\\d\\))? with time zone");
  }

  private static String columns(List<String> names) {
    return "(" + String.join(", ", names) + ")";
  }

  /// §1：除 `public` 外不许有用户建的 schema。
  private static List<SchemaViolation> extraSchemas(JdbcTemplate jdbc) {
    return jdbc.queryForList(USER_SCHEMAS_SQL, String.class).stream()
        .filter(schema -> !schema.equals("public"))
        .map(schema -> new SchemaViolation("§1", schema, "业务表都放 public，不能建别的 schema"))
        .toList();
  }

  /// §1：库里的表和登记文件一一对应。
  private static List<SchemaViolation> registration(TableRegistry registry, Set<String> tables) {
    List<SchemaViolation> violations = new ArrayList<>();
    for (String table : tables) {
      if (!registry.tables().containsKey(table) && !registry.framework().contains(table)) {
        violations.add(new SchemaViolation("§1", table, "库里有这张表，但没在表登记文件里登记"));
      }
    }
    Set<String> registered = new TreeSet<>(registry.tables().keySet());
    registered.addAll(registry.framework());
    for (String table : registered) {
      if (!tables.contains(table)) {
        violations.add(new SchemaViolation("§1", table, "登记了这张表，但库里没有"));
      }
    }
    return violations;
  }

  /// §1：业务表名以服务前缀开头。
  private static List<SchemaViolation> prefixes(TableRegistry registry) {
    String expected = registry.prefix() + "_";
    return new TreeSet<>(registry.tables().keySet())
        .stream()
            .filter(table -> !table.startsWith(expected))
            .map(table -> new SchemaViolation("§1", table, "表名要以 " + expected + " 开头"))
            .toList();
  }
}
