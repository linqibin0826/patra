package dev.patra.tool.launch;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// JarConfigOverlapChecker 单元测试：同一个键不在 jar 和 Nacos 两处写。
@DisplayName("JarConfigOverlapChecker 单元测试")
class JarConfigOverlapCheckerTest {

  @TempDir Path root;

  @Test
  @DisplayName("jar 和本服务的 Nacos 配置写了同一个键：报出文件和键")
  void should_report_key_written_in_jar_and_service_file() throws IOException {
    write("nacos/patra-common.yaml", "patra.config-source.common: patra-common.yaml\n");
    write(
        "nacos/patra-catalog.yaml",
        "patra.config-source.service: patra-catalog.yaml\nspring.datasource.url: jdbc:x\n");
    write(
        "api/patra-catalog/patra-catalog-boot/src/main/resources/application.yml",
        "spring:\n  datasource:\n    url: jdbc:y\n");

    assertThat(check())
        .containsExactly(
            "patra-catalog-boot 的 application.yml 与 Nacos 的 patra-catalog.yaml 都写了"
                + " spring.datasource.url");
  }

  @Test
  @DisplayName("jar 和公共配置 patra-common.yaml 写了同一个键：同样报出")
  void should_report_key_written_in_jar_and_common_file() throws IOException {
    write(
        "nacos/patra-common.yaml",
        "patra.config-source.common: patra-common.yaml\nxxl.job.admin.addresses: http://x\n");
    write("nacos/patra-catalog.yaml", "patra.config-source.service: patra-catalog.yaml\n");
    write(
        "api/patra-catalog/patra-catalog-boot/src/main/resources/application.yml",
        "xxl:\n  job:\n    admin:\n      addresses: http://y\n");

    assertThat(check())
        .containsExactly(
            "patra-catalog-boot 的 application.yml 与 Nacos 的 patra-common.yaml 都写了"
                + " xxl.job.admin.addresses");
  }

  @Test
  @DisplayName("一处写成列表、一处写成单值也算同一个键")
  void should_report_list_form_of_same_key() throws IOException {
    write(
        "nacos/patra-common.yaml",
        "patra.config-source.common: patra-common.yaml\nxxl.job.admin.addresses: http://x\n");
    write("nacos/patra-catalog.yaml", "patra.config-source.service: patra-catalog.yaml\n");
    write(
        "api/patra-catalog/patra-catalog-boot/src/main/resources/application.yml",
        "xxl.job.admin.addresses:\n  - http://y\n");

    assertThat(check())
        .containsExactly(
            "patra-catalog-boot 的 application.yml 与 Nacos 的 patra-common.yaml 都写了"
                + " xxl.job.admin.addresses[0]");
  }

  @Test
  @DisplayName("找不到服务的 boot 模块时报出，不悄悄跳过")
  void should_report_missing_boot_module() throws IOException {
    write("nacos/patra-common.yaml", "patra.config-source.common: patra-common.yaml\n");
    write("nacos/patra-catalog.yaml", "patra.config-source.service: patra-catalog.yaml\n");

    assertThat(check())
        .containsExactly(
            "找不到 patra-catalog 的 boot 模块配置目录：patra-catalog/patra-catalog-boot/src/main/resources"
                + " 或 patra-catalog-boot/src/main/resources");
  }

  @Test
  @DisplayName("boot 模块直接放在 patra-api 下也能找到（如 patra-gateway-boot），本服务的其他 yml 一起查")
  void should_find_top_level_boot_module_and_check_every_yaml() throws IOException {
    write("nacos/patra-common.yaml", "patra.config-source.common: patra-common.yaml\n");
    write(
        "nacos/patra-gateway.yaml",
        "patra.config-source.service: patra-gateway.yaml\nspring.data.redis.host: x\n");
    write("api/patra-gateway-boot/src/main/resources/application.yml", "server.port: 9528\n");
    write(
        "api/patra-gateway-boot/src/main/resources/gateway-extra.yaml",
        "spring.data.redis.host: y\n");

    assertThat(check())
        .containsExactly(
            "patra-gateway-boot 的 gateway-extra.yaml 与 Nacos 的 patra-gateway.yaml 都写了"
                + " spring.data.redis.host");
  }

  @Test
  @DisplayName("仓库里 7 个服务的 jar 配置与 patra-infra/nacos/dev 没有重复的键")
  void should_pass_for_repository() {
    String repoRoot =
        Objects.requireNonNull(
            System.getProperty("patra.repo-root"), "缺系统属性 patra.repo-root（由 Gradle 传入）");

    assertThat(
            JarConfigOverlapChecker.check(
                Path.of(repoRoot, "patra-infra", "nacos", "dev"), Path.of(repoRoot, "patra-api")))
        .isEmpty();
  }

  /// 检查临时目录下的 `nacos/` 与 `api/`。
  ///
  /// @return 违规说明
  private List<String> check() {
    return JarConfigOverlapChecker.check(root.resolve("nacos"), root.resolve("api"));
  }

  /// 在临时目录里写一份 UTF-8 文件，按需建父目录。
  ///
  /// @param relative 相对临时目录的路径
  /// @param content 文件内容
  /// @throws IOException 写入失败时
  private void write(String relative, String content) throws IOException {
    Path file = root.resolve(relative);
    Files.createDirectories(file.getParent());
    Files.writeString(file, content, StandardCharsets.UTF_8);
  }
}
