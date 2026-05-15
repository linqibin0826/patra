package dev.linqibin.patra.catalog.infra.batch.mesh;

import com.patra.starter.batch.core.JobOperatorHelper;
import dev.linqibin.patra.catalog.domain.model.vo.mesh.MeshImportParams;
import dev.linqibin.patra.catalog.domain.port.batch.MeshBatchPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.job.Job;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/// MeSH 批量导入端口适配器。
///
/// **职责**：
///
/// - 实现 Domain 层定义的 `MeshBatchPort` 接口
/// - 封装 Spring Batch 框架细节，对上层透明
/// - 为不同的导入类型提供语义明确的方法实现
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
/// - Application 层通过 `MeshBatchPort` 接口调用，无需感知 Spring Batch
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@Component
public class MeshBatchAdapter implements MeshBatchPort {

  private final JobOperatorHelper jobOperatorHelper;
  private final Job meshDescriptorImportJob;
  private final Job meshScrImportJob;

  /// 构造函数。
  ///
  /// @param jobOperatorHelper Job 启动器
  /// @param meshDescriptorImportJob Descriptor 导入 Job
  /// @param meshScrImportJob SCR 导入 Job
  public MeshBatchAdapter(
      JobOperatorHelper jobOperatorHelper,
      @Qualifier("meshDescriptorImportJob") Job meshDescriptorImportJob,
      @Qualifier("meshScrImportJob") Job meshScrImportJob) {
    this.jobOperatorHelper = jobOperatorHelper;
    this.meshDescriptorImportJob = meshDescriptorImportJob;
    this.meshScrImportJob = meshScrImportJob;
  }

  /// 启动 MeSH 主题词导入批处理。
  @Override
  public Long launchDescriptorImport(MeshImportParams params) {
    log.info("启动 MeSH 主题词导入 Job，URL：{}，版本：{}", params.downloadUrl(), params.meshVersion());
    return launchJob(meshDescriptorImportJob, params);
  }

  /// 启动 MeSH SCR 导入批处理。
  @Override
  public Long launchScrImport(MeshImportParams params) {
    log.info("启动 MeSH SCR 导入 Job，URL：{}，版本：{}", params.downloadUrl(), params.meshVersion());
    return launchJob(meshScrImportJob, params);
  }

  /// 启动指定的 Job。
  ///
  /// @param job Job 实例
  /// @param params 导入参数
  /// @return Job Execution ID
  private Long launchJob(Job job, MeshImportParams params) {
    MeshImportJobParams jobParams =
        MeshImportJobParams.builder()
            .downloadUrl(params.downloadUrl())
            .meshVersion(params.meshVersion())
            .build();

    // 不添加时间戳，相同参数的 Job 只执行一次（支持断点续传）
    return jobOperatorHelper.launch(job, jobParams, false);
  }
}
