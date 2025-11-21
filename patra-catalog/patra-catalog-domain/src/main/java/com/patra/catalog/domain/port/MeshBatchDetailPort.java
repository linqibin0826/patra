package com.patra.catalog.domain.port;

import com.patra.catalog.domain.model.enums.MeshBatchStatus;
import com.patra.catalog.domain.model.valueobject.FailedBatch;
import com.patra.catalog.domain.model.valueobject.MeshImportId;
import java.util.List;

/**
 * MeSH 批次详情仓储接口（Port）。
 *
 * <p>定义批次详情的查询能力，用于进度监控和失败批次追踪。
 *
 * <p><b>设计原则</b>：
 *
 * <ul>
 *   <li>依赖倒置：Domain 层定义接口，Infrastructure 层实现
 *   <li>只读Port：仅提供查询方法，批次详情由导入流程创建
 *   <li>支持进度监控：提供按状态统计和失败批次查询
 * </ul>
 *
 * <p><b>使用场景</b>：
 *
 * <ul>
 *   <li>进度查询：统计各状态批次数量，计算完成度
 *   <li>失败追踪：查询失败批次详情，用于重试和错误分析
 *   <li>性能分析：通过批次时间戳分析导入速度
 * </ul>
 *
 * @author linqibin
 * @since 0.2.0
 */
public interface MeshBatchDetailPort {

  /**
   * 查询指定任务的失败批次。
   *
   * <p>用于重试失败批次和错误分析。
   *
   * @param importId 任务ID
   * @return 失败批次列表（状态为 FAILED）
   */
  List<FailedBatch> findFailedBatches(MeshImportId importId);

  /**
   * 统计指定任务的某状态批次数量。
   *
   * <p>用于进度监控和状态统计。
   *
   * @param importId 任务ID
   * @param status 批次状态
   * @return 批次数量
   */
  Long countByStatus(MeshImportId importId, MeshBatchStatus status);
}
