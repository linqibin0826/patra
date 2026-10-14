package dev.linqibin.patra.ingest;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.io.ClassPathResource;

/// jar 里配置文件的约束（PAP-77）：随环境变的值在 Nacos，jar 里没有 profile 文件，
/// 服务名由启动器传入，Nacos 接入由 starter 注入。jar 与 Nacos 不重复写同一个键由 starter-nacos 的
/// `JarConfigOverlapCheckerTest` 统一检查。
@DisplayName("jar 内配置文件约束")
class ApplicationConfigTest {

  @Test
  @DisplayName("classpath 上没有 profile 文件")
  void should_not_ship_profile_files() {
    assertThat(getClass().getResource("/application-dev.yml")).isNull();
    assertThat(getClass().getResource("/application-container.yml")).isNull();
  }

  @Test
  @DisplayName("application.yml 不写服务名、激活的 profile 和 Nacos 接入")
  void should_leave_name_profile_and_nacos_to_launcher_and_starter() throws IOException {
    assertThat(propertyNames("application.yml"))
        .noneMatch(
            name ->
                name.startsWith("spring.application.name")
                    || name.startsWith("spring.profiles.active")
                    || name.startsWith("spring.cloud.nacos"));
  }

  /// 读出 classpath 上一份 YAML 的全部属性名，多文档合在一起。
  ///
  /// @param resource classpath 上的文件名
  /// @return 扁平化后的属性名
  /// @throws IOException 读取失败时
  private static List<String> propertyNames(String resource) throws IOException {
    return new YamlPropertySourceLoader()
        .load(resource, new ClassPathResource(resource)).stream()
            .map(source -> (EnumerablePropertySource<?>) source)
            .flatMap(source -> Arrays.stream(source.getPropertyNames()))
            .toList();
  }
}
