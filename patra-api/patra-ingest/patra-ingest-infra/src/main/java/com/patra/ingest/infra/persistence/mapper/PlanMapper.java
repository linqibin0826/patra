package com.patra.ingest.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.patra.ingest.infra.persistence.entity.PlanDO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 计划 Mapper - 提供计划表的数据访问操作。
 * 
 * @author linqibin
 * @since 0.1.0
 */

public interface PlanMapper extends BaseMapper<PlanDO> {
    
    /**
     * 根据计划键查找计划。
     */
    PlanDO findByPlanKey(@Param("planKey") String planKey);
    
    /**
     * 根据调度实例ID查找计划列表。
     */
    List<PlanDO> findByScheduleInstanceId(@Param("scheduleInstanceId") Long scheduleInstanceId);
    
    /**
     * 查找指定数据源和操作类型的活跃计划。
     */
    List<PlanDO> findActiveByProvenanceAndOperation(
            @Param("provenanceCode") String provenanceCode,
            @Param("operationCode") String operationCode,
            @Param("statusCodes") List<String> statusCodes);
    
    /**
     * 检查计划键是否存在。
     */
    int countByPlanKey(@Param("planKey") String planKey);
    
    /**
     * 统计指定状态的计划数量。
     */
    long countByProvenanceAndOperationAndStatus(
            @Param("provenanceCode") String provenanceCode,
            @Param("operationCode") String operationCode,
            @Param("statusCode") String statusCode);
    
    /**
     * 批量更新计划状态。
     */
    int batchUpdateStatus(
            @Param("planIds") List<Long> planIds,
            @Param("statusCode") String statusCode,
            @Param("remarks") String remarks);
    
    /**
     * 软删除计划。
     */
    int softDeleteById(@Param("planId") Long planId);
}
