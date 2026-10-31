package dev.patra.tool.events.consumer;

import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/// 已处理事件清理的 XXL-Job 入口（handler `processedEventPurgeJob`），无任务参数。
///
/// 消费事件的服务在 xxl-job-admin 登记：每天一次（CRON `0 0 7 * * ?`），路由「第一个」，阻塞处理「单机串行」，
/// 超时 300 秒，不重试。逻辑见 {@link ProcessedEventPurger}。
@Slf4j
@RequiredArgsConstructor
public class ProcessedEventPurgeJob {

  private final ProcessedEventPurger purger;

  /// 执行一次清理，回写删除条数；出错回写失败。
  @XxlJob("processedEventPurgeJob")
  public void execute() {
    try {
      int count = purger.purge();
      XxlJobHelper.handleSuccess("删除 %d 条已处理事件".formatted(count));
    } catch (RuntimeException ex) {
      log.error("已处理事件清理失败：{}", ex.getMessage(), ex);
      XxlJobHelper.handleFail("执行失败：" + ex.getMessage());
    }
  }
}
