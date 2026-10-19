package dev.patra.catalog.infra.batch.publication;

import dev.patra.catalog.domain.model.vo.publication.PublicationImportParams;
import dev.patra.catalog.domain.model.vo.publication.PubmedUpdateFileCounts;
import dev.patra.catalog.domain.model.vo.publication.PubmedUpdateImportParams;
import dev.patra.catalog.domain.port.batch.PublicationBatchPort;
import dev.patra.catalog.infra.batch.publication.update.PubmedUpdateContextKeys;
import dev.patra.tool.batch.core.JobOperatorHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/// Publication 批量导入端口适配器。
///
/// **职责**：
///
/// - 实现 Domain 层定义的 `PublicationBatchPort` 接口
/// - 封装 Spring Batch 框架细节，对上层透明
/// - 使用 `JobOperatorHelper` 启动批处理任务
///
/// **单文件模式**：
///
/// 每次调用只处理一个 PubMed Baseline XML 文件：
/// - `params.fileIndex` 指定文件索引（1-1334）
/// - 自动生成完整的下载 URL
///
/// **临时文件下载特性**：
///
/// - 传递 download URL 给 Job
/// - ItemReader 在 open() 时通过 FileDownloadPort 下载文件到临时目录
/// - ItemReader 在 close() 时自动清理临时文件
///
/// **断点续传**：
///
/// 由于不添加时间戳（`addTimestamp=false`），相同参数的 Job 只会执行一次。
/// 如果 Job 失败，重新启动时会从上次中断的位置继续执行。
///
/// **设计说明**：
///
/// - 此类位于 Infrastructure 层，是六边形架构中的 Driven Adapter
/// - Application 层通过 `PublicationBatchPort` 接口调用，无需感知 Spring Batch
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@Component
public class PublicationBatchAdapter implements PublicationBatchPort {

  private final JobOperatorHelper jobOperatorHelper;
  private final JobRepository jobRepository;
  private final Job pubmedBaselineImportJob;
  private final Job pubmedUpdateImportJob;

  /// 构造函数。
  ///
  /// @param jobOperatorHelper Job 启动器
  /// @param pubmedBaselineImportJob PubMed Baseline 导入 Job
  public PublicationBatchAdapter(
      JobOperatorHelper jobOperatorHelper,
      JobRepository jobRepository,
      @Qualifier("pubmedBaselineImportJob") Job pubmedBaselineImportJob,
      @Qualifier("pubmedUpdateImportJob") Job pubmedUpdateImportJob) {
    this.jobOperatorHelper = jobOperatorHelper;
    this.jobRepository = jobRepository;
    this.pubmedBaselineImportJob = pubmedBaselineImportJob;
    this.pubmedUpdateImportJob = pubmedUpdateImportJob;
  }

  /// 启动 PubMed Baseline 文献批量导入任务。
  ///
  /// @param params 导入参数（包含 baseUrl 和 fileIndex）
  /// @return 批处理执行标识符（Spring Batch Job Execution ID）
  @Override
  public Long launchBaselineImport(PublicationImportParams params) {
    String downloadUrl = params.getDownloadUrl();
    String importBatch = extractImportBatch(downloadUrl);
    log.info(
        "启动 PubMed Baseline 导入 Job，文件索引：{}，批次：{}，URL：{}",
        params.fileIndex(),
        importBatch,
        downloadUrl);

    PublicationImportJobParams jobParams =
        PublicationImportJobParams.builder()
            .downloadUrl(downloadUrl)
            .importBatch(importBatch)
            .generation(params.generation())
            .build();

    // 不添加时间戳，相同参数的 Job 只执行一次（支持断点续传）
    Long executionId = jobOperatorHelper.launch(pubmedBaselineImportJob, jobParams, false);
    failFastWhenExecutionFailed(executionId);
    return executionId;
  }

  /// 同步执行一个 updatefiles 文件的导入作业。
  ///
  /// 用不带时间戳的标识参数启动：同参数的作业实例失败后再跑从断点续传。启动前先查同参数的最后一次执行，
  /// 已 COMPLETED 则不再启动、直接读它的计数——覆盖"作业已完成、但进度表 `markCompleted`
  /// 之前进程中断"的情况，否则每次都会撞 `JobInstanceAlreadyCompleteException`。
  ///
  /// @param params 参数
  /// @return 处理计数
  /// @throws IllegalStateException 作业执行失败时抛出
  @Override
  public PubmedUpdateFileCounts launchUpdateImport(PubmedUpdateImportParams params) {
    String downloadUrl = params.file().downloadUrl(params.updateUrl());
    String importBatch = "update-" + params.file().fileName().replace(".xml.gz", "");
    PubmedUpdateImportJobParams jobParams =
        PubmedUpdateImportJobParams.builder()
            .downloadUrl(downloadUrl)
            .importBatch(importBatch)
            .scope(params.scope().name())
            .generation(params.generation())
            .build();

    JobExecution completed =
        jobRepository.getLastJobExecution(
            pubmedUpdateImportJob.getName(), toJobParameters(jobParams));
    if (completed != null && completed.getStatus() == BatchStatus.COMPLETED) {
      log.info("增量同步作业已完成过，直接读取计数：importBatch={}", importBatch);
      return readCounts(completed);
    }

    log.info(
        "启动 PubMed 增量同步 Job：importBatch={}，scope={}，URL：{}",
        importBatch,
        params.scope(),
        downloadUrl);
    Long executionId = jobOperatorHelper.launch(pubmedUpdateImportJob, jobParams, false);
    JobExecution execution =
        jobOperatorHelper
            .findJobExecution(executionId)
            .orElseThrow(
                () -> new IllegalStateException("找不到增量同步作业执行记录：executionId=" + executionId));
    BatchStatus status = execution.getStatus();
    if (status == null || status.isUnsuccessful()) {
      throw new IllegalStateException(
          "PubMed 增量同步任务执行失败，executionId=" + executionId + "，status=" + status);
    }
    return readCounts(execution);
  }

  /// 按 `JobOperatorHelper` 同样的规则（字符串、标识参数、跳过 null）组装 JobParameters，用于查已有实例。
  private static JobParameters toJobParameters(PubmedUpdateImportJobParams params) {
    JobParametersBuilder builder = new JobParametersBuilder();
    builder.addString("downloadUrl", params.getDownloadUrl());
    builder.addString("importBatch", params.getImportBatch());
    builder.addString("scope", params.getScope());
    if (params.getGeneration() != null) {
      builder.addString("generation", params.getGeneration());
    }
    return builder.toJobParameters();
  }

  /// 从 Job 上下文读出计数，缺失的键按 0。
  private static PubmedUpdateFileCounts readCounts(JobExecution execution) {
    ExecutionContext ctx = execution.getExecutionContext();
    return new PubmedUpdateFileCounts(
        ctx.getInt(PubmedUpdateContextKeys.COUNT_CREATED, 0),
        ctx.getInt(PubmedUpdateContextKeys.COUNT_UPDATED, 0),
        ctx.getInt(PubmedUpdateContextKeys.COUNT_DELETED, 0),
        ctx.getInt(PubmedUpdateContextKeys.COUNT_SKIPPED_ABSENT, 0),
        ctx.getInt(PubmedUpdateContextKeys.COUNT_SKIPPED_INVALID, 0),
        ctx.getInt(PubmedUpdateContextKeys.COUNT_DOI_CONFLICTS, 0),
        ctx.getInt(PubmedUpdateContextKeys.COUNT_SKIP_ERRORS, 0));
  }

  /// 从下载 URL 中提取导入批次标识。
  ///
  /// 从 URL 路径中提取文件名（去除 `.xml.gz` 扩展名），并加上 `baseline-` 前缀。
  ///
  /// 示例：`https://.../.../pubmed26n0001.xml.gz` → `baseline-pubmed26n0001`
  ///
  /// @param downloadUrl 下载 URL
  /// @return 导入批次标识
  private static String extractImportBatch(String downloadUrl) {
    String fileName = downloadUrl.substring(downloadUrl.lastIndexOf('/') + 1);
    String baseName = fileName.replace(".xml.gz", "");
    return "baseline-" + baseName;
  }

  /// 对同步失败的 Job 执行结果进行快速失败处理，避免上层误判为成功。
  private void failFastWhenExecutionFailed(Long executionId) {
    jobOperatorHelper
        .findJobExecution(executionId)
        .ifPresent(
            execution -> {
              BatchStatus status = execution.getStatus();
              if (status != null && status.isUnsuccessful()) {
                throw new IllegalStateException(
                    "PubMed Baseline 导入任务执行失败，executionId=" + executionId + "，status=" + status);
              }
            });
  }
}
