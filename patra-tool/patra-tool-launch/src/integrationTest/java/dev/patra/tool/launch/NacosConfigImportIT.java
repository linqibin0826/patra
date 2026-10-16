package dev.patra.tool.launch;

import static dev.patra.tool.launch.NacosServerFixture.causeChain;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;

/// 经启动器导入 Nacos 配置的集成测试（spec 8.2 第 1、2、3、6、8 条）。
///
/// 这些用例连同一个 Nacos、用同一个账号，可以共用一个 JVM（SCA 在进程里只有一个 ConfigService）。
@DisplayName("经启动器导入 Nacos 配置 集成测试")
@Timeout(value = 5, unit = TimeUnit.MINUTES)
class NacosConfigImportIT {

  private static final String SERVICE = "patra-it-app";

  private static final String COMMON_YAML =
      """
      patra:
        config-source:
          common: patra-common.yaml
        it:
          jar-vs-nacos: from-common
          common-vs-service: from-common
          shared: from-common
      """;

  private static NacosServerFixture nacos;

  @BeforeAll
  static void startNacos() {
    NacosServerFixture.isolateSnapshots();
    nacos = NacosServerFixture.start();
    nacos.useGrpcPortOffset();
    nacos.publish("patra-common.yaml", COMMON_YAML);
    nacos.publish(SERVICE + ".yaml", serviceYaml("v1"));
  }

  @AfterAll
  static void stopNacos() {
    nacos.close();
  }

  /// 本服务配置引用了 `${PATRA_IT_SECRET}`，先给它一个值，应用才能启动。
  @BeforeEach
  void provideSecret() {
    System.setProperty("PATRA_IT_SECRET", "secret-for-test");
  }

  @AfterEach
  void restore() {
    System.clearProperty("patra.it.shared");
    System.clearProperty("PATRA_IT_SECRET");
    nacos.publish(SERVICE + ".yaml", serviceYaml("v1"));
  }

  @Test
  @DisplayName("读到两份配置：Nacos 覆盖 jar，本服务配置覆盖公共配置")
  void should_read_both_data_ids_with_nacos_over_jar_and_service_over_common() {
    try (ConfigurableApplicationContext context = nacos.launch(SERVICE)) {
      ConfigurableEnvironment environment = context.getEnvironment();

      assertThat(environment.getProperty("patra.it.jar-vs-nacos")).isEqualTo("from-common");
      assertThat(environment.getProperty("patra.it.common-vs-service")).isEqualTo("from-service");
      assertThat(environment.getProperty("patra.it.jar-only")).isEqualTo("from-jar");
    }
  }

  @Test
  @DisplayName("系统属性覆盖 Nacos 的值")
  void should_let_system_property_override_nacos() {
    System.setProperty("patra.it.shared", "from-system");

    try (ConfigurableApplicationContext context = nacos.launch(SERVICE)) {
      assertThat(context.getEnvironment().getProperty("patra.it.shared")).isEqualTo("from-system");
    }
  }

  @Test
  @DisplayName("Nacos 配置里的 ${变量} 能解析到进程里的值")
  void should_resolve_placeholder_in_nacos_config() {
    try (ConfigurableApplicationContext context = nacos.launch(SERVICE)) {
      assertThat(context.getEnvironment().getProperty("patra.it.secret"))
          .isEqualTo("secret-for-test");
    }
  }

  @Test
  @DisplayName("Nacos 配置引用的变量没有值：启动失败，报出文件、键和变量名")
  void should_fail_naming_variable_when_nacos_reference_unresolved() {
    System.clearProperty("PATRA_IT_SECRET");

    assertThatThrownBy(() -> nacos.launch(SERVICE))
        .satisfies(
            failure ->
                assertThat(causeChain(failure))
                    .contains(
                        "Nacos 配置 patra-it-app.yaml 的 patra.it.secret 引用的变量 PATRA_IT_SECRET 没有值"));
  }

  @Test
  @DisplayName("本服务的配置在 Nacos 里不存在：启动失败，报签到错误")
  void should_fail_with_signature_message_when_service_data_id_missing() {
    assertThatThrownBy(() -> nacos.launch("patra-it-missing"))
        .satisfies(
            failure ->
                assertThat(causeChain(failure))
                    .contains("没拿到本服务的 Nacos 配置 patra-it-missing.yaml")
                    .doesNotContain("没拿到 Nacos 配置 patra-common.yaml"));
  }

  @Test
  @DisplayName("Nacos 里的值变了：运行中应用的配置环境读到新值")
  void should_see_new_value_after_nacos_change() {
    try (ConfigurableApplicationContext context = nacos.launch(SERVICE)) {
      assertThat(refreshable(context)).isEqualTo("v1");

      nacos.publish(SERVICE + ".yaml", serviceYaml("v2"));

      await()
          .atMost(Duration.ofSeconds(30))
          .untilAsserted(() -> assertThat(refreshable(context)).isEqualTo("v2"));
    }
  }

  @Test
  @DisplayName("刷新时本服务配置少了签到行：这次变更被拒绝，应用继续用旧值")
  void should_keep_old_values_when_refreshed_config_lacks_signature() {
    try (ConfigurableApplicationContext context = nacos.launch(SERVICE)) {
      nacos.publish(SERVICE + ".yaml", serviceYaml("v2"));
      await()
          .atMost(Duration.ofSeconds(30))
          .untilAsserted(() -> assertThat(refreshable(context)).isEqualTo("v2"));

      nacos.publish(
          SERVICE + ".yaml",
          """
          patra:
            it:
              refreshable: unsigned
          """);

      await()
          .during(Duration.ofSeconds(5))
          .atMost(Duration.ofSeconds(10))
          .untilAsserted(() -> assertThat(refreshable(context)).isEqualTo("v2"));
      assertThat(context.isActive()).isTrue();
    }
  }

  private static String refreshable(ConfigurableApplicationContext context) {
    return context.getEnvironment().getProperty("patra.it.refreshable");
  }

  private static String serviceYaml(String refreshable) {
    return """
        patra:
          config-source:
            service: patra-it-app.yaml
          it:
            common-vs-service: from-service
            secret: ${PATRA_IT_SECRET}
            refreshable: %s
        """
        .formatted(refreshable);
  }
}
