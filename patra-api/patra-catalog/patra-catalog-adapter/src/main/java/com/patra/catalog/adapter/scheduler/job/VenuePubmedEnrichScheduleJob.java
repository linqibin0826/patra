package com.patra.catalog.adapter.scheduler.job;

import com.patra.catalog.adapter.scheduler.config.SerfileDataSourceProperties;
import com.patra.catalog.adapter.scheduler.exception.SerfileConfigurationException;
import com.patra.catalog.adapter.scheduler.util.SerfileFileNameParser;
import com.patra.catalog.app.usecase.venue.pubmed.command.VenuePubmedEnrichCommand;
import com.patra.catalog.app.usecase.venue.pubmed.dto.VenuePubmedEnrichResult;
import com.patra.common.cqrs.CommandBus;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/// PubMed Venue 数据富化定时任务。
///
/// 通过 XXL-Job 控制台手动触发，执行 NLM SerfileBase 期刊数据的富化。
/// URL 从配置文件读取，版本号从文件名自动推断。
///
/// **JobHandler 名称**: `venuePubmedEnrichJob`
///
/// **配置要求**：
///
/// ```yaml
/// patra:
///   catalog:
///     serfile:
///       serfile-url: https://ftp.ncbi.nlm.nih.gov/pubmed/Serfile/serfilebase2025.xml
/// ```
///
/// **富化策略**：
///
/// 增量覆盖模式：
///
/// 1. 匹配已有期刊记录时，PubMed 数据完全覆盖 OpenAlex 数据
/// 2. 匹配策略：ISSN-L → NLM ID → ISSN（降级策略）
/// 3. PubMed 独有期刊将创建新的 VenueAggregate 记录
///
/// **运行建议**：
///
/// - 首次富化预计耗时 10-30 分钟（取决于网络和数据库性能）
/// - 建议在低峰期手动触发
/// - 超时时间建议设置为 60 分钟
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@Component
@RequiredArgsConstructor
public class VenuePubmedEnrichScheduleJob {

  private final CommandBus commandBus;
  private final SerfileDataSourceProperties serfileDataSourceProperties;

  /// 执行 PubMed Venue 数据富化任务。
  ///
  /// **JobHandler 名称**: `venuePubmedEnrichJob`
  ///
  /// **无需参数**：URL 从配置文件读取，版本号从文件名自动推断。
  @XxlJob("venuePubmedEnrichJob")
  public void executeVenuePubmedEnrich() {
    log.info("PubMed Venue 富化任务已触发，jobId [{}]", XxlJobHelper.getJobId());

    try {
      String url = serfileDataSourceProperties.getSerfileUrl();
      String serfileVersion = SerfileFileNameParser.extractVersion(url);
      log.info("Serfile 配置：URL [{}]，版本 [{}]（从文件名推断）", url, serfileVersion);

      VenuePubmedEnrichResult result =
          commandBus.handle(VenuePubmedEnrichCommand.of(url, serfileVersion));
      handleSuccess(result);

    } catch (SerfileConfigurationException ex) {
      handleConfigurationError(ex);
    } catch (Exception ex) {
      handleExecutionError(ex);
    }
  }

  /// 处理配置错误。
  private void handleConfigurationError(SerfileConfigurationException ex) {
    log.warn("PubMed Venue 富化任务配置错误：{}", ex.getMessage());
    XxlJobHelper.handleFail("配置错误：" + ex.getMessage());
  }

  /// 处理执行错误。
  private void handleExecutionError(Exception ex) {
    log.error("PubMed Venue 富化任务执行失败：{}", ex.getMessage(), ex);
    XxlJobHelper.handleFail("执行失败：" + ex.getMessage());
  }

  /// 处理成功执行。
  private void handleSuccess(VenuePubmedEnrichResult result) {
    String message = result.message();
    log.info(message);
    XxlJobHelper.handleSuccess(message);
  }
}
