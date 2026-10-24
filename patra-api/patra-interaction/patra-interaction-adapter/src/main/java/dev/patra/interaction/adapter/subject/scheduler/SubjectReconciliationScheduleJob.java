package dev.patra.interaction.adapter.subject.scheduler;

import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import dev.patra.interaction.app.subject.usecase.reconcile.ReconcileSubjectsCommand;
import dev.patra.interaction.app.subject.usecase.reconcile.ReconciliationResult;
import dev.patra.tool.cqrs.CommandBus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/// 对象对账的 XXL-Job 入口（handler `subjectReconciliationJob`），无任务参数。
///
/// mini 的 xxl-job-admin 里每天 06:30 调一次（Cron `0 30 6 * * ?`，排在 catalog 每日增量之后）；catalog
/// 换库重建或重跑导入后要手动触发一次。有批次因 catalog 不可用被跳过时回写失败（调度中心标红），次日自动重跑。
@Slf4j
@Component
@RequiredArgsConstructor
public class SubjectReconciliationScheduleJob {

  private final CommandBus commandBus;

  /// 执行一次。
  @XxlJob("subjectReconciliationJob")
  public void execute() {
    log.info("对象对账任务已触发，jobId [{}]", XxlJobHelper.getJobId());
    try {
      ReconciliationResult result = commandBus.handle(ReconcileSubjectsCommand.of());
      if (result.failedBatches() > 0) {
        XxlJobHelper.handleFail(
            "已核对 " + result.scanned() + " 个，失败 " + result.failedBatches() + " 批");
      } else {
        XxlJobHelper.handleSuccess(
            "已核对 %d 个：换 ID %d、失效 %d、复活 %d"
                .formatted(
                    result.scanned(),
                    result.sourceIdChanged(),
                    result.becameUnavailable(),
                    result.revived()));
      }
    } catch (RuntimeException ex) {
      log.error("对象对账任务执行失败：{}", ex.getMessage(), ex);
      XxlJobHelper.handleFail("执行失败：" + ex.getMessage());
    }
  }
}
