package com.patra.ingest.domain.port;

import com.patra.ingest.domain.model.aggregate.PlanAggregate;
import com.patra.ingest.domain.model.vo.plan.PlanId;
import com.patra.ingest.domain.model.vo.plan.PlanKey;
import com.patra.ingest.domain.model.vo.common.ProvenanceCode;
import com.patra.ingest.domain.model.vo.common.OperationCode;
import com.patra.ingest.domain.model.vo.common.StatusCode;

import java.util.List;
import java.util.Optional;

/**
 * 计划仓储端口 - 定义计划聚合的持久化操作。
 *
 * @author linqibin
 * @since 0.1.0
 */
public interface PlanRepository {

    /**
     * 保存计划聚合。
     *
     * @param plan 计划聚合
     * @return 保存后的计划聚合（包含生成的ID）
     */
    PlanAggregate save(PlanAggregate plan);

    /**
     * 根据ID查找计划。
     *
     * @param planId 计划ID
     * @return 计划聚合，如果不存在则返回空
     */
    Optional<PlanAggregate> findById(PlanId planId);

    /**
     * 根据计划键查找计划。
     *
     * @param planKey 计划键
     * @return 计划聚合，如果不存在则返回空
     */
    Optional<PlanAggregate> findByPlanKey(PlanKey planKey);

    /**
     * 根据调度实例ID查找计划。
     *
     * @param scheduleInstanceId 调度实例ID
     * @return 计划聚合列表
     */
    List<PlanAggregate> findByScheduleInstanceId(Long scheduleInstanceId);

    /**
     * 查找指定数据源和操作类型的活跃计划。
     *
     * @param provenanceCode 数据源编码
     * @param operationCode 操作类型
     * @param statusCodes 状态码列表
     * @return 计划聚合列表
     */
    List<PlanAggregate> findActiveByProvenanceAndOperation(
            ProvenanceCode provenanceCode,
            OperationCode operationCode,
            List<StatusCode> statusCodes);

    /**
     * 检查计划键是否已存在。
     *
     * @param planKey 计划键
     * @return 如果存在返回true，否则返回false
     */
    boolean existsByPlanKey(PlanKey planKey);

    /**
     * 统计指定状态的计划数量。
     *
     * @param provenanceCode 数据源编码
     * @param operationCode 操作类型
     * @param statusCode 状态码
     * @return 计划数量
     */
    long countByProvenanceAndOperationAndStatus(
            ProvenanceCode provenanceCode,
            OperationCode operationCode,
            StatusCode statusCode);

    /**
     * 删除计划（软删除）。
     *
     * @param planId 计划ID
     */
    void deleteById(PlanId planId);

    /**
     * 批量更新计划状态。
     *
     * @param planIds 计划ID列表
     * @param statusCode 新状态
     * @param remarks 备注
     * @return 更新的记录数
     */
    int batchUpdateStatus(List<PlanId> planIds, StatusCode statusCode, String remarks);
}
