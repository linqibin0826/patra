package dev.patra.tool.launch;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertySource;

/// 不经启动器、用 `@SpringBootTest` 启动的集成测试（spec 8.2 第 7 条）。
///
/// 对应接入 starter 后各服务的测试：Nacos 不可达也照常启动，不导入 Nacos 配置，不做签到检查。
/// 服务发现照各服务测试 profile 的做法关掉。
@SpringBootTest(
    classes = NacosITBootstrap.class,
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = {
      "PATRA_NACOS_ADDR=127.0.0.1:1",
      "spring.cloud.nacos.discovery.enabled=false",
      "spring.cloud.service-registry.auto-registration.enabled=false"
    })
@DisplayName("不经启动器启动 集成测试")
class WithoutLauncherIT {

  @Autowired private ConfigurableEnvironment environment;

  @Test
  @DisplayName("Nacos 不可达也正常启动：配置中心关闭，只有 jar 里的配置")
  void should_start_from_jar_config_without_nacos_import() {
    assertThat(environment.getProperty(NacosDefaultsPostProcessor.CONFIG_ENABLED_PROPERTY))
        .isEqualTo("false");
    assertThat(environment.getProperty("patra.it.jar-vs-nacos")).isEqualTo("from-jar");
    assertThat(environment.getPropertySources().stream().map(PropertySource::getName))
        .noneMatch(name -> name.contains("nacos:"));
  }
}
