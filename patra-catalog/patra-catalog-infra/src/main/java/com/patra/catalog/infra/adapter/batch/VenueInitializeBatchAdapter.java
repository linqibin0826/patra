package com.patra.catalog.infra.adapter.batch;

import com.patra.catalog.domain.model.vo.venue.VenueInitializeParams;
import com.patra.catalog.domain.port.batch.VenueInitializeBatchPort;
import com.patra.catalog.infra.batch.venue.VenueInitializeJobParams;
import com.patra.starter.batch.core.JobLauncherHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.stereotype.Component;

/// OpenAlex Venue 批量导入端口适配器。
///
/// **职责**：
///
/// - 实现 Domain 层定义的 `VenueInitializeBatchPort` 接口
/// - 封装 Spring Batch 框架细节，对上层透明
/// - 使用 `JobLauncherHelper` 启动批处理任务
///
/// **流式处理特性**：
///
/// - 传递分区 URL 列表给 Job
/// - ItemReader 按需从远程 URL 流式下载
/// - 无临时文件清理逻辑
///
/// **设计说明**：
///
/// - 此类位于 Infrastructure 层，是六边形架构中的 Driven Adapter
/// - Application 层通过 `VenueInitializeBatchPort` 接口调用，无需感知 Spring Batch
///
/// **与 MeshDescriptorBatchAdapter 的差异**：
///
/// - 参数类型为 `VenueInitializeParams`（包含多分区 URL 列表）
/// - Job 参数包含 `partitionUrls`（逗号分隔）和 `partitionCount`
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@Component
@RequiredArgsConstructor
public class VenueInitializeBatchAdapter implements VenueInitializeBatchPort {

  private final JobLauncherHelper jobLauncherHelper;
  private final Job venueInitializeJob;

  @Override
  public Long launchImport(VenueInitializeParams params) {
    log.info("启动 OpenAlex Venue 导入 Job，分区数量：{}", params.getPartitionCount());

    VenueInitializeJobParams jobParams =
        VenueInitializeJobParams.builder()
            .partitionUrls(params.getPartitionUrlsAsString())
            .partitionCount(params.getPartitionCount())
            .build();

    // 不添加时间戳，相同参数的 Job 只执行一次（支持断点续传）
    return jobLauncherHelper.launch(venueInitializeJob, jobParams, false);
  }
}
