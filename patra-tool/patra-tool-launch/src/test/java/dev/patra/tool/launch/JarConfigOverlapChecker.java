package dev.patra.tool.launch;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.FileSystemResource;

/// 检查同一个键没有同时写在 jar 和 Nacos 里。
///
/// 两处都写时，哪个生效取决于导入顺序：改了 Nacos 可能不生效，排查时也说不清以哪处为准。只在测试里用：
/// [JarConfigOverlapCheckerTest] 检查仓库里 `patra-infra/nacos/dev/` 与 6 个 boot 模块的配置。
final class JarConfigOverlapChecker {

  /// boot 模块里 jar 配置所在目录。
  private static final String RESOURCES = "src/main/resources";

  /// 不实例化。
  private JarConfigOverlapChecker() {}

  /// 对 Nacos 目录下每份服务配置，找到对应 boot 模块 `src/main/resources` 下的全部 yml，比对键。
  ///
  /// boot 模块按 `{服务名}/{服务名}-boot` 找，找不到再找 `{服务名}-boot`（如网关）。
  ///
  /// @param nacosDirectory Nacos 配置目录（含 `patra-common.yaml`）
  /// @param apiDirectory `patra-api` 目录
  /// @return 违规说明；全部通过时为空
  static List<String> check(Path nacosDirectory, Path apiDirectory) {
    Set<String> commonKeys = keys(nacosDirectory.resolve(NacosConfigFileChecker.COMMON_DATA_ID));
    List<String> violations = new ArrayList<>();
    for (Path serviceFile : yamlFiles(nacosDirectory)) {
      String dataId = serviceFile.getFileName().toString();
      if (dataId.equals(NacosConfigFileChecker.COMMON_DATA_ID)) {
        continue;
      }
      String service = dataId.substring(0, dataId.length() - ".yaml".length());
      Map<String, Set<String>> nacosKeys = new LinkedHashMap<>();
      nacosKeys.put(dataId, keys(serviceFile));
      nacosKeys.put(NacosConfigFileChecker.COMMON_DATA_ID, commonKeys);
      String nested = service + "/" + service + "-boot/" + RESOURCES;
      String topLevel = service + "-boot/" + RESOURCES;
      Path resources =
          Stream.of(nested, topLevel)
              .map(apiDirectory::resolve)
              .filter(Files::isDirectory)
              .findFirst()
              .orElse(null);
      if (resources == null) {
        violations.add("找不到 " + service + " 的 boot 模块配置目录：" + nested + " 或 " + topLevel);
        continue;
      }
      for (Path jarFile : yamlFiles(resources)) {
        for (String jarKey : keys(jarFile)) {
          nacosKeys.forEach(
              (nacosFile, names) -> {
                if (names.stream().anyMatch(name -> sameProperty(jarKey, name))) {
                  violations.add(
                      service
                          + "-boot 的 "
                          + jarFile.getFileName()
                          + " 与 Nacos 的 "
                          + nacosFile
                          + " 都写了 "
                          + jarKey);
                }
              });
        }
      }
    }
    return violations;
  }

  /// 两个属性名是否落在同一个属性上：相同，或一个是另一个的上层（后面接 `.` 或 `[`），例如
  /// `a.b` 与 `a.b[0]`、`a.b.c`。
  ///
  /// @param first 一个属性名
  /// @param second 另一个属性名
  /// @return 落在同一个属性上时为 `true`
  private static boolean sameProperty(String first, String second) {
    return first.equals(second) || isParent(first, second) || isParent(second, first);
  }

  /// `parent` 是否是 `child` 的上层。
  ///
  /// @param parent 可能的上层属性名
  /// @param child 可能的下层属性名
  /// @return 是上层时为 `true`
  private static boolean isParent(String parent, String child) {
    return child.startsWith(parent + ".") || child.startsWith(parent + "[");
  }

  /// 读出一份 YAML 的全部属性名（Spring 的压平规则，列表元素为 `key[i]`）。
  ///
  /// @param file YAML 文件
  /// @return 属性名，按出现顺序
  private static Set<String> keys(Path file) {
    try {
      Set<String> names = new LinkedHashSet<>();
      for (PropertySource<?> source :
          new YamlPropertySourceLoader().load(file.toString(), new FileSystemResource(file))) {
        names.addAll(List.of(((EnumerablePropertySource<?>) source).getPropertyNames()));
      }
      return names;
    } catch (IOException e) {
      throw new UncheckedIOException("读取配置文件失败：" + file, e);
    }
  }

  /// 列出目录下的 `*.yml`、`*.yaml` 普通文件（不进子目录），按文件名排序；目录不存在时为空。
  ///
  /// @param directory 目录
  /// @return 排好序的文件
  private static List<Path> yamlFiles(Path directory) {
    if (!Files.isDirectory(directory)) {
      return List.of();
    }
    try (Stream<Path> files = Files.list(directory)) {
      return files
          .filter(Files::isRegularFile)
          .filter(file -> file.toString().endsWith(".yml") || file.toString().endsWith(".yaml"))
          .sorted()
          .toList();
    } catch (IOException e) {
      throw new UncheckedIOException("列出目录失败：" + directory, e);
    }
  }
}
