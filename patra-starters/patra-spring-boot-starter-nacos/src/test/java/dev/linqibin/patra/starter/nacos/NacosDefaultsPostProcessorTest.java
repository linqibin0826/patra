package dev.linqibin.patra.starter.nacos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.core.env.StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME;

import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.boot.env.DefaultPropertiesPropertySource;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;

/// NacosDefaultsPostProcessor 单元测试：连接与服务发现的默认值、配置中心开关、本机密钥文件。
///
/// 每个用例都换掉真实的系统环境变量，并把 `user.home` 指到临时目录：开发机上的
/// `PATRA_INFRA_HOST`、`~/.patra/secrets` 不影响结果。
@DisplayName("NacosDefaultsPostProcessor 单元测试")
class NacosDefaultsPostProcessorTest {

  private final NacosDefaultsPostProcessor postProcessor = new NacosDefaultsPostProcessor();

  @TempDir private Path home;

  @Test
  @DisplayName("没设任何变量时，Nacos 地址是 127.0.0.1:8848")
  void should_default_server_addr_to_loopback_when_no_variable_set() {
    StandardEnvironment environment = process(environment(Map.of()));

    assertThat(environment.getProperty("spring.cloud.nacos.server-addr"))
        .isEqualTo("127.0.0.1:8848");
  }

  @Test
  @DisplayName("没设 PATRA_NACOS_ADDR 时，地址取 PATRA_INFRA_HOST:8848")
  void should_use_infra_host_when_nacos_addr_not_set() {
    StandardEnvironment environment = process(environment(Map.of("PATRA_INFRA_HOST", "10.0.0.5")));

    assertThat(environment.getProperty("spring.cloud.nacos.server-addr"))
        .isEqualTo("10.0.0.5:8848");
  }

  @Test
  @DisplayName("设了 PATRA_NACOS_ADDR 时，它优先于 PATRA_INFRA_HOST")
  void should_prefer_nacos_addr_over_infra_host() {
    StandardEnvironment environment =
        process(
            environment(
                Map.of("PATRA_INFRA_HOST", "10.0.0.5", "PATRA_NACOS_ADDR", "nacos.example:8848")));

    assertThat(environment.getProperty("spring.cloud.nacos.server-addr"))
        .isEqualTo("nacos.example:8848");
  }

  @Test
  @DisplayName("账号密码从 NACOS_USERNAME、NACOS_PASSWORD 读取")
  void should_read_credentials_from_environment_variables() {
    StandardEnvironment environment =
        process(environment(Map.of("NACOS_USERNAME", "patra-app", "NACOS_PASSWORD", "pw")));

    assertThat(environment.getProperty("spring.cloud.nacos.username")).isEqualTo("patra-app");
    assertThat(environment.getProperty("spring.cloud.nacos.password")).isEqualTo("pw");
  }

  @Test
  @DisplayName("密码不给默认值：没设 NACOS_PASSWORD 时解析失败")
  void should_fail_to_resolve_password_when_variable_missing() {
    StandardEnvironment environment = process(environment(Map.of()));

    assertThatThrownBy(() -> environment.getProperty("spring.cloud.nacos.password"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("NACOS_PASSWORD");
  }

  @Test
  @DisplayName("服务发现：service 取服务名，通告地址取 PATRA_ADVERTISE_IP，fail-fast 关闭")
  void should_configure_discovery_with_application_name_and_advertise_ip() {
    StandardEnvironment environment = environment(Map.of("PATRA_ADVERTISE_IP", "100.70.1.2"));
    environment
        .getPropertySources()
        .addLast(new MapPropertySource("app", Map.of("spring.application.name", "patra-registry")));

    process(environment);

    assertThat(environment.getProperty("spring.cloud.nacos.discovery.service"))
        .isEqualTo("patra-registry");
    assertThat(environment.getProperty("spring.cloud.nacos.discovery.ip")).isEqualTo("100.70.1.2");
    assertThat(environment.getProperty("spring.cloud.nacos.discovery.fail-fast"))
        .isEqualTo("false");
  }

  @Test
  @DisplayName("没设 PATRA_ADVERTISE_IP 时通告地址为空，由客户端自动探测")
  void should_leave_advertise_ip_empty_when_variable_missing() {
    StandardEnvironment environment = process(environment(Map.of()));

    assertThat(environment.getProperty("spring.cloud.nacos.discovery.ip")).isEmpty();
  }

  @Test
  @DisplayName("服务的配置能覆盖注入的默认值")
  void should_let_application_config_override_defaults() {
    StandardEnvironment environment = environment(Map.of());
    environment
        .getPropertySources()
        .addLast(
            new MapPropertySource(
                "applicationConfig", Map.of("spring.cloud.nacos.server-addr", "other:8848")));

    process(environment);

    assertThat(environment.getProperty("spring.cloud.nacos.server-addr")).isEqualTo("other:8848");
  }

  @Test
  @DisplayName("启动器已放进 default properties 的同名键不被覆盖，其余键照常补上")
  void should_keep_launcher_default_properties() {
    StandardEnvironment environment = environment(Map.of());
    environment
        .getPropertySources()
        .addLast(
            new DefaultPropertiesPropertySource(
                Map.of(
                    "spring.application.name", "patra-registry",
                    "spring.cloud.nacos.server-addr", "launcher:8848")));

    process(environment);

    assertThat(environment.getProperty("spring.cloud.nacos.server-addr"))
        .isEqualTo("launcher:8848");
    assertThat(environment.getProperty("spring.application.name")).isEqualTo("patra-registry");
    assertThat(
            environment
                .getPropertySources()
                .get(DefaultPropertiesPropertySource.NAME)
                .getProperty("spring.cloud.nacos.username"))
        .isEqualTo("${NACOS_USERNAME}");
  }

  @Test
  @DisplayName("环境里没有 nacos: 导入时，关闭 Nacos 配置中心")
  void should_disable_config_center_when_no_nacos_import() {
    StandardEnvironment environment = process(environment(Map.of()));

    assertThat(environment.getProperty("spring.cloud.nacos.config.enabled")).isEqualTo("false");
  }

  @Test
  @DisplayName("启动器加了 nacos: 导入时，不关闭配置中心")
  void should_keep_config_center_when_launcher_adds_nacos_import() {
    StandardEnvironment environment = environment(Map.of());
    environment
        .getPropertySources()
        .addLast(
            new DefaultPropertiesPropertySource(
                Map.of(
                    "spring.config.import", "nacos:patra-common.yaml,nacos:patra-registry.yaml")));

    process(environment);

    assertThat(environment.getProperty("spring.cloud.nacos.config.enabled")).isNull();
  }

  @Test
  @DisplayName("optional:nacos: 导入也算 nacos: 导入")
  void should_keep_config_center_when_import_is_optional_nacos() {
    StandardEnvironment environment =
        process(environment(Map.of("SPRING_CONFIG_IMPORT", "optional:nacos:patra-common.yaml")));

    assertThat(environment.getProperty("spring.cloud.nacos.config.enabled")).isNull();
  }

  @Test
  @DisplayName("只有非 nacos: 的导入时，照样关闭配置中心")
  void should_disable_config_center_when_imports_have_no_nacos() {
    StandardEnvironment environment = environment(Map.of());
    environment
        .getPropertySources()
        .addFirst(
            new MapPropertySource(
                "args", Map.of("spring.config.import", "classpath:registry-error-config.yaml")));

    process(environment);

    assertThat(environment.getProperty("spring.cloud.nacos.config.enabled")).isEqualTo("false");
  }

  @Test
  @DisplayName("排在 Spring Boot 加载配置之前")
  void should_run_before_config_data_loading() {
    assertThat(postProcessor.getOrder()).isLessThan(ConfigDataEnvironmentPostProcessor.ORDER);
  }

  private StandardEnvironment environment(Map<String, Object> systemEnvironment) {
    StandardEnvironment environment = new StandardEnvironment();
    MutablePropertySources sources = environment.getPropertySources();
    sources.replace(
        SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
        new SystemEnvironmentPropertySource(
            SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, systemEnvironment));
    sources.addFirst(new MapPropertySource("test", Map.of("user.home", home.toString())));
    return environment;
  }

  private StandardEnvironment process(StandardEnvironment environment) {
    postProcessor.postProcessEnvironment(environment, new SpringApplication());
    return environment;
  }
}
