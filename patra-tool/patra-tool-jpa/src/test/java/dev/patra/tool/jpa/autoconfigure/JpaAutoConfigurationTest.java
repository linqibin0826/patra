package dev.patra.tool.jpa.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import dev.patra.tool.core.error.codes.HttpStdErrors;
import dev.patra.tool.jpa.error.contributor.JpaErrorMappingContributor;
import dev.patra.tool.jpa.json.JsonColumnCodec;
import dev.patra.tool.web.error.spi.ErrorMappingContributor;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import tools.jackson.databind.json.JsonMapper;

class JpaAutoConfigurationTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(JacksonAutoConfiguration.class, JpaAutoConfiguration.class))
          .withBean(HttpStdErrors.Group.class, () -> HttpStdErrors.of("TST"))
          // @EnableJpaAuditing 需要 JPA 元模型；契约测试没有 EntityManagerFactory，用桩顶替
          .withBean(
              "jpaMappingContext",
              JpaMetamodelMappingContext.class,
              () -> mock(JpaMetamodelMappingContext.class));

  @Test
  void should_register_codec_auditing_and_error_mapping_by_default() {
    runner.run(
        ctx ->
            assertThat(ctx)
                .hasSingleBean(JsonColumnCodec.class)
                .hasSingleBean(DateTimeProvider.class)
                .hasSingleBean(JpaErrorMappingContributor.class)
                .doesNotHaveBean(Clock.class));
  }

  @Test
  void should_register_nothing_when_disabled() {
    runner
        .withPropertyValues("patra.jpa.enabled=false")
        .run(ctx -> assertThat(ctx).doesNotHaveBean(JsonColumnCodec.class));
  }

  @Test
  void should_back_off_when_user_defines_codec() {
    runner
        .withBean(
            "myCodec",
            JsonColumnCodec.class,
            () -> new JsonColumnCodec(JsonMapper.builder().build()))
        .run(
            ctx -> assertThat(ctx).getBean(JsonColumnCodec.class).isSameAs(ctx.getBean("myCodec")));
  }

  @Test
  void should_skip_error_mapping_without_web_on_classpath() {
    runner
        .withClassLoader(new FilteredClassLoader(ErrorMappingContributor.class))
        .run(
            ctx ->
                assertThat(ctx)
                    .hasSingleBean(JsonColumnCodec.class)
                    .doesNotHaveBean(JpaErrorMappingContributor.class));
  }
}
