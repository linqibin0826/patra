package dev.patra.tool.web.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.tool.core.error.codes.HttpStdErrors;
import dev.patra.tool.web.error.adapter.ProblemDetailAdapter;
import dev.patra.tool.web.error.engine.ErrorResolutionEngine;
import dev.patra.tool.web.error.handler.GlobalRestExceptionHandler;
import dev.patra.tool.web.error.pipeline.ErrorResolutionPipeline;
import dev.patra.tool.web.error.spi.TraceProvider;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

class WebErrorAutoConfigurationTest {

  private final WebApplicationContextRunner runner =
      new WebApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(WebErrorAutoConfiguration.class))
          .withPropertyValues("patra.web.error.context-prefix=TST");

  @Test
  void should_register_engine_pipeline_and_handler_by_default() {
    runner.run(
        ctx -> {
          assertThat(ctx)
              .hasSingleBean(ErrorResolutionEngine.class)
              .hasSingleBean(ErrorResolutionPipeline.class)
              .hasSingleBean(TraceProvider.class)
              .hasSingleBean(ProblemDetailAdapter.class)
              .hasSingleBean(GlobalRestExceptionHandler.class);
          assertThat(ctx.getBean(HttpStdErrors.Group.class).NOT_FOUND().code()).startsWith("TST-");
        });
  }

  @Test
  void should_register_nothing_when_module_disabled() {
    runner
        .withPropertyValues("patra.web.enabled=false")
        .run(
            ctx ->
                assertThat(ctx)
                    .doesNotHaveBean(GlobalRestExceptionHandler.class)
                    .doesNotHaveBean(ErrorResolutionEngine.class));
  }

  @Test
  void should_back_off_when_user_defines_trace_provider() {
    TraceProvider custom = () -> Optional.of("fixed");
    runner
        .withBean("customTrace", TraceProvider.class, () -> custom)
        .run(ctx -> assertThat(ctx).getBean(TraceProvider.class).isSameAs(custom));
  }

  @Test
  void should_bind_nested_defaults() {
    runner.run(
        ctx -> {
          WebProperties props = ctx.getBean(WebProperties.class);
          assertThat(props.error().engine().maxCauseDepth()).isEqualTo(10);
          assertThat(props.error().trace().headerNames())
              .containsExactly("traceId", "X-B3-TraceId", "traceparent");
        });
  }
}
