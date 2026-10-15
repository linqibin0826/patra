package dev.patra.tool.launch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/// PatraApplication 单元测试：启动器放进 default properties 的内容。
@DisplayName("PatraApplication 单元测试")
class PatraApplicationTest {

  @Test
  @DisplayName("服务名放进 spring.application.name")
  void should_put_application_name() {
    assertThat(PatraApplication.defaultProperties("patra-registry"))
        .containsEntry("spring.application.name", "patra-registry");
  }

  @Test
  @DisplayName("先导入公共配置、再导入本服务配置，都不标 optional")
  void should_import_common_then_service_config_without_optional() {
    assertThat(PatraApplication.defaultProperties("patra-registry"))
        .containsEntry("spring.config.import", "nacos:patra-common.yaml,nacos:patra-registry.yaml");
  }

  @Test
  @DisplayName("配置中心用 dev namespace，Nacos 的值优先于 jar")
  void should_use_dev_namespace_and_prefer_remote_config() {
    assertThat(PatraApplication.defaultProperties("patra-registry"))
        .containsEntry("spring.cloud.nacos.config.namespace", "dev")
        .containsEntry("spring.cloud.nacos.config.preference", "remote");
  }

  @Test
  @DisplayName("开启签到检查")
  void should_require_config_source_check() {
    assertThat(PatraApplication.defaultProperties("patra-registry"))
        .containsEntry(ConfigSourceCheckPostProcessor.REQUIRED_PROPERTY, "true");
  }

  @Test
  @DisplayName("只放这五项")
  void should_contain_only_launcher_properties() {
    assertThat(PatraApplication.defaultProperties("patra-registry")).hasSize(5);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = "  ")
  @DisplayName("服务名为空时报错")
  void should_reject_blank_application_name(String applicationName) {
    assertThatThrownBy(() -> PatraApplication.defaultProperties(applicationName))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("服务名不能为空");
  }
}
