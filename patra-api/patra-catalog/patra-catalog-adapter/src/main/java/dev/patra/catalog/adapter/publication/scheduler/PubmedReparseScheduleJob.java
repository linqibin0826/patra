package dev.patra.catalog.adapter.publication.scheduler;

import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import dev.patra.catalog.app.publication.usecase.sync.PubmedReparseCommand;
import dev.patra.catalog.app.publication.usecase.sync.PubmedReparseResult;
import dev.patra.tool.cqrs.CommandBus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/// PubMed 本地重算的 XXL-Job 入口（handler `pubmedReparseJob`，无参数）：解析器升级后按保存的原文重算，不联网。
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@Component
@RequiredArgsConstructor
public class PubmedReparseScheduleJob {

  private final CommandBus commandBus;

  /// 执行一次本地重算。
  @XxlJob("pubmedReparseJob")
  public void execute() {
    log.info("PubMed 本地重算任务已触发，jobId [{}]", XxlJobHelper.getJobId());
    try {
      PubmedReparseResult result = commandBus.handle(new PubmedReparseCommand());
      log.info(result.message());
      XxlJobHelper.handleSuccess(result.message());
    } catch (Exception ex) {
      log.error("PubMed 本地重算任务执行失败：{}", ex.getMessage(), ex);
      XxlJobHelper.handleFail("执行失败：" + ex.getMessage());
    }
  }
}
