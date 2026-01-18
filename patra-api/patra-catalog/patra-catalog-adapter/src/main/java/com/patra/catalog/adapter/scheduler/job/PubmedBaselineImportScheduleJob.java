package com.patra.catalog.adapter.scheduler.job;

import com.patra.catalog.adapter.scheduler.config.PubmedDataSourceProperties;
import com.patra.catalog.app.usecase.publication.baseline.command.PublicationBaselineImportCommand;
import com.patra.catalog.app.usecase.publication.baseline.dto.PublicationBaselineImportResult;
import com.patra.common.cqrs.CommandBus;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/// PubMed Baseline 文献导入定时任务。
///
/// 通过 XXL-Job 控制台手动触发或调度执行，进行 PubMed Baseline 文献批量导入。
///
/// **单文件模式**：
///
/// 每次 Job 执行只处理一个 XML 文件，通过 `fileIndex` 参数指定（1-1274）。
/// 这种设计支持：
///
/// - 测试环境：手动指定 `fileIndex=1` 测试第 1 个文件
/// - 生产环境：通过 XXL-Job 循环调度批量导入 1274 个文件
///
/// **调度参数**：
///
/// ```
/// fileIndex=1      # 导入第 1 个文件（pubmed25n0001.xml.gz）
/// fileIndex=42     # 导入第 42 个文件（pubmed25n0042.xml.gz）
/// ```
///
/// **配置要求**：
///
/// ```yaml
/// patra:
///   catalog:
///     pubmed:
///       baseline-url: https://ftp.ncbi.nlm.nih.gov/pubmed/baseline/
/// ```
///
/// **数据规模**：
///
/// - 2025 Baseline 共 1274 个文件
/// - 每文件约 30,000 条记录
/// - 总计约 3,700 万条
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@Component
@RequiredArgsConstructor
public class PubmedBaselineImportScheduleJob {

  private final CommandBus commandBus;
  private final PubmedDataSourceProperties pubmedDataSourceProperties;

  /// 执行 PubMed Baseline 文献导入任务。
  ///
  /// **JobHandler 名称**: `pubmedBaselineImportJob`
  ///
  /// **参数格式**: `fileIndex=N`，其中 N 为 1-1274 的整数
  ///
  /// **示例**:
  /// - `fileIndex=1` - 导入第 1 个文件
  /// - `fileIndex=100` - 导入第 100 个文件
  @XxlJob("pubmedBaselineImportJob")
  public void execute() {
    log.info("PubMed Baseline 导入任务已触发，jobId [{}]", XxlJobHelper.getJobId());

    try {
      // 解析 fileIndex 参数
      int fileIndex = parseFileIndex();
      log.info(
          "PubMed Baseline 配置：baseUrl [{}]，fileIndex [{}]",
          pubmedDataSourceProperties.getBaselineUrl(),
          fileIndex);

      executeImport(fileIndex);

    } catch (IllegalArgumentException ex) {
      handleParameterError(ex);
    } catch (Exception ex) {
      handleExecutionError(ex);
    }
  }

  /// 执行导入（供测试调用）。
  ///
  /// @param fileIndex 文件索引
  void executeImport(int fileIndex) {
    PublicationBaselineImportResult result =
        commandBus.handle(
            PublicationBaselineImportCommand.of(
                pubmedDataSourceProperties.getBaselineUrl(), fileIndex));
    handleSuccess(result.message());
  }

  /// 解析 fileIndex 参数。
  ///
  /// @return 文件索引
  /// @throws IllegalArgumentException 当参数格式无效时
  private int parseFileIndex() {
    String jobParam = XxlJobHelper.getJobParam();
    if (jobParam == null || jobParam.isBlank()) {
      throw new IllegalArgumentException("缺少 fileIndex 参数，格式：fileIndex=N（N 为 1-1274）");
    }

    // 解析 fileIndex=N 格式
    String[] parts = jobParam.split("=");
    if (parts.length != 2 || !"fileIndex".equals(parts[0].trim())) {
      throw new IllegalArgumentException("参数格式无效，期望：fileIndex=N（N 为 1-1274），实际：" + jobParam);
    }

    try {
      return Integer.parseInt(parts[1].trim());
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("fileIndex 必须是整数，实际：" + parts[1], e);
    }
  }

  /// 处理参数错误。
  private void handleParameterError(IllegalArgumentException ex) {
    log.warn("PubMed Baseline 导入任务参数错误：{}", ex.getMessage());
    XxlJobHelper.handleFail("参数错误：" + ex.getMessage());
  }

  /// 处理执行错误。
  private void handleExecutionError(Exception ex) {
    log.error("PubMed Baseline 导入任务执行失败：{}", ex.getMessage(), ex);
    XxlJobHelper.handleFail("执行失败：" + ex.getMessage());
  }

  /// 处理成功执行。
  private void handleSuccess(String message) {
    log.info(message);
    XxlJobHelper.handleSuccess(message);
  }
}
