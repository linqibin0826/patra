package com.patra.ingest.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.patra.ingest.infra.persistence.entity.PlanDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

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
    @Select("SELECT * FROM ing_plan WHERE plan_key = #{planKey} AND deleted = 0")
    PlanDO findByPlanKey(@Param("planKey") String planKey);
    
    /**
     * 根据调度实例ID查找计划列表。
     */
    @Select("SELECT * FROM ing_plan WHERE schedule_instance_id = #{scheduleInstanceId} AND deleted = 0 ORDER BY created_at")
    List<PlanDO> findByScheduleInstanceId(@Param("scheduleInstanceId") Long scheduleInstanceId);
    
    /**
     * 查找指定数据源和操作类型的活跃计划。
     */
    @Select("""
        SELECT * FROM ing_plan 
        WHERE provenance_code = #{provenanceCode} 
        AND operation_code = #{operationCode} 
        AND status_code IN 
        <foreach collection="statusCodes" item="status" open="(" separator="," close=")">
            #{status}
        </foreach>
        AND deleted = 0 
        ORDER BY created_at DESC
        """)
    List<PlanDO> findActiveByProvenanceAndOperation(
            @Param("provenanceCode") String provenanceCode,
            @Param("operationCode") String operationCode,
            @Param("statusCodes") List<String> statusCodes);
    
    /**
     * 检查计划键是否存在。
     */
    @Select("SELECT COUNT(1) FROM ing_plan WHERE plan_key = #{planKey} AND deleted = 0")
    int countByPlanKey(@Param("planKey") String planKey);
    
    /**
     * 统计指定状态的计划数量。
     */
    @Select("""
        SELECT COUNT(1) FROM ing_plan 
        WHERE provenance_code = #{provenanceCode} 
        AND operation_code = #{operationCode} 
        AND status_code = #{statusCode} 
        AND deleted = 0
        """)
    long countByProvenanceAndOperationAndStatus(
            @Param("provenanceCode") String provenanceCode,
            @Param("operationCode") String operationCode,
            @Param("statusCode") String statusCode);
    
    /**
     * 批量更新计划状态。
     */
    @Update("""
        UPDATE ing_plan 
        SET status_code = #{statusCode}, 
            record_remarks = JSON_SET(COALESCE(record_remarks, '{}'), '$.batchUpdateReason', #{remarks}),
            updated_at = NOW(),
            version = version + 1
        WHERE id IN 
        <foreach collection="planIds" item="planId" open="(" separator="," close=")">
            #{planId}
        </foreach>
        AND deleted = 0
        """)
    int batchUpdateStatus(
            @Param("planIds") List<Long> planIds,
            @Param("statusCode") String statusCode,
            @Param("remarks") String remarks);
    
    /**
     * 软删除计划。
     */
    @Update("""
        UPDATE ing_plan 
        SET deleted = 1, 
            updated_at = NOW(),
            version = version + 1
        WHERE id = #{planId}
        """)
    int softDeleteById(@Param("planId") Long planId);
}
