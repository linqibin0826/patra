package dev.patra.catalog.infra.publication.batch;

import dev.patra.catalog.domain.publication.model.enums.PubmedFileKind;
import dev.patra.catalog.domain.publication.model.enums.PubmedImportScope;
import dev.patra.catalog.domain.publication.model.vo.PubmedFileCounts;
import dev.patra.catalog.domain.publication.model.vo.PubmedFileImportParams;
import dev.patra.catalog.domain.publication.port.batch.PublicationBatchPort;
import dev.patra.catalog.infra.publication.batch.update.PubmedImportContextKeys;
import dev.patra.tool.batch.core.JobOperatorHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/// PubMed 文献批处理端口适配器：同步启动文件导入或本地重算作业，返回计数。
///
/// 作业参数不加时间戳：同参数的作业实例失败后再跑会从断点续传；重处理由调用方换新的 generation 生成新实例。
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@Component
public class PublicationBatchAdapter implements PublicationBatchPort {

  /// 本地重算写进 `import_batch` 的批次名。
  private static final String REPARSE_BATCH = "reparse";

  private final JobOperatorHelper jobOperatorHelper;
  private final JobOperator jobOperator;
  private final JobRepository jobRepository;
  private final Job pubmedFileImportJob;
  private final Job pubmedReparseJob;

  /// 创建适配器。
  ///
  /// @param jobOperatorHelper 作业启动器
  /// @param jobOperator 作业操作器（恢复残留执行）
  /// @param jobRepository 作业仓库（查已有执行）
  /// @param pubmedFileImportJob 文件导入作业
  /// @param pubmedReparseJob 本地重算作业
  public PublicationBatchAdapter(
      JobOperatorHelper jobOperatorHelper,
      JobOperator jobOperator,
      JobRepository jobRepository,
      @Qualifier("pubmedFileImportJob") Job pubmedFileImportJob,
      @Qualifier("pubmedReparseJob") Job pubmedReparseJob) {
    this.jobOperatorHelper = jobOperatorHelper;
    this.jobOperator = jobOperator;
    this.jobRepository = jobRepository;
    this.pubmedFileImportJob = pubmedFileImportJob;
    this.pubmedReparseJob = pubmedReparseJob;
  }

  /// 同步处理一个 PubMed 文件。
  ///
  /// 启动前先查同参数的最后一次执行：已 COMPLETED 则不再启动、直接读它的计数（覆盖「作业已完成、但进度表
  /// `markCompleted` 之前进程中断」的情况）；仍在运行的只能是进程被杀后的残留，先 recover 再续跑。
  ///
  /// @param params 文件与范围
  /// @return 处理计数
  /// @throws IllegalStateException 作业执行失败时
  @Override
  public PubmedFileCounts launchFileImport(PubmedFileImportParams params) {
    String downloadUrl = params.file().downloadUrl(params.baseUrl());
    String prefix = params.file().kind() == PubmedFileKind.BASELINE ? "baseline-" : "update-";
    PubmedFileImportJobParams jobParams =
        PubmedFileImportJobParams.builder()
            .downloadUrl(downloadUrl)
            .importBatch(prefix + params.file().fileName().replace(".xml.gz", ""))
            .scope(params.scope().name())
            .generation(params.generation())
            .build();
    return launch(pubmedFileImportJob, jobParams);
  }

  /// 同步执行本地重算。
  ///
  /// @param generation 作业 generation
  /// @return 处理计数
  /// @throws IllegalStateException 作业执行失败时
  @Override
  public PubmedFileCounts launchReparse(String generation) {
    PubmedFileImportJobParams jobParams =
        PubmedFileImportJobParams.builder()
            .importBatch(REPARSE_BATCH)
            .scope(PubmedImportScope.EXISTING_ONLY.name())
            .reparse(Boolean.TRUE.toString())
            .generation(generation)
            .build();
    return launch(pubmedReparseJob, jobParams);
  }

  /// 启动作业并等待结束，失败时抛出。
  ///
  /// @param job 作业
  /// @param jobParams 作业参数
  /// @return 计数
  private PubmedFileCounts launch(Job job, PubmedFileImportJobParams jobParams) {
    JobExecution last =
        jobRepository.getLastJobExecution(job.getName(), toJobParameters(jobParams));
    if (last != null && last.getStatus() == BatchStatus.COMPLETED) {
      log.info("作业已完成过，直接读取计数：{}，importBatch={}", job.getName(), jobParams.getImportBatch());
      return readCounts(last);
    }
    if (last != null && last.getStatus() != null && last.getStatus().isRunning()) {
      // 外层任务锁保证同一时刻只有一个实例，查到仍在运行的执行只能是进程被杀后的残留
      log.warn("作业有残留的运行中执行，标记为失败后续跑：{}，importBatch={}", job.getName(), jobParams.getImportBatch());
      jobOperator.recover(last);
    }

    log.info(
        "启动 {}：importBatch={}，scope={}，URL：{}",
        job.getName(),
        jobParams.getImportBatch(),
        jobParams.getScope(),
        jobParams.getDownloadUrl());
    Long executionId = jobOperatorHelper.launch(job, jobParams, false);
    JobExecution execution =
        jobOperatorHelper
            .findJobExecution(executionId)
            .orElseThrow(() -> new IllegalStateException("找不到作业执行记录：executionId=" + executionId));
    BatchStatus status = execution.getStatus();
    if (status == null || status.isUnsuccessful()) {
      throw new IllegalStateException(
          "PubMed 导入作业执行失败，executionId=" + executionId + "，status=" + status);
    }
    return readCounts(execution);
  }

  /// 按 `JobOperatorHelper` 同样的规则（字符串、标识参数、跳过 null）组装 JobParameters，用于查已有实例。
  ///
  /// @param params 作业参数
  /// @return JobParameters
  private static JobParameters toJobParameters(PubmedFileImportJobParams params) {
    JobParametersBuilder builder = new JobParametersBuilder();
    addIfPresent(builder, "downloadUrl", params.getDownloadUrl());
    addIfPresent(builder, "importBatch", params.getImportBatch());
    addIfPresent(builder, "scope", params.getScope());
    addIfPresent(builder, "reparse", params.getReparse());
    addIfPresent(builder, "generation", params.getGeneration());
    return builder.toJobParameters();
  }

  /// 值非空时加一个字符串标识参数。
  ///
  /// @param builder 构建器
  /// @param key 键
  /// @param value 值，可空
  private static void addIfPresent(JobParametersBuilder builder, String key, String value) {
    if (value != null) {
      builder.addString(key, value);
    }
  }

  /// 从作业上下文读出计数，缺失的键按 0。
  ///
  /// @param execution 作业执行
  /// @return 计数
  private static PubmedFileCounts readCounts(JobExecution execution) {
    ExecutionContext ctx = execution.getExecutionContext();
    return new PubmedFileCounts(
        ctx.getInt(PubmedImportContextKeys.COUNT_CREATED, 0),
        ctx.getInt(PubmedImportContextKeys.COUNT_UPDATED, 0),
        ctx.getInt(PubmedImportContextKeys.COUNT_DELETED, 0),
        ctx.getInt(PubmedImportContextKeys.COUNT_SKIPPED_ABSENT, 0),
        ctx.getInt(PubmedImportContextKeys.COUNT_SKIPPED_INVALID, 0),
        ctx.getInt(PubmedImportContextKeys.COUNT_DOI_CONFLICTS, 0),
        ctx.getInt(PubmedImportContextKeys.COUNT_SKIP_ERRORS, 0),
        ctx.getInt(PubmedImportContextKeys.COUNT_SKIPPED_STALE, 0));
  }
}
