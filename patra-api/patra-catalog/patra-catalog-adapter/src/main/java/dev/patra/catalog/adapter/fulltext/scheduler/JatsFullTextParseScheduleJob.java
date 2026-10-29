package dev.patra.catalog.adapter.fulltext.scheduler;

import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import dev.patra.catalog.app.fulltext.usecase.parse.FullTextParseRunStats;
import dev.patra.catalog.app.fulltext.usecase.parse.JatsFullTextParseCommand;
import dev.patra.tool.cqrs.CommandBus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/// JATS 全文解析的 XXL-Job 入口（handler `jatsFullTextParseJob`，无参数）。
///
/// 由全文抓取任务作为子任务触发，另有一条每小时的兜底任务；全量重新解析见
/// `docs/patra/runbooks/v0.11-fulltext-runbook.md`。
///
/// @author linqibin
/// @since 0.11.0
@Slf4j
@Component
@RequiredArgsConstructor
public class JatsFullTextParseScheduleJob {

  private final CommandBus commandBus;

  /// 执行一次解析。
  @XxlJob("jatsFullTextParseJob")
  public void execute() {
    log.info("JATS 全文解析任务已触发，jobId [{}]", XxlJobHelper.getJobId());
    try {
      FullTextParseRunStats stats = commandBus.handle(new JatsFullTextParseCommand());
      log.info(stats.message());
      XxlJobHelper.handleSuccess(stats.message());
    } catch (Exception ex) {
      log.error("JATS 全文解析任务执行失败：{}", ex.getMessage(), ex);
      XxlJobHelper.handleFail("执行失败：" + ex.getMessage());
    }
  }
}
