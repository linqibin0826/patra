package dev.patra.catalog.adapter.scheduler.job;

import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import dev.patra.catalog.adapter.scheduler.config.PubmedDataSourceProperties;
import dev.patra.catalog.app.usecase.publication.baseline.command.PublicationBaselineImportCommand;
import dev.patra.catalog.app.usecase.publication.baseline.dto.PublicationBaselineImportResult;
import dev.patra.tool.cqrs.CommandBus;
import java.util.HashSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/// PubMed Baseline 文献导入定时任务。
///
/// 通过 XXL-Job 控制台手动触发或调度执行，进行 PubMed Baseline 文献批量导入。
///
/// **单文件模式**：
///
/// 每次 Job 执行只处理一个 XML 文件，通过 `fileIndex` 参数指定（1-1334）。
/// 这种设计支持：
///
/// - 测试环境：手动指定 `fileIndex=1` 测试第 1 个文件
/// - 生产环境：通过 XXL-Job 循环调度批量导入 1334 个文件
///
/// **调度参数**：
///
/// ```
/// fileIndex=1                      # 导入第 1 个文件（pubmed26n0001.xml.gz）
/// fileIndex=42                     # 导入第 42 个文件（pubmed26n0042.xml.gz）
/// fileIndex=1333,generation=v0.6   # 以 v0.6 代次重跑第 1333 个文件
/// ```
///
/// `generation` 可选，是 Spring Batch 的 identifying 参数：缺省时行为与既有调度完全一致
/// （相同 fileIndex 复用同一 JobInstance，支持断点续传）；传值时生成新的 JobInstance。
///
/// **它只解除 JobInstance 复用限制，不具备"重导"能力**：用于换库重建后重跑同一文件。
/// 在已有数据的库上重跑，记录会被 Writer 按 PMID/DOI 全量判重跳过，数据不会变化
/// —— 此时 Job 仍报 SUCCESS，容易误判为"重导成功"。
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
/// - 2025 Baseline 共 1334 个文件
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
  /// **参数格式**: `fileIndex=N[,generation=STR]`，其中 N 为 `>= 1` 的整数
  /// （不校验上限——baseline 文件总数逐年变化，越界由下载 404 暴露）
  ///
  /// **示例**:
  /// - `fileIndex=1` - 导入第 1 个文件
  /// - `fileIndex=100` - 导入第 100 个文件
  /// - `fileIndex=1333,generation=v0.6` - 以 v0.6 代次重跑第 1333 个文件
  ///
  /// `generation` 仅解除 JobInstance 复用限制（换库重建后重跑同一文件），
  /// 不会让已有数据的库真的重导 —— 记录会被 Writer 按 PMID/DOI 全量跳过。
  @XxlJob("pubmedBaselineImportJob")
  public void execute() {
    log.info("PubMed Baseline 导入任务已触发，jobId [{}]", XxlJobHelper.getJobId());

    try {
      ParsedJobParam parsed = parseJobParam();
      log.info(
          "PubMed Baseline 配置：baseUrl [{}]，fileIndex [{}]，generation [{}]",
          pubmedDataSourceProperties.getBaselineUrl(),
          parsed.fileIndex(),
          parsed.generation());

      executeImport(parsed.fileIndex(), parsed.generation());

    } catch (IllegalArgumentException ex) {
      handleParameterError(ex);
    } catch (Exception ex) {
      handleExecutionError(ex);
    }
  }

  /// 执行导入（供测试调用）。
  ///
  /// @param fileIndex 文件索引
  /// @param generation 代次标识（可空）
  void executeImport(int fileIndex, String generation) {
    PublicationBaselineImportResult result =
        commandBus.handle(
            PublicationBaselineImportCommand.of(
                pubmedDataSourceProperties.getBaselineUrl(), fileIndex, generation));
    handleSuccess(result.message());
  }

  /// 解析调度参数 `fileIndex=N[,generation=STR]`。
  ///
  /// 按逗号切分为若干 `key=value` 组，仅接受 `fileIndex`（必填）与 `generation`（可选）。
  /// 出现未知 key 或重复 key 时抛出异常，避免控制台手打失误被静默忽略。
  ///
  /// @return 解析结果
  /// @throws IllegalArgumentException 当参数缺失、格式无效、包含未知 key 或重复 key 时
  private ParsedJobParam parseJobParam() {
    String jobParam = XxlJobHelper.getJobParam();
    if (jobParam == null || jobParam.isBlank()) {
      throw new IllegalArgumentException("缺少 fileIndex 参数，格式：fileIndex=N[,generation=STR]");
    }

    Integer fileIndex = null;
    String generation = null;
    Set<String> seenKeys = new HashSet<>();
    for (String pair : jobParam.split(",")) {
      String[] keyValue = pair.split("=", 2);
      if (keyValue.length != 2 || keyValue[0].isBlank()) {
        throw new IllegalArgumentException("参数格式无效，期望：fileIndex=N[,generation=STR]，实际：" + jobParam);
      }
      String key = keyValue[0].trim();
      String value = keyValue[1].trim();
      if (!seenKeys.add(key)) {
        throw new IllegalArgumentException("参数 key [%s] 重复，实际：%s".formatted(key, jobParam));
      }
      switch (key) {
        case "fileIndex" -> fileIndex = parseFileIndex(value);
        case "generation" -> generation = value.isBlank() ? null : value;
        default ->
            throw new IllegalArgumentException(
                "未知参数 key [%s]，仅支持 fileIndex 与 generation，实际：%s".formatted(key, jobParam));
      }
    }

    if (fileIndex == null) {
      throw new IllegalArgumentException("缺少 fileIndex 参数，格式：fileIndex=N[,generation=STR]");
    }
    return ParsedJobParam.of(fileIndex, generation);
  }

  /// 解析 fileIndex 取值。
  ///
  /// 仅校验下限（`>= 1`）：baseline 文件总数逐年变化，写死上限会变成年度维护债，
  /// 越界的 fileIndex 交由下载阶段的 404 暴露。
  ///
  /// @param value fileIndex 的字符串取值
  /// @return 文件索引
  /// @throws IllegalArgumentException 当取值不是整数或小于 1 时
  private static int parseFileIndex(String value) {
    int fileIndex;
    try {
      fileIndex = Integer.parseInt(value);
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("fileIndex 必须是整数，实际：'" + value + "'", e);
    }
    if (fileIndex < 1) {
      throw new IllegalArgumentException("fileIndex 必须 >= 1，实际：'" + value + "'");
    }
    return fileIndex;
  }

  /// 调度参数解析结果。
  ///
  /// @param fileIndex 文件索引
  /// @param generation 代次标识（可空）
  record ParsedJobParam(int fileIndex, String generation) {

    /// 创建调度参数解析结果。
    ///
    /// @param fileIndex 文件索引
    /// @param generation 代次标识（可空）
    /// @return 解析结果
    static ParsedJobParam of(int fileIndex, String generation) {
      return new ParsedJobParam(fileIndex, generation);
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
