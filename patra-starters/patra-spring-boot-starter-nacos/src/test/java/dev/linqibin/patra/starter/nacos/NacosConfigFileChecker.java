package dev.linqibin.patra.starter.nacos;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.MarkedYAMLException;

/// 检查 `patra-infra/nacos/<namespace>/` 下要推送到 Nacos 的配置文件（spec 第 4 节）。
///
/// 只在测试里用：CI 经 [NacosConfigFilesTest] 检查仓库里的真实目录，坏配置合不进 main。
final class NacosConfigFileChecker {

  /// 公共配置的 dataId。
  static final String COMMON_DATA_ID = "patra-common.yaml";

  /// 敏感键名的结尾（去掉 `-`、`_` 并转小写后比较）。
  private static final List<String> SENSITIVE_SUFFIXES =
      List.of(
          "password",
          "passwd",
          "secret",
          "token",
          "credential",
          "credentials",
          "privatekey",
          "apikey",
          "accesskey",
          "secretkey");

  /// 不实例化。
  private NacosConfigFileChecker() {}

  /// 检查目录下所有 `*.yaml`。
  ///
  /// @param directory 配置目录，目录名即 namespace
  /// @return 违反规则的说明，每条以文件名开头；全部通过时为空
  static List<String> check(Path directory) {
    List<String> violations = new ArrayList<>();
    if (!Files.isRegularFile(directory.resolve(COMMON_DATA_ID))) {
      violations.add("缺少 " + COMMON_DATA_ID);
    }
    for (Path file : listYamlFiles(directory)) {
      violations.addAll(checkFile(directory.relativize(file).toString(), file));
    }
    return violations;
  }

  /// 检查一份文件。
  ///
  /// @param name 文件名（即 dataId）
  /// @param file 文件路径
  /// @return 这份文件的违规说明
  private static List<String> checkFile(String name, Path file) {
    String text = read(file);
    if (text.indexOf('\r') >= 0) {
      return List.of(name + "：换行符必须是 LF，不能有 CR（\\r）");
    }
    Object root;
    try {
      root = newYaml().load(text);
    } catch (MarkedYAMLException e) {
      return List.of(name + "：YAML 解析失败，第 " + line(e) + " 行：" + e.getProblem());
    }
    if (!(root instanceof Map<?, ?> map)) {
      return List.of(name + "：顶层必须是键值对（map）");
    }
    List<Leaf> leaves = new ArrayList<>();
    flatten("", map, leaves);
    List<String> violations = new ArrayList<>();
    Map<String, Object> flat = new LinkedHashMap<>();
    for (Leaf leaf : leaves) {
      // `a.b: 1` 与 `a: {b: 2}` 是两个 YAML 键，SnakeYAML 不报重复，Spring 却当成同一个属性
      if (flat.containsKey(leaf.path())) {
        violations.add(name + "：重复键 " + leaf.path() + "（扁平与嵌套写法指向同一个属性）");
      }
      flat.put(leaf.path(), leaf.value());
    }
    String property = signatureProperty(name);
    if (!name.equals(flat.get(property))) {
      violations.add(name + "：签到 " + property + " 的值必须是 " + name);
    }
    String line = property + ": " + name;
    if (text.lines().noneMatch(line::equals)) {
      violations.add(name + "：签到必须写成一行扁平键「" + line + "」");
    }
    // 逐个叶子检查，不看覆盖后的结果：被覆盖的字面量照样会进仓库
    for (Leaf leaf : leaves) {
      if (isSensitive(leaf.path()) && !isReference(leaf.value())) {
        violations.add(name + "：敏感键 " + leaf.path() + " 只能写成 ${变量} 或 ${变量:默认值}");
      }
    }
    return violations.stream().distinct().toList();
  }

  /// 键路径最后一段（去掉 `[n]`、`-`、`_` 并转小写后）是否以敏感词结尾。
  ///
  /// @param key 点分路径
  /// @return 是敏感键时为 `true`
  private static boolean isSensitive(String key) {
    String last = key.substring(key.lastIndexOf('.') + 1);
    String normalized =
        last.replaceAll("\\[\\d+]", "").replace("-", "").replace("_", "").toLowerCase(Locale.ROOT);
    return SENSITIVE_SUFFIXES.stream().anyMatch(normalized::endsWith);
  }

  /// 值是否没有泄露内容：`null`、空串，或 `${...}` 形式的引用。
  ///
  /// @param value 叶子值
  /// @return 可以提交时为 `true`
  private static boolean isReference(Object value) {
    if (value == null) {
      return true;
    }
    return value instanceof String text
        && (text.isEmpty() || (text.startsWith("${") && text.endsWith("}")));
  }

  /// 这份文件应写的签到属性：公共配置写 common，其余写 service。
  ///
  /// @param name 文件名
  /// @return 签到属性名
  private static String signatureProperty(String name) {
    return COMMON_DATA_ID.equals(name)
        ? ConfigSourceCheckPostProcessor.COMMON_PROPERTY
        : ConfigSourceCheckPostProcessor.SERVICE_PROPERTY;
  }

  /// 把嵌套的 map 和 list 压平成点分路径与叶子值，列表元素写成 `key[i]`。
  ///
  /// 按出现顺序保留全部叶子，路径相同的也不合并，供重复键与敏感值检查使用。
  ///
  /// @param prefix 当前路径，顶层为空串
  /// @param node 当前节点
  /// @param out 收集结果
  private static void flatten(String prefix, Object node, List<Leaf> out) {
    if (node instanceof Map<?, ?> map) {
      map.forEach(
          (key, value) ->
              flatten(prefix.isEmpty() ? String.valueOf(key) : prefix + "." + key, value, out));
    } else if (node instanceof List<?> list) {
      for (int i = 0; i < list.size(); i++) {
        flatten(prefix + "[" + i + "]", list.get(i), out);
      }
    } else {
      out.add(Leaf.of(prefix, node));
    }
  }

  /// 压平后的一个叶子。
  ///
  /// @param path 点分路径
  /// @param value 叶子值，可能为 `null`
  private record Leaf(String path, Object value) {

    /// 创建叶子。
    ///
    /// @param path 点分路径
    /// @param value 叶子值，可能为 `null`
    /// @return 叶子
    static Leaf of(String path, Object value) {
      return new Leaf(path, value);
    }
  }

  /// 出错位置的行号（从 1 开始）；没有位置信息时为 `?`。
  ///
  /// @param e 解析异常
  /// @return 行号
  private static String line(MarkedYAMLException e) {
    return e.getProblemMark() == null ? "?" : String.valueOf(e.getProblemMark().getLine() + 1);
  }

  /// 只构造基础类型、拒绝重复键的解析器。
  ///
  /// @return 新的解析器（`Yaml` 不是线程安全的，每次新建）
  private static Yaml newYaml() {
    LoaderOptions options = new LoaderOptions();
    options.setAllowDuplicateKeys(false);
    return new Yaml(new SafeConstructor(options));
  }

  /// 按 UTF-8 读出文件内容。
  ///
  /// @param file 文件
  /// @return 内容
  private static String read(Path file) {
    try {
      return Files.readString(file, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException("读取配置文件失败：" + file, e);
    }
  }

  /// 列出目录下以 `.yaml` 结尾的普通文件，按文件名排序。
  ///
  /// @param directory 配置目录
  /// @return 排好序的文件
  private static List<Path> listYamlFiles(Path directory) {
    try (Stream<Path> files = Files.list(directory)) {
      return files
          .filter(Files::isRegularFile)
          .filter(file -> file.toString().endsWith(".yaml"))
          .sorted()
          .toList();
    } catch (IOException e) {
      throw new UncheckedIOException("列出配置目录失败：" + directory, e);
    }
  }
}
