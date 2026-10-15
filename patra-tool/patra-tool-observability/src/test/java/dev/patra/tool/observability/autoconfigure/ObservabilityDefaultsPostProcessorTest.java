package dev.patra.tool.observability.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.DefaultPropertiesPropertySource;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.mock.env.MockEnvironment;

class ObservabilityDefaultsPostProcessorTest {

  private final ObservabilityDefaultsPostProcessor processor =
      new ObservabilityDefaultsPostProcessor();

  @Test
  void should_add_defaults_at_lowest_priority() {
    StandardEnvironment env = new StandardEnvironment();
    processor.postProcessEnvironment(env, new SpringApplication());
    assertThat(env.getProperty("logging.config"))
        .isEqualTo("classpath:dev/patra/tool/observability/logback-patra.xml");
    assertThat(env.getProperty("logging.level.dev.patra")).isEqualTo("DEBUG");
    assertThat(env.getProperty("management.endpoints.web.exposure.include"))
        .isEqualTo("health,info,metrics");
    assertThat(env.getProperty("management.endpoint.health.show-details"))
        .isEqualTo("when-authorized");
    assertThat(env.getPropertySources().get(DefaultPropertiesPropertySource.NAME)).isNotNull();
  }

  @Test
  void should_let_any_other_source_win() {
    MockEnvironment env =
        new MockEnvironment().withProperty("logging.config", "classpath:mine.xml");
    processor.postProcessEnvironment(env, new SpringApplication());
    assertThat(env.getProperty("logging.config")).isEqualTo("classpath:mine.xml");
  }

  @Test
  void should_not_override_existing_default_properties() {
    StandardEnvironment env = new StandardEnvironment();
    DefaultPropertiesPropertySource.addOrMerge(
        Map.of("management.endpoints.web.exposure.include", "health"), env.getPropertySources());
    processor.postProcessEnvironment(env, new SpringApplication());
    assertThat(env.getProperty("management.endpoints.web.exposure.include")).isEqualTo("health");
    assertThat(env.getProperty("logging.level.dev.patra")).isEqualTo("DEBUG");
  }

  @Test
  void should_resolve_placeholders_against_environment() {
    StandardEnvironment env = new StandardEnvironment();
    env.getPropertySources()
        .addFirst(
            new MapPropertySource(
                "test",
                Map.of("spring.application.name", "patra-x", "PATRA_LOG_DIR", "/var/log/patra")));
    processor.postProcessEnvironment(env, new SpringApplication());
    assertThat(env.getProperty("management.metrics.tags.application")).isEqualTo("patra-x");
    assertThat(env.getProperty("logging.file.path")).isEqualTo("/var/log/patra");
  }
}
