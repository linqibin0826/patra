package dev.linqibin.patra.starter.nacos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.mock.env.MockEnvironment;

/// ConfigSourceCheckPostProcessor 单元测试：两份 Nacos 配置的签到检查。
@DisplayName("ConfigSourceCheckPostProcessor 单元测试")
class ConfigSourceCheckPostProcessorTest {

  private static final String COMMON_MISSING =
      "没拿到 Nacos 配置 patra-common.yaml：Nacos 不可达且本地没有快照，或该文件不存在";
  private static final String SERVICE_MISSING =
      "没拿到本服务的 Nacos 配置 patra-ingest.yaml：Nacos 不可达且本地没有快照，或该文件不存在";

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
            .withProperty("patra.config-source.service", "patra-ingest.yaml");

    assertThatCode(() -> check(environment)).doesNotThrowAnyException();
  }

  @Test
  @DisplayName("缺公共配置的签到：启动失败，说明没拿到 patra-common.yaml")
  void should_fail_with_common_message_when_common_signature_missing() {
    MockEnvironment environment =
        requiredEnvironment().withProperty("patra.config-source.service", "patra-ingest.yaml");

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
            .withProperty("patra.config-source.service", "patra-ingest.yaml");

    assertThatThrownBy(() -> check(environment)).hasMessage(COMMON_MISSING);
  }

  @Test
  @DisplayName("排在 Spring Boot 加载配置之后")
  void should_run_after_config_data_loading() {
    assertThat(postProcessor.getOrder()).isGreaterThan(ConfigDataEnvironmentPostProcessor.ORDER);
  }

  private void check(MockEnvironment environment) {
    postProcessor.postProcessEnvironment(environment, new SpringApplication());
  }

  private static MockEnvironment requiredEnvironment() {
    return new MockEnvironment()
        .withProperty(ConfigSourceCheckPostProcessor.REQUIRED_PROPERTY, "true")
        .withProperty("spring.application.name", "patra-ingest");
  }
}
