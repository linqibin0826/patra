package dev.patra.tool.launch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.boot.env.OriginTrackedMapPropertySource;
import org.springframework.mock.env.MockEnvironment;

/// ConfigSourceCheckPostProcessor 单元测试：两份 Nacos 配置的签到检查。
@DisplayName("ConfigSourceCheckPostProcessor 单元测试")
class ConfigSourceCheckPostProcessorTest {

  private static final String COMMON_MISSING =
      "没拿到 Nacos 配置 patra-common.yaml：Nacos 不可达且本地没有快照，或该文件不存在";
  private static final String SERVICE_MISSING =
      "没拿到本服务的 Nacos 配置 patra-catalog.yaml：Nacos 不可达且本地没有快照，或该文件不存在";

  private final ConfigSourceCheckPostProcessor postProcessor = new ConfigSourceCheckPostProcessor();

  @Test
  @DisplayName("没开启签到检查时（测试、没用启动器），缺签到也不报错")
  void should_skip_check_when_not_required() {
    MockEnvironment environment = new MockEnvironment();

    assertThatCode(() -> check(environment)).doesNotThrowAnyException();
  }

  @Test
  @DisplayName("两个签到都在时通过")
  void should_pass_when_both_signatures_present() {
    MockEnvironment environment =
        requiredEnvironment()
            .withProperty("patra.config-source.common", "patra-common.yaml")
            .withProperty("patra.config-source.service", "patra-catalog.yaml");

    assertThatCode(() -> check(environment)).doesNotThrowAnyException();
  }

  @Test
  @DisplayName("缺公共配置的签到：启动失败，说明没拿到 patra-common.yaml")
  void should_fail_with_common_message_when_common_signature_missing() {
    MockEnvironment environment =
        requiredEnvironment().withProperty("patra.config-source.service", "patra-catalog.yaml");

    assertThatThrownBy(() -> check(environment))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage(COMMON_MISSING);
  }

  @Test
  @DisplayName("缺本服务配置的签到：启动失败，报错里带上服务名")
  void should_fail_with_service_message_when_service_signature_missing() {
    MockEnvironment environment =
        requiredEnvironment().withProperty("patra.config-source.common", "patra-common.yaml");

    assertThatThrownBy(() -> check(environment))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage(SERVICE_MISSING);
  }

  @Test
  @DisplayName("两个都缺时两条都报")
  void should_report_both_when_both_signatures_missing() {
    assertThatThrownBy(() -> check(requiredEnvironment()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage(COMMON_MISSING + "\n" + SERVICE_MISSING);
  }

  @Test
  @DisplayName("签到值是空白字符串时当作没拿到")
  void should_treat_blank_signature_as_missing() {
    MockEnvironment environment =
        requiredEnvironment()
            .withProperty("patra.config-source.common", "  ")
            .withProperty("patra.config-source.service", "patra-catalog.yaml");

    assertThatThrownBy(() -> check(environment)).hasMessage(COMMON_MISSING);
  }

  @Test
  @DisplayName("Nacos 配置引用的变量没有值：启动失败，报出文件、键和变量名")
  void should_fail_when_nacos_property_references_missing_variable() {
    MockEnvironment environment = signedEnvironment();
    environment
        .getPropertySources()
        .addLast(
            nacosSource(
                "patra-common.yaml", Map.of("spring.data.redis.password", "${REDIS_PASSWORD}")));

    assertThatThrownBy(() -> check(environment))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage(
            "Nacos 配置 patra-common.yaml 的 spring.data.redis.password 引用的变量 REDIS_PASSWORD 没有值："
                + "本机放在 ~/.patra/secrets/ 下的 .env 文件里，容器经 env_file 注入");
  }

  @Test
  @DisplayName("引用的变量有值、或写了默认值时通过")
  void should_pass_when_references_resolve_or_have_default() {
    MockEnvironment environment = signedEnvironment().withProperty("REDIS_PASSWORD", "x");
    environment
        .getPropertySources()
        .addLast(
            nacosSource(
                "patra-common.yaml",
                Map.of(
                    "spring.data.redis.password", "${REDIS_PASSWORD}",
                    "spring.datasource.password", "${PATRA_DB_PASSWORD:123456}",
                    "spring.data.redis.port", 16379)));

    assertThatCode(() -> check(environment)).doesNotThrowAnyException();
  }

  @Test
  @DisplayName("只查 Nacos 配置：jar 里的配置引用了没值的变量不归这里管")
  void should_ignore_unresolved_references_outside_nacos() {
    MockEnvironment environment = signedEnvironment();
    environment
        .getPropertySources()
        .addLast(
            new OriginTrackedMapPropertySource(
                "Config resource 'class path resource [application.yml]' via location"
                    + " 'optional:classpath:/'",
                Map.of("some.key", "${NOT_SET}")));

    assertThatCode(() -> check(environment)).doesNotThrowAnyException();
  }

  @Test
  @DisplayName("排在 Spring Boot 加载配置之后")
  void should_run_after_config_data_loading() {
    assertThat(postProcessor.getOrder()).isGreaterThan(ConfigDataEnvironmentPostProcessor.ORDER);
  }

  private void check(MockEnvironment environment) {
    postProcessor.postProcessEnvironment(environment, new SpringApplication());
  }

  private static MockEnvironment signedEnvironment() {
    return requiredEnvironment()
        .withProperty("patra.config-source.common", "patra-common.yaml")
        .withProperty("patra.config-source.service", "patra-catalog.yaml");
  }

  /// 按 SCA 的 `NacosConfigDataLoader` 放进环境的样子造一份 Nacos 配置：名字是 `分组@dataId`。
  ///
  /// @param dataId 配置的 dataId
  /// @param properties 配置内容
  /// @return 属性源
  private static OriginTrackedMapPropertySource nacosSource(
      String dataId, Map<String, Object> properties) {
    return new OriginTrackedMapPropertySource("DEFAULT_GROUP@" + dataId, properties);
  }

  private static MockEnvironment requiredEnvironment() {
    return new MockEnvironment()
        .withProperty(ConfigSourceCheckPostProcessor.REQUIRED_PROPERTY, "true")
        .withProperty("spring.application.name", "patra-catalog");
  }
}
