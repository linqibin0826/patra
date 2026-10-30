package dev.patra.interaction.adapter.subject.scheduler;

import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import dev.patra.interaction.app.subject.usecase.recount.RecountResult;
import dev.patra.interaction.app.subject.usecase.recount.RecountStatsCommand;
import dev.patra.tool.cqrs.CommandBus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/// 按明细重算的 XXL-Job 入口（handler `statsRecountJob`），无任务参数。重算各子域登记的每项计数
/// （`StatsRecountGateway`），修正与明细不一致的行。
///
/// 只手动触发：mini 的 xxl-job-admin 里登记为调度类型「无」，超时 600 秒、不重试（release spec v0.15 决策 A：不做
/// 每晚对账）。发现差异照常修正并回写成功，差异条数写在回写消息里；只有执行出错才回写失败。
@Slf4j
@Component
@RequiredArgsConstructor
public class StatsRecountScheduleJob {

  private final CommandBus commandBus;

  /// 执行一次。
  @XxlJob("statsRecountJob")
  public void execute() {
    log.info("统计重算任务已触发，jobId [{}]", XxlJobHelper.getJobId());
    try {
      RecountResult result = commandBus.handle(RecountStatsCommand.of());
      XxlJobHelper.handleSuccess("差异 %d 行：%s".formatted(result.corrected(), result.summary()));
    } catch (RuntimeException ex) {
      log.error("统计重算任务执行失败：{}", ex.getMessage(), ex);
      XxlJobHelper.handleFail("执行失败：" + ex.getMessage());
    }
  }
}
