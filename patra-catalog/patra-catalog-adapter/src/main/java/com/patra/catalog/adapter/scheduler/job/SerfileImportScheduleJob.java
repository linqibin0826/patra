package com.patra.catalog.adapter.scheduler.job;

import com.patra.catalog.adapter.scheduler.config.SerfileDataSourceProperties;
import com.patra.catalog.adapter.scheduler.exception.SerfileConfigurationException;
import com.patra.catalog.adapter.scheduler.util.SerfileFileNameParser;
import com.patra.catalog.app.usecase.serfile.SerfileImportUseCase;
import com.patra.catalog.app.usecase.serfile.command.SerfileImportCommand;
import com.patra.catalog.app.usecase.serfile.dto.SerfileImportResult;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/// NLM Serfile 期刊数据导入定时任务。
///
/// 通过 XXL-Job 控制台手动触发，执行 NLM SerfileBase 期刊数据的批量导入。
/// URL 从配置文件读取，版本号从文件名自动推断。
///
/// **JobHandler 名称**: `serfileImportJob`
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
/// **导入策略**：
///
/// 增量覆盖模式：
///
/// 1. 匹配已有期刊记录时，PubMed 数据完全覆盖 OpenAlex 数据
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
public class SerfileImportScheduleJob {

  private final SerfileImportUseCase serfileImportUseCase;
  private final SerfileDataSourceProperties serfileDataSourceProperties;

  /// 执行 Serfile 期刊数据导入任务。
  ///
  /// **JobHandler 名称**: `serfileImportJob`
  ///
  /// **无需参数**：URL 从配置文件读取，版本号从文件名自动推断。
  @XxlJob("serfileImportJob")
  public void executeSerfileImport() {
    log.info("Serfile 导入任务已触发，jobId [{}]", XxlJobHelper.getJobId());

    try {
      String url = serfileDataSourceProperties.getSerfileUrl();
      String serfileVersion = SerfileFileNameParser.extractVersion(url);
      log.info("Serfile 配置：URL [{}]，版本 [{}]（从文件名推断）", url, serfileVersion);

      SerfileImportCommand command = SerfileImportCommand.of(url, serfileVersion);
      SerfileImportResult result = serfileImportUseCase.importSerfile(command);
      handleSuccess(result);

    } catch (SerfileConfigurationException ex) {
      handleConfigurationError(ex);
    } catch (Exception ex) {
      handleExecutionError(ex);
    }
  }

  /// 处理配置错误。
  private void handleConfigurationError(SerfileConfigurationException ex) {
    log.warn("Serfile 导入任务配置错误：{}", ex.getMessage());
    XxlJobHelper.handleFail("配置错误：" + ex.getMessage());
  }

  /// 处理执行错误。
  private void handleExecutionError(Exception ex) {
    log.error("Serfile 导入任务执行失败：{}", ex.getMessage(), ex);
    XxlJobHelper.handleFail("执行失败：" + ex.getMessage());
  }

  /// 处理成功执行。
  private void handleSuccess(SerfileImportResult result) {
    String message = result.message();
    log.info(message);
    XxlJobHelper.handleSuccess(message);
  }
}
