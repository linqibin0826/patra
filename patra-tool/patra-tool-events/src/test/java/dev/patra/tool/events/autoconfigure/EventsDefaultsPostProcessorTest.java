package dev.patra.tool.events.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.DefaultPropertiesPropertySource;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

@DisplayName("事件模块的默认值")
class EventsDefaultsPostProcessorTest {

  private final EventsDefaultsPostProcessor processor = new EventsDefaultsPostProcessor();

  @Test
  @DisplayName("以最低优先级给出确认、完成模式、重试与 prefetch 的默认值")
  void should_add_defaults_at_lowest_priority() {
    StandardEnvironment env = new StandardEnvironment();
    processor.postProcessEnvironment(env, new SpringApplication());
    assertThat(env.getProperty("spring.rabbitmq.publisher-confirm-type")).isEqualTo("correlated");
    assertThat(env.getProperty("spring.rabbitmq.publisher-returns")).isEqualTo("true");
    assertThat(env.getProperty("spring.modulith.events.completion-mode")).isEqualTo("delete");
    assertThat(env.getProperty("spring.modulith.events.jdbc.schema-initialization.enabled"))
        .isEqualTo("false");
    assertThat(env.getProperty("spring.rabbitmq.listener.simple.retry.max-retries")).isEqualTo("3");
    assertThat(env.getProperty("spring.rabbitmq.listener.simple.prefetch")).isEqualTo("10");
    assertThat(env.getProperty("management.health.rabbit.enabled")).isEqualTo("false");
    assertThat(env.getProperty("spring.modulith.events.registry-trigger-annotation"))
        .isEqualTo("org.springframework.modulith.events.ApplicationModuleListener");
    assertThat(env.getPropertySources().get(DefaultPropertiesPropertySource.NAME)).isNotNull();
  }

  @Test
  @DisplayName("服务自己的配置覆盖默认值")
  void should_let_application_properties_override_defaults() {
    StandardEnvironment env = new StandardEnvironment();
    env.getPropertySources()
        .addFirst(
            new MapPropertySource(
                "application", Map.of("spring.rabbitmq.listener.simple.prefetch", "50")));
    processor.postProcessEnvironment(env, new SpringApplication());
    assertThat(env.getProperty("spring.rabbitmq.listener.simple.prefetch")).isEqualTo("50");
  }

  @Test
  @DisplayName("defaultProperties 里已有的键不被覆盖")
  void should_keep_existing_default_properties() {
    StandardEnvironment env = new StandardEnvironment();
    DefaultPropertiesPropertySource.addOrMerge(
        Map.of("spring.modulith.events.completion-mode", "update"), env.getPropertySources());
    processor.postProcessEnvironment(env, new SpringApplication());
    assertThat(env.getProperty("spring.modulith.events.completion-mode")).isEqualTo("update");
    assertThat(env.getProperty("spring.rabbitmq.publisher-confirm-type")).isEqualTo("correlated");
  }
}
