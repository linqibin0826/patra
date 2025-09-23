package com.patra.ingest.contract.query;

import com.patra.ingest.contract.model.PlanView;
import com.patra.ingest.contract.model.PlanSummaryView;
import com.patra.ingest.contract.model.PlanStatisticsView;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 计划查询端口 - 定义计划相关的查询接口。
 * 
 * <p>该端口用于跨服务的计划信息查询，支持多种查询维度和聚合统计。
 * 
 * @author linqibin
 * @since 0.1.0
 */
public interface PlanQueryPort {
    
    /**
     * 根据计划ID查询计划详情。
     * 
     * @param planId 计划ID
     * @return 计划详情视图
     */
    Optional<PlanView> findPlanById(Long planId);
    
    /**
     * 根据计划键查询计划详情。
     * 
     * @param planKey 计划键
     * @return 计划详情视图
     */
    Optional<PlanView> findPlanByKey(String planKey);
    
    /**
     * 查询指定数据源的活跃计划列表。
     * 
     * @param provenanceCode 数据源编码
     * @param operationCode 操作类型（可选）
     * @param statusCodes 状态列表（可选）
     * @param limit 限制数量
     * @return 计划摘要列表
     */
    List<PlanSummaryView> findActivePlans(
            String provenanceCode,
            String operationCode,
            List<String> statusCodes,
            int limit);
    
    /**
     * 查询指定调度实例的计划列表。
     * 
     * @param scheduleInstanceId 调度实例ID
     * @return 计划摘要列表
     */
    List<PlanSummaryView> findPlansByScheduleInstance(Long scheduleInstanceId);
    
    /**
     * 查询指定时间范围内的计划列表。
     * 
     * @param provenanceCode 数据源编码
     * @param operationCode 操作类型（可选）
     * @param createdFrom 创建时间起始
     * @param createdTo 创建时间结束
     * @param limit 限制数量
     * @return 计划摘要列表
     */
    List<PlanSummaryView> findPlansByTimeRange(
            String provenanceCode,
            String operationCode,
            Instant createdFrom,
            Instant createdTo,
            int limit);
    
    /**
     * 统计指定数据源的计划数量。
     * 
     * @param provenanceCode 数据源编码
     * @param operationCode 操作类型（可选）
     * @param statusCode 状态编码（可选）
     * @return 计划数量
     */
    long countPlans(String provenanceCode, String operationCode, String statusCode);
    
    /**
     * 获取计划统计信息。
     * 
     * @param provenanceCode 数据源编码
     * @param operationCode 操作类型（可选）
     * @param timeRangeHours 统计时间范围（小时）
     * @return 统计信息
     */
    PlanStatisticsView getPlanStatistics(
            String provenanceCode,
            String operationCode,
            int timeRangeHours);
    
    /**
     * 检查计划键是否存在。
     * 
     * @param planKey 计划键
     * @return 如果存在返回true
     */
    boolean existsPlanKey(String planKey);
    
    /**
     * 查询最近的成功计划。
     * 
     * @param provenanceCode 数据源编码
     * @param operationCode 操作类型
     * @param limit 限制数量
     * @return 计划摘要列表
     */
    List<PlanSummaryView> findRecentSuccessfulPlans(
            String provenanceCode,
            String operationCode,
            int limit);
    
    /**
     * 查询失败的计划列表。
     * 
     * @param provenanceCode 数据源编码（可选）
     * @param operationCode 操作类型（可选）
     * @param failedSince 失败时间起始
     * @param limit 限制数量
     * @return 计划摘要列表
     */
    List<PlanSummaryView> findFailedPlans(
            String provenanceCode,
            String operationCode,
            Instant failedSince,
            int limit);
}
