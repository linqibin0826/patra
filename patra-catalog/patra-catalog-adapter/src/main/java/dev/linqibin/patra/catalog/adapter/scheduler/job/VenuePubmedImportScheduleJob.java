package dev.linqibin.patra.catalog.adapter.scheduler.job;

import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import dev.linqibin.commons.cqrs.CommandBus;
import dev.linqibin.patra.catalog.adapter.scheduler.config.LsiouDataSourceProperties;
import dev.linqibin.patra.catalog.adapter.scheduler.exception.LsiouConfigurationException;
import dev.linqibin.patra.catalog.adapter.scheduler.util.LsiouFileNameParser;
import dev.linqibin.patra.catalog.app.usecase.venue.pubmed.command.VenuePubmedImportCommand;
import dev.linqibin.patra.catalog.app.usecase.venue.pubmed.dto.VenuePubmedImportResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/// PubMed Venue 数据导入定时任务。
///
/// 通过 XXL-Job 控制台手动触发，执行 NLM LSIOU 期刊数据的导入。
/// URL 从配置文件读取，版本号从文件名自动推断。
///
/// **JobHandler 名称**: `venuePubmedImportJob`
///
/// **配置要求**：
///
/// ```yaml
/// patra:
///   catalog:
///     lsiou:
///       url: ftp://ftp.nlm.nih.gov/online/journals/lsi2024.xml
/// ```
///
/// **回退规则**：
///
/// - 若主目录文件不存在，将自动回退到 `/online/journals/archive`
///
/// **导入策略**：
///
/// 增量覆盖模式：
///
/// 1. 匹配已有期刊记录时，PubMed 数据覆盖旧数据
/// 2. 匹配策略：ISSN-L → NLM ID → ISSN（降级策略）
/// 3. PubMed 独有期刊将创建新的 VenueAggregate 记录
///
/// **运行建议**：
///
/// - 首次导入预计耗时 10-30 分钟（取决于网络和数据库性能）
/// - 建议在低峰期手动触发
/// - 超时时间建议设置为 60 分钟
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@Component
@RequiredArgsConstructor
public class VenuePubmedImportScheduleJob {

  private final CommandBus commandBus;
  private final LsiouDataSourceProperties lsiouDataSourceProperties;

  /// 执行 PubMed Venue 数据导入任务。
  ///
  /// **JobHandler 名称**: `venuePubmedImportJob`
  ///
  /// **无需参数**：URL 从配置文件读取，版本号从文件名自动推断。
  @XxlJob("venuePubmedImportJob")
  public void executeVenuePubmedImport() {
    log.info("PubMed Venue 导入任务已触发，jobId [{}]", XxlJobHelper.getJobId());

    try {
      String url = lsiouDataSourceProperties.getUrl();
      String lsiouVersion = LsiouFileNameParser.extractVersion(url);
      log.info("LSIOU 配置：URL [{}]，版本 [{}]（从文件名推断）", url, lsiouVersion);

      VenuePubmedImportResult result =
          commandBus.handle(VenuePubmedImportCommand.of(url, lsiouVersion));
      handleSuccess(result);

    } catch (LsiouConfigurationException ex) {
      handleConfigurationError(ex);
    } catch (Exception ex) {
      handleExecutionError(ex);
    }
  }

  /// 处理配置错误。
  private void handleConfigurationError(LsiouConfigurationException ex) {
    log.warn("PubMed Venue 导入任务配置错误：{}", ex.getMessage());
    XxlJobHelper.handleFail("配置错误：" + ex.getMessage());
  }

  /// 处理执行错误。
  private void handleExecutionError(Exception ex) {
    log.error("PubMed Venue 导入任务执行失败：{}", ex.getMessage(), ex);
    XxlJobHelper.handleFail("执行失败：" + ex.getMessage());
  }

  /// 处理成功执行。
  private void handleSuccess(VenuePubmedImportResult result) {
    String message = result.message();
    log.info(message);
    XxlJobHelper.handleSuccess(message);
  }
}
