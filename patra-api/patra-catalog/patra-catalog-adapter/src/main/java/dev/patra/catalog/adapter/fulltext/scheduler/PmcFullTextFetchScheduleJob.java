package dev.patra.catalog.adapter.fulltext.scheduler;

import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import dev.patra.catalog.app.fulltext.usecase.fetch.FullTextFetchRunStats;
import dev.patra.catalog.app.fulltext.usecase.fetch.PmcFullTextFetchCommand;
import dev.patra.tool.cqrs.CommandBus;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/// PMC 全文抓取的 XXL-Job 入口（handler `pmcFullTextFetchJob`）。
///
/// 参数可省；手动重抓写 `publicationIds=1,2,3`（整段按逗号切成 ID 列表，不复用 baseline 的键值对解析）。
/// 由增量同步任务作为子任务触发，另有一条每小时的兜底任务；建任务的步骤见
/// `docs/patra/runbooks/v0.11-fulltext-runbook.md`。
///
/// @author linqibin
/// @since 0.11.0
@Slf4j
@Component
@RequiredArgsConstructor
public class PmcFullTextFetchScheduleJob {

  private static final String IDS_PREFIX = "publicationIds=";

  private final CommandBus commandBus;

  /// 执行一次抓取。
  @XxlJob("pmcFullTextFetchJob")
  public void execute() {
    log.info("PMC 全文抓取任务已触发，jobId [{}]", XxlJobHelper.getJobId());
    List<Long> ids;
    try {
      ids = parsePublicationIds(XxlJobHelper.getJobParam());
    } catch (IllegalArgumentException ex) {
      log.warn("PMC 全文抓取任务参数错误：{}", ex.getMessage());
      XxlJobHelper.handleFail("参数错误：" + ex.getMessage());
      return;
    }

    try {
      FullTextFetchRunStats stats = commandBus.handle(new PmcFullTextFetchCommand(ids));
      log.info(stats.message());
      XxlJobHelper.handleSuccess(stats.message());
    } catch (Exception ex) {
      log.error("PMC 全文抓取任务执行失败：{}", ex.getMessage(), ex);
      XxlJobHelper.handleFail("执行失败：" + ex.getMessage());
    }
  }

  /// 解析 `publicationIds=1,2,3`，空参数返回空列表。
  private static List<Long> parsePublicationIds(String jobParam) {
    if (jobParam == null || jobParam.isBlank()) {
      return List.of();
    }
    String param = jobParam.trim();
    if (!param.startsWith(IDS_PREFIX)) {
      throw new IllegalArgumentException("只支持 publicationIds=1,2,3，实际：" + jobParam);
    }
    try {
      return Arrays.stream(param.substring(IDS_PREFIX.length()).split(","))
          .map(String::trim)
          .filter(s -> !s.isEmpty())
          .map(Long::valueOf)
          .toList();
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("publicationIds 必须是逗号分隔的整数，实际：" + jobParam, e);
    }
  }
}
