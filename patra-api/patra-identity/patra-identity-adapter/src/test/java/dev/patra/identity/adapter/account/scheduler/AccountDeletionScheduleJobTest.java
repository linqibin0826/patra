package dev.patra.identity.adapter.account.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.xxl.job.core.context.XxlJobContext;
import dev.patra.identity.app.account.usecase.deletion.DeletionRunResult;
import dev.patra.identity.app.account.usecase.deletion.ExecuteDueAccountDeletionsCommand;
import dev.patra.tool.cqrs.CommandBus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// AccountDeletionScheduleJob 单元测试：全部成功回写成功，有失败或命令抛异常回写失败，都不往外抛。
@DisplayName("AccountDeletionScheduleJob 单元测试")
class AccountDeletionScheduleJobTest {

  private final CommandBus commandBus = mock(CommandBus.class);
  private final AccountDeletionScheduleJob job = new AccountDeletionScheduleJob(commandBus);
  private final XxlJobContext context = new XxlJobContext(1L, null, null, 0, 1);

  @BeforeEach
  void setContext() {
    XxlJobContext.setXxlJobContext(context);
  }

  @AfterEach
  void clearContext() {
    XxlJobContext.setXxlJobContext(null);
  }

  @Test
  @DisplayName("没有失败：回写成功与数量")
  void should_report_success_when_nothing_failed() {
    when(commandBus.handle(any(ExecuteDueAccountDeletionsCommand.class)))
        .thenReturn(DeletionRunResult.of(3, 0));

    job.execute();

    assertThat(context.getHandleCode()).isEqualTo(XxlJobContext.HANDLE_CODE_SUCCESS);
    assertThat(context.getHandleMsg()).contains("已注销 3 个");
  }

  @Test
  @DisplayName("有用户失败：回写失败与数量")
  void should_report_failure_when_some_users_failed() {
    when(commandBus.handle(any(ExecuteDueAccountDeletionsCommand.class)))
        .thenReturn(DeletionRunResult.of(2, 1));

    job.execute();

    assertThat(context.getHandleCode()).isEqualTo(XxlJobContext.HANDLE_CODE_FAIL);
    assertThat(context.getHandleMsg()).contains("已注销 2 个，失败 1 个");
  }

  @Test
  @DisplayName("命令抛异常：回写失败，不往外抛")
  void should_report_failure_when_command_throws() {
    when(commandBus.handle(any(ExecuteDueAccountDeletionsCommand.class)))
        .thenThrow(new IllegalStateException("boom"));

    assertThatCode(job::execute).doesNotThrowAnyException();
    assertThat(context.getHandleCode()).isEqualTo(XxlJobContext.HANDLE_CODE_FAIL);
  }
}
