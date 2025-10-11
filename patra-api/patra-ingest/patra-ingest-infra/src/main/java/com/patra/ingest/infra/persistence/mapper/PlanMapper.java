package com.patra.ingest.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.patra.ingest.infra.persistence.entity.PlanDO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * Plan Mapper - data access operations for the plan table.
 * 
 * @author linqibin
 * @since 0.1.0
 */

public interface PlanMapper extends BaseMapper<PlanDO> {
    
    /**
     * Find a plan by its plan key.
     */
    PlanDO findByPlanKey(@Param("planKey") String planKey);
    
    /**
     * Find plans by schedule instance id.
     */
    List<PlanDO> findByScheduleInstanceId(@Param("scheduleInstanceId") Long scheduleInstanceId);
    
    /**
     * Find active plans for a given provenance and operation type.
     */
    List<PlanDO> findActiveByProvenanceAndOperation(
            @Param("provenanceCode") String provenanceCode,
            @Param("operationCode") String operationCode,
            @Param("statusCodes") List<String> statusCodes);
    
    /**
     * Count if a plan key exists.
     */
    int countByPlanKey(@Param("planKey") String planKey);
    
    /**
     * Count plans by status for a given provenance and operation.
     */
    long countByProvenanceAndOperationAndStatus(
            @Param("provenanceCode") String provenanceCode,
            @Param("operationCode") String operationCode,
            @Param("statusCode") String statusCode);
    
    /**
     * Batch update plan status.
     */
    int batchUpdateStatus(
            @Param("planIds") List<Long> planIds,
            @Param("statusCode") String statusCode,
            @Param("remarks") String remarks);
    
    /**
     * Soft-delete a plan by id.
     */
    int softDeleteById(@Param("planId") Long planId);
}
