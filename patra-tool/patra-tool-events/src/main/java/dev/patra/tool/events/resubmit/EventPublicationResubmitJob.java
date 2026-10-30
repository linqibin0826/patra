package dev.patra.tool.events.resubmit;

import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/// 事件补投的 XXL-Job 入口（handler `eventPublicationResubmitJob`），无任务参数。
///
/// 发布事件的服务在 xxl-job-admin 登记：每分钟一次（CRON `0 * * * * ?`），路由「第一个」，
/// 阻塞处理「丢弃后续调度」，超时 60 秒，不重试。逻辑见 {@link EventPublicationResubmitter}。
@Slf4j
@RequiredArgsConstructor
public class EventPublicationResubmitJob {

  private final EventPublicationResubmitter resubmitter;

  /// 执行一次补投，回写补投条数；出错回写失败。
  @XxlJob("eventPublicationResubmitJob")
  public void execute() {
    try {
      int count = resubmitter.resubmit();
      XxlJobHelper.handleSuccess("补投 %d 条".formatted(count));
    } catch (RuntimeException ex) {
      log.error("事件补投失败：{}", ex.getMessage(), ex);
      XxlJobHelper.handleFail("执行失败：" + ex.getMessage());
    }
  }
}
