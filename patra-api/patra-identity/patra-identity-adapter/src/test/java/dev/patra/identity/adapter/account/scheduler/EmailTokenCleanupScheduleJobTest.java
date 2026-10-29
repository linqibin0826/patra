package dev.patra.identity.adapter.account.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.xxl.job.core.context.XxlJobContext;
import dev.patra.identity.app.account.usecase.email.PurgeExpiredEmailTokensCommand;
import dev.patra.tool.cqrs.CommandBus;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/// EmailTokenCleanupScheduleJob 单元测试：带配置的保留期调用命令；失败不抛出。
@DisplayName("EmailTokenCleanupScheduleJob 单元测试")
class EmailTokenCleanupScheduleJobTest {

  private final CommandBus commandBus = mock(CommandBus.class);
  private final EmailTokenCleanupScheduleJob job =
      new EmailTokenCleanupScheduleJob(commandBus, Duration.ofDays(30));

  @AfterEach
  void clearContext() {
    XxlJobContext.setXxlJobContext(null);
  }

  @Test
  @DisplayName("按配置的 30 天保留期发清理命令")
  void should_purge_with_configured_retention() {
    when(commandBus.handle(any(PurgeExpiredEmailTokensCommand.class))).thenReturn(12);

    job.execute();

    ArgumentCaptor<PurgeExpiredEmailTokensCommand> command =
        ArgumentCaptor.forClass(PurgeExpiredEmailTokensCommand.class);
    verify(commandBus).handle(command.capture());
    assertThat(command.getValue().retention()).isEqualTo(Duration.ofDays(30));
  }

  @Test
  @DisplayName("命令抛异常时任务只记失败，不往外抛")
  void should_not_throw_when_command_fails() {
    when(commandBus.handle(any(PurgeExpiredEmailTokensCommand.class)))
        .thenThrow(new IllegalStateException("boom"));

    assertThatCode(job::execute).doesNotThrowAnyException();
  }
}
