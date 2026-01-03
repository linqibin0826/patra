package com.patra.catalog.infra.adapter.batch.mesh;

import com.patra.catalog.domain.model.vo.mesh.MeshImportParams;
import com.patra.catalog.domain.port.batch.MeshScrBatchPort;
import com.patra.starter.batch.core.JobLauncherHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.stereotype.Component;

/// MeSH SCR 批量导入端口适配器。
///
/// **职责**：
///
/// - 实现 Domain 层定义的 `MeshScrBatchPort` 接口
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
/// - Application 层通过 `MeshScrBatchPort` 接口调用，无需感知 Spring Batch
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@Component
@RequiredArgsConstructor
public class MeshScrBatchAdapter implements MeshScrBatchPort {

  private final JobLauncherHelper jobLauncherHelper;
  private final Job meshScrImportJob;

  @Override
  public Long launchImport(MeshImportParams params) {
    log.info("启动 MeSH SCR 导入 Job，URL：{}，版本：{}", params.downloadUrl(), params.meshVersion());

    MeshImportJobParams jobParams =
        MeshImportJobParams.builder()
            .downloadUrl(params.downloadUrl())
            .meshVersion(params.meshVersion())
            .build();

    // 不添加时间戳，相同参数的 Job 只执行一次（支持断点续传）
    return jobLauncherHelper.launch(meshScrImportJob, jobParams, false);
  }
}
