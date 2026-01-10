package com.patra.catalog.infra.adapter.batch.author;

import com.patra.catalog.domain.model.vo.author.AuthorImportParams;
import com.patra.catalog.domain.port.batch.AuthorBatchPort;
import com.patra.starter.batch.core.JobLauncherHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/// PubMed Computed Authors 批量导入端口适配器。
///
/// **职责**：
///
/// - 实现 Domain 层定义的 `AuthorBatchPort` 接口
/// - 封装 Spring Batch 框架细节，对上层透明
/// - 使用 `JobLauncherHelper` 启动批处理任务
///
/// **流式处理特性**：
///
/// - 传递 download URL 给 Job
/// - ItemReader 在 open() 时建立 HTTP 连接
/// - 无临时文件清理逻辑
///
/// **设计说明**：
///
/// - 此类位于 Infrastructure 层，是六边形架构中的 Driven Adapter
/// - Application 层通过 `AuthorBatchPort` 接口调用，无需感知 Spring Batch
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@Component
public class AuthorBatchAdapter implements AuthorBatchPort {

  private final JobLauncherHelper jobLauncherHelper;
  private final Job authorImportJob;

  /// 构造函数。
  ///
  /// @param jobLauncherHelper Job 启动器
  /// @param authorImportJob Author 导入 Job
  public AuthorBatchAdapter(
      JobLauncherHelper jobLauncherHelper, @Qualifier("authorImportJob") Job authorImportJob) {
    this.jobLauncherHelper = jobLauncherHelper;
    this.authorImportJob = authorImportJob;
  }

  /// 启动 PubMed Computed Authors 导入批处理。
  @Override
  public Long launchAuthorImport(AuthorImportParams params) {
    log.info("启动 PubMed Computed Authors 导入 Job，URL：{}", params.downloadUrl());

    AuthorImportJobParams jobParams =
        AuthorImportJobParams.builder().downloadUrl(params.downloadUrl()).build();

    // 不添加时间戳，相同参数的 Job 只执行一次（支持断点续传）
    return jobLauncherHelper.launch(authorImportJob, jobParams, false);
  }
}
