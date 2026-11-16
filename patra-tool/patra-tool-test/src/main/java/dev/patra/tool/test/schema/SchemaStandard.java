package dev.patra.tool.test.schema;

import java.util.ArrayList;
import java.util.List;
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
    return new SchemaStandardReport(violations);
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
