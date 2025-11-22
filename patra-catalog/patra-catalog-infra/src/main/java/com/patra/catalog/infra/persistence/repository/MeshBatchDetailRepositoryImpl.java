package com.patra.catalog.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.patra.catalog.domain.model.enums.MeshBatchStatus;
import com.patra.catalog.domain.model.valueobject.FailedBatch;
import com.patra.catalog.domain.model.valueobject.MeshImportId;
import com.patra.catalog.domain.port.MeshBatchDetailPort;
import com.patra.catalog.infra.persistence.entity.MeshBatchDetailDO;
import com.patra.catalog.infra.persistence.mapper.MeshBatchDetailMapper;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

/// MeSH 批次详情仓储实现。
///
/// 实现 {@link MeshBatchDetailPort} 接口，提供批次详情查询能力。
///
/// **实现策略**：
///
/// - 使用 MyBatis-Plus LambdaQueryWrapper 进行简单查询
///   - 只读仓储：不提供保存方法，批次详情由导入流程创建
///   - 查询优化：使用索引（idx_batch_detail_import_id、idx_batch_detail_status）
///   - DO到值对象转换：查询后转换为Domain层的FailedBatch值对象
///
/// **技术实现**：
///
/// - findFailedBatches()：WHERE import_id = ? AND status = 'FAILED'
///   - countByStatus()：SELECT COUNT(*) WHERE import_id = ? AND status = ?
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@Repository
@RequiredArgsConstructor
public class MeshBatchDetailRepositoryImpl implements MeshBatchDetailPort {

  private final MeshBatchDetailMapper batchDetailMapper;

  /// 查询指定任务的失败批次。
  ///
  /// @param importId 任务ID
  /// @return 失败批次列表
  @Override
  public List<FailedBatch> findFailedBatches(MeshImportId importId) {
    log.debug("查询失败批次，任务ID：{}", importId.value());

    LambdaQueryWrapper<MeshBatchDetailDO> queryWrapper =
        new LambdaQueryWrapper<MeshBatchDetailDO>()
            .eq(MeshBatchDetailDO::getImportId, importId.value())
            .eq(MeshBatchDetailDO::getStatus, MeshBatchStatus.FAILED.getCode())
            .orderByAsc(MeshBatchDetailDO::getTableName, MeshBatchDetailDO::getBatchNum);

    List<MeshBatchDetailDO> failedBatchDOs = batchDetailMapper.selectList(queryWrapper);

    log.debug("查询到 {} 个失败批次，任务ID：{}", failedBatchDOs.size(), importId.value());

    // 转换为Domain层值对象
    return failedBatchDOs.stream().map(this::toFailedBatch).collect(Collectors.toList());
  }

  /// 统计指定任务的某状态批次数量。
  ///
  /// @param importId 任务ID
  /// @param status 批次状态
  /// @return 批次数量
  @Override
  public Long countByStatus(MeshImportId importId, MeshBatchStatus status) {
    log.debug("统计批次数量，任务ID：{}，状态：{}", importId.value(), status.getDisplayName());

    LambdaQueryWrapper<MeshBatchDetailDO> queryWrapper =
        new LambdaQueryWrapper<MeshBatchDetailDO>()
            .eq(MeshBatchDetailDO::getImportId, importId.value())
            .eq(MeshBatchDetailDO::getStatus, status.getCode());

    Long count = batchDetailMapper.selectCount(queryWrapper);

    log.debug("统计结果：{} 个批次，任务ID：{}，状态：{}", count, importId.value(), status.getDisplayName());

    return count;
  }

  /// 将DO转换为FailedBatch值对象。
  ///
  /// @param batchDetailDO 批次详情DO
  /// @return 失败批次值对象
  private FailedBatch toFailedBatch(MeshBatchDetailDO batchDetailDO) {
    return FailedBatch.builder()
        .batchId(batchDetailDO.getId())
        .tableName(batchDetailDO.getTableName())
        .batchNum(batchDetailDO.getBatchNum())
        .failureReason(batchDetailDO.getErrorMessage())
        .failureTime(batchDetailDO.getEndTime())
        .retryCount(batchDetailDO.getRetryCount())
        .build();
  }
}
