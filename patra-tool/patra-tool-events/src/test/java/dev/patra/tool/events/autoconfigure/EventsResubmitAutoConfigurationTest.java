package dev.patra.tool.events.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.xxl.job.core.handler.annotation.XxlJob;
import dev.patra.tool.events.resubmit.EventPublicationResubmitJob;
import dev.patra.tool.events.resubmit.EventPublicationResubmitter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.modulith.events.core.EventPublicationRegistry;

@DisplayName("EventsResubmitAutoConfiguration 契约")
class EventsResubmitAutoConfigurationTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(EventsResubmitAutoConfiguration.class))
          .withBean(EventPublicationRegistry.class, () -> mock(EventPublicationRegistry.class));

  @Test
  @DisplayName("有 Modulith 事件登记时注册补投与 XXL-Job 任务")
  void should_register_resubmitter_and_job() {
    runner
        .withBean(IncompleteEventPublications.class, () -> mock(IncompleteEventPublications.class))
        .run(
            context -> {
              assertThat(context).hasSingleBean(EventPublicationResubmitter.class);
              assertThat(context).hasSingleBean(EventPublicationResubmitJob.class);
            });
  }

  @Test
  @DisplayName("没有事件登记时不注册")
  void should_back_off_without_event_registry() {
    runner.run(
        context -> {
          assertThat(context).doesNotHaveBean(EventPublicationResubmitter.class);
          assertThat(context).doesNotHaveBean(EventPublicationResubmitJob.class);
        });
  }

  @Test
  @DisplayName("没有 XXL-Job 时只注册补投，不注册任务")
  void should_skip_job_without_xxl_job() {
    runner
        .withBean(IncompleteEventPublications.class, () -> mock(IncompleteEventPublications.class))
        .withClassLoader(new FilteredClassLoader(XxlJob.class))
        .run(
            context -> {
              assertThat(context).hasSingleBean(EventPublicationResubmitter.class);
              assertThat(context).doesNotHaveBean(EventPublicationResubmitJob.class);
            });
  }
}
