package dev.patra.notification.adapter.mail.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.xxl.job.core.context.XxlJobContext;
import dev.patra.notification.app.mail.usecase.digest.DigestScheduleResult;
import dev.patra.notification.app.mail.usecase.digest.ScheduleDailyDigestCommand;
import dev.patra.tool.cqrs.CommandBus;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/// 每日汇总建名单任务：参数为空是定时，`users=…` 是手动验收，别的参数拒绝（spec 5.2）。
class DailyDigestScheduleJobTest {

  private final CommandBus commandBus = mock(CommandBus.class);
  private final DailyDigestScheduleJob job = new DailyDigestScheduleJob(commandBus);

  @AfterEach
  void clearContext() {
    XxlJobContext.setXxlJobContext(null);
  }

  /// 参数为空：定时。
  @Test
  void digest_job_without_param_is_scheduled() {
    XxlJobContext context = context(null);
    when(commandBus.handle(any(ScheduleDailyDigestCommand.class))).thenReturn(result());

    job.execute();

    verify(commandBus).handle(ScheduleDailyDigestCommand.scheduled());
    assertThat(context.getHandleCode()).isEqualTo(XxlJobContext.HANDLE_CODE_SUCCESS);
    assertThat(context.getHandleMsg()).isEqualTo("窗口 2026-10-31T00:00:00Z：名单 3 人，新建 2 封，清理 1 条");
  }

  /// `users=…`：只给列出的人，去空白。
  @Test
  void digest_job_parses_users_param() {
    context(" users = 12, 34 ");
    when(commandBus.handle(any(ScheduleDailyDigestCommand.class))).thenReturn(result());

    job.execute();

    verify(commandBus).handle(ScheduleDailyDigestCommand.manual(Set.of(12L, 34L)));
  }

  /// 不认识的参数：回写失败，不调命令。
  @ParameterizedTest
  @ValueSource(strings = {"now", "users=", "users=a,1", "users=0", "users=-3", "ids=1"})
  void digest_job_rejects_bad_param(String param) {
    XxlJobContext context = context(param);

    job.execute();

    verifyNoInteractions(commandBus);
    assertThat(context.getHandleCode()).isEqualTo(XxlJobContext.HANDLE_CODE_FAIL);
    assertThat(context.getHandleMsg()).startsWith("参数应为空或 users=用户ID,…");
  }

  private static XxlJobContext context(String param) {
    XxlJobContext context = new XxlJobContext(1L, param, null, 0, 1);
    XxlJobContext.setXxlJobContext(context);
    return context;
  }

  private static DigestScheduleResult result() {
    return DigestScheduleResult.of(Instant.parse("2026-10-31T00:00:00Z"), 3, 2, 1);
  }
}
