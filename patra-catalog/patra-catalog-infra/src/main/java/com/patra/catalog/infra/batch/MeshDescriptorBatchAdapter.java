package com.patra.catalog.infra.batch;

import com.patra.catalog.domain.model.vo.mesh.MeshImportParams;
import com.patra.catalog.domain.port.MeshDescriptorBatchPort;
import com.patra.starter.batch.core.JobLauncherHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.stereotype.Component;

/// MeSH 主题词批量导入端口适配器。
///
/// **职责**：
///
/// - 实现 Domain 层定义的 `MeshDescriptorBatchPort` 接口
/// - 封装 Spring Batch 框架细节，对上层透明
/// - 使用 `JobLauncherHelper` 启动批处理任务
///
/// **设计说明**：
///
/// - 此类位于 Infrastructure 层，是六边形架构中的 Driven Adapter
/// - Application 层通过 `MeshDescriptorBatchPort` 接口调用，无需感知 Spring Batch
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@Component
@RequiredArgsConstructor
public class MeshDescriptorBatchAdapter implements MeshDescriptorBatchPort {

  private final JobLauncherHelper jobLauncherHelper;
  private final Job meshDescriptorImportJob;

  @Override
  public Long launchImport(MeshImportParams params) {
    log.info(
        "启动 MeSH 主题词导入 Job，文件：{}，版本：{}，强制新实例：{}，临时文件：{}",
        params.filePath(),
        params.meshVersion(),
        params.forceNewInstance(),
        params.tempFile());

    MeshImportJobParams jobParams =
        MeshImportJobParams.builder()
            .filePath(params.filePath())
            .meshVersion(params.meshVersion())
            .tempFile(String.valueOf(params.tempFile()))
            .build();

    return jobLauncherHelper.launch(meshDescriptorImportJob, jobParams, params.forceNewInstance());
  }
}
