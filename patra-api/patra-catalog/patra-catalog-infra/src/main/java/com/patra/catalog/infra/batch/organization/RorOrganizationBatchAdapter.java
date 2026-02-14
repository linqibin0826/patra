package com.patra.catalog.infra.batch.organization;

import com.patra.catalog.domain.model.vo.organization.RorImportParams;
import com.patra.catalog.domain.port.batch.RorOrganizationBatchPort;
import com.patra.starter.batch.core.JobOperatorHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.job.Job;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/// ROR 机构批量导入端口适配器。
///
/// **职责**：
///
/// - 实现 Domain 层定义的 `RorOrganizationBatchPort` 接口
/// - 封装 Spring Batch 框架细节，对上层透明
/// - 使用 `JobOperatorHelper` 启动批处理任务
///
/// **临时文件下载特性**：
///
/// - 传递 download URL 给 Job
/// - ItemReader 在 open() 时通过 FileDownloadPort 下载文件到临时目录
/// - ItemReader 在 close() 时自动清理临时文件
///
/// **设计说明**：
///
/// - 此类位于 Infrastructure 层，是六边形架构中的 Driven Adapter
/// - Application 层通过 `RorOrganizationBatchPort` 接口调用，无需感知 Spring Batch
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@Component
public class RorOrganizationBatchAdapter implements RorOrganizationBatchPort {

  private final JobOperatorHelper jobOperatorHelper;
  private final Job rorOrganizationImportJob;

  /// 构造函数。
  ///
  /// @param jobOperatorHelper Job 启动器
  /// @param rorOrganizationImportJob ROR 机构导入 Job
  public RorOrganizationBatchAdapter(
      JobOperatorHelper jobOperatorHelper,
      @Qualifier("rorOrganizationImportJob") Job rorOrganizationImportJob) {
    this.jobOperatorHelper = jobOperatorHelper;
    this.rorOrganizationImportJob = rorOrganizationImportJob;
  }

  /// 启动 ROR 机构导入批处理。
  @Override
  public Long launchImport(RorImportParams params) {
    log.info("启动 ROR 机构导入 Job，URL：{}，版本：{}", params.downloadUrl(), params.rorVersion());
    return launchJob(rorOrganizationImportJob, params);
  }

  /// 启动指定的 Job。
  ///
  /// @param job Job 实例
  /// @param params 导入参数
  /// @return Job Execution ID
  private Long launchJob(Job job, RorImportParams params) {
    RorOrganizationJobParams jobParams =
        RorOrganizationJobParams.builder()
            .downloadUrl(params.downloadUrl())
            .rorVersion(params.rorVersion())
            .build();

    // 不添加时间戳，相同参数的 Job 只执行一次（支持断点续传）
    return jobOperatorHelper.launch(job, jobParams, false);
  }
}
