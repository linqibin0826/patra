package dev.patra.tool.test.schema;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.core.io.ClassPathResource;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/// 一个服务的表登记文件 `db/tables.yml`：服务前缀、每张业务表的档位和说明、按框架默认名保留的表。
///
/// 结构检查拿它和库里的真实表对照：库里有、登记里没有的表，或登记了、库里没有的表，都算违规。
///
/// @param prefix 服务前缀，不带下划线，如 `itr`
/// @param tables 业务表，键是表名
/// @param framework 框架表的表名，如 `flyway_schema_history`；只查是否在 `public`，不查结构
public record TableRegistry(String prefix, Map<String, TableEntry> tables, Set<String> framework) {

  /// 防御性拷贝。
  public TableRegistry {
    tables = Map.copyOf(tables);
    framework = Set.copyOf(framework);
  }

  /// 一张业务表的登记项。
  ///
  /// @param tier 档位
  /// @param note 这张表是什么；拿不准档位的表顺便写为什么这样归
  public record TableEntry(TableTier tier, String note) {}

  /// 从类路径读登记文件。
  ///
  /// @param resource 类路径资源名，如 `db/tables.yml`
  /// @return 登记内容
  /// @throws IllegalArgumentException 文件缺少前缀或表、表缺少档位或说明、档位写错
  public static TableRegistry load(String resource) {
    Map<String, Object> root;
    try (InputStream in = new ClassPathResource(resource).getInputStream()) {
      root = new Yaml(new SafeConstructor(new LoaderOptions())).load(in);
    } catch (IOException e) {
      throw new UncheckedIOException("读不到表登记文件 " + resource, e);
    }
    if (root == null || !(root.get("prefix") instanceof String prefix) || prefix.isBlank()) {
      throw new IllegalArgumentException(resource + " 缺少 prefix");
    }
    if (!(root.get("tables") instanceof Map<?, ?> rawTables) || rawTables.isEmpty()) {
      throw new IllegalArgumentException(resource + " 缺少 tables");
    }
    Map<String, TableEntry> tables = new LinkedHashMap<>();
    rawTables.forEach((name, value) -> tables.put(name.toString(), entry(resource, name, value)));
    List<?> framework = root.get("framework") instanceof List<?> list ? list : List.of();
    return new TableRegistry(
        prefix, tables, Set.copyOf(framework.stream().map(Object::toString).toList()));
  }

  private static TableEntry entry(String resource, Object table, Object value) {
    if (!(value instanceof Map<?, ?> fields)
        || !(fields.get("tier") instanceof String tier)
        || !(fields.get("note") instanceof String note)
        || note.isBlank()) {
      throw new IllegalArgumentException(resource + " 里的 " + table + " 要写 tier 和 note");
    }
    try {
      return new TableEntry(TableTier.valueOf(tier.toUpperCase(Locale.ROOT)), note);
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException(
          resource + " 里的 " + table + " 档位只能是 main 或 other，写的是 " + tier, e);
    }
  }
}
