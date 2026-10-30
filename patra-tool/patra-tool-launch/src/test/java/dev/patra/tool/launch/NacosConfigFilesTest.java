package dev.patra.tool.launch;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// NacosConfigFileChecker 单元测试：要推送到 Nacos 的配置文件的格式与安全检查（spec 第 4 节）。
@DisplayName("NacosConfigFileChecker 单元测试")
class NacosConfigFilesTest {

  @TempDir Path directory;

  @Test
  @DisplayName("签到正确、敏感键只写引用的目录通过")
  void should_pass_when_files_follow_rules() throws IOException {
    write("patra-common.yaml", "patra.config-source.common: patra-common.yaml\n");
    write(
        "patra-catalog.yaml",
        """
        patra.config-source.service: patra-catalog.yaml
        spring:
          datasource:
            password: ${INGEST_DB_PASSWORD}
        """);

    assertThat(NacosConfigFileChecker.check(directory)).isEmpty();
  }

  @Test
  @DisplayName("YAML 解析失败时不通过，报出行号")
  void should_report_parse_error() throws IOException {
    write("patra-common.yaml", "patra.config-source.common: patra-common.yaml\nbad: [\n");

    assertThat(NacosConfigFileChecker.check(directory))
        .singleElement()
        .asString()
        .startsWith("patra-common.yaml：YAML 解析失败");
  }

  @Test
  @DisplayName("重复键不通过：Spring 会静默取后一个")
  void should_reject_duplicate_keys() throws IOException {
    write("patra-common.yaml", "patra.config-source.common: patra-common.yaml\na: 1\na: 2\n");

    assertThat(NacosConfigFileChecker.check(directory))
        .singleElement()
        .asString()
        .contains("YAML 解析失败", "duplicate key");
  }

  @Test
  @DisplayName("同一个键扁平、嵌套各写一次时不通过，被覆盖的字面量敏感值也报出")
  void should_reject_same_key_in_flat_and_nested_form() throws IOException {
    write(
        "patra-common.yaml",
        """
        patra.config-source.common: patra-common.yaml
        spring:
          datasource:
            password: hunter2
        spring.datasource.password: ${DB_PASSWORD}
        """);

    assertThat(NacosConfigFileChecker.check(directory))
        .containsExactlyInAnyOrder(
            "patra-common.yaml：重复键 spring.datasource.password（扁平与嵌套写法指向同一个属性）",
            "patra-common.yaml：敏感键 spring.datasource.password 只能写成 ${变量} 或 ${变量:默认值}");
  }

  @Test
  @DisplayName("顶层不是 map 时不通过")
  void should_reject_non_map_root() throws IOException {
    write("patra-common.yaml", "- a\n- b\n");

    assertThat(NacosConfigFileChecker.check(directory))
        .containsExactly("patra-common.yaml：顶层必须是键值对（map）");
  }

  @Test
  @DisplayName("签到值与文件名不一致时不通过")
  void should_reject_signature_value_mismatch() throws IOException {
    write("patra-common.yaml", "patra.config-source.common: patra-common.yaml\n");
    write("patra-catalog.yaml", "patra.config-source.service: patra-identity.yaml\n");

    assertThat(NacosConfigFileChecker.check(directory))
        .contains("patra-catalog.yaml：签到 patra.config-source.service 的值必须是 patra-catalog.yaml");
  }

  @Test
  @DisplayName("公共配置用了 service 签到属性时不通过")
  void should_reject_common_file_with_service_signature() throws IOException {
    write("patra-common.yaml", "patra.config-source.service: patra-common.yaml\n");

    assertThat(NacosConfigFileChecker.check(directory))
        .contains("patra-common.yaml：签到 patra.config-source.common 的值必须是 patra-common.yaml");
  }

  @Test
  @DisplayName("签到写成嵌套形式时不通过：推送脚本只认一行扁平键")
  void should_reject_nested_signature() throws IOException {
    write(
        "patra-common.yaml",
        """
        patra:
          config-source:
            common: patra-common.yaml
        """);

    assertThat(NacosConfigFileChecker.check(directory))
        .containsExactly(
            "patra-common.yaml：签到必须写成一行扁平键「patra.config-source.common: patra-common.yaml」");
  }

  @Test
  @DisplayName("CRLF 换行不通过：推送脚本的签到匹配会失败")
  void should_reject_crlf_line_endings() throws IOException {
    write("patra-common.yaml", "patra.config-source.common: patra-common.yaml\r\na: 1\r\n");

    assertThat(NacosConfigFileChecker.check(directory))
        .containsExactly("patra-common.yaml：换行符必须是 LF，不能有 CR（\\r）");
  }

  @Test
  @DisplayName("敏感键写字面量时不通过，不打印值")
  void should_reject_literal_sensitive_value() throws IOException {
    write(
        "patra-common.yaml",
        """
        patra.config-source.common: patra-common.yaml
        spring:
          datasource:
            password: hunter2
        """);

    assertThat(NacosConfigFileChecker.check(directory))
        .containsExactly("patra-common.yaml：敏感键 spring.datasource.password 只能写成 ${变量} 或 ${变量:默认值}");
  }

  @Test
  @DisplayName("敏感键写 ${变量:默认值} 时通过")
  void should_accept_reference_with_default() throws IOException {
    write(
        "patra-common.yaml",
        "patra.config-source.common: patra-common.yaml\nredis:\n  password: ${REDIS_PASSWORD:dev}\n");

    assertThat(NacosConfigFileChecker.check(directory)).isEmpty();
  }

  @Test
  @DisplayName("数字字面量的敏感键也不通过")
  void should_reject_numeric_sensitive_value() throws IOException {
    write("patra-common.yaml", "patra.config-source.common: patra-common.yaml\napi-key: 123456\n");

    assertThat(NacosConfigFileChecker.check(directory))
        .containsExactly("patra-common.yaml：敏感键 api-key 只能写成 ${变量} 或 ${变量:默认值}");
  }

  @Test
  @DisplayName("代理密码 auth-pwd 写字面量时不通过")
  void should_reject_literal_auth_pwd() throws IOException {
    write("patra-common.yaml", "patra.config-source.common: patra-common.yaml\nauth-pwd: x\n");

    assertThat(NacosConfigFileChecker.check(directory))
        .containsExactly("patra-common.yaml：敏感键 auth-pwd 只能写成 ${变量} 或 ${变量:默认值}");
  }

  @Test
  @DisplayName("代理账号密钥 auth-key 写字面量时不通过")
  void should_reject_literal_auth_key() throws IOException {
    write("patra-common.yaml", "patra.config-source.common: patra-common.yaml\nauth-key: x\n");

    assertThat(NacosConfigFileChecker.check(directory))
        .containsExactly("patra-common.yaml：敏感键 auth-key 只能写成 ${变量} 或 ${变量:默认值}");
  }

  @Test
  @DisplayName("token-ttl 这类不以敏感词结尾的键不算敏感键")
  void should_not_treat_token_ttl_as_sensitive() throws IOException {
    write(
        "patra-common.yaml",
        "patra.config-source.common: patra-common.yaml\nsession:\n  token-ttl: 30m\n");

    assertThat(NacosConfigFileChecker.check(directory)).isEmpty();
  }

  @Test
  @DisplayName("敏感键为空值时通过：没有泄露内容")
  void should_accept_empty_sensitive_value() throws IOException {
    write(
        "patra-common.yaml",
        "patra.config-source.common: patra-common.yaml\nredis:\n  password:\n  secret: ''\n");

    assertThat(NacosConfigFileChecker.check(directory)).isEmpty();
  }

  @Test
  @DisplayName("缺公共配置时不通过")
  void should_require_common_file() throws IOException {
    write("patra-catalog.yaml", "patra.config-source.service: patra-catalog.yaml\n");

    assertThat(NacosConfigFileChecker.check(directory)).containsExactly("缺少 patra-common.yaml");
  }

  @Test
  @DisplayName("仓库里的 patra-infra/nacos/dev 全部通过")
  void should_pass_for_repository_directory() {
    String repoRoot =
        Objects.requireNonNull(
            System.getProperty("patra.repo-root"), "缺系统属性 patra.repo-root（由 Gradle 传入）");
    Path repositoryDirectory = Path.of(repoRoot, "patra-infra", "nacos", "dev");

    assertThat(repositoryDirectory).isDirectory();
    assertThat(NacosConfigFileChecker.check(repositoryDirectory)).isEmpty();
  }

  /// 在临时目录里写一份 UTF-8 文件。
  ///
  /// @param name 文件名
  /// @param content 文件内容
  /// @throws IOException 写入失败时
  private void write(String name, String content) throws IOException {
    Files.writeString(directory.resolve(name), content, StandardCharsets.UTF_8);
  }
}
