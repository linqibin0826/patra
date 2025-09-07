package com.patra.ingest.infra.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.patra.ingest.domain.model.enums.PlanStatus;
import com.patra.starter.mybatis.entity.BaseDO.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import java.time.LocalDateTime;

/**
 * 采集计划数据对象
 *
 * @author linqibin
 * @since 0.1.0
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ing_plan")
public class PlanDO extends BaseDO {
    
    /**
     * 人类可读或外部幂等键（唯一）
     */
    @TableField("plan_key")
    private String planKey;
    
    /**
     * 计划名称
     */
    @TableField("name")
    private String name;
    
    /**
     * 计划说明
     */
    @TableField("description")
    private String description;
    
    /**
     * 表达式哈希：SHA-256(normalized_json)
     */
    @TableField("expr_hash")
    private String exprHash;
    
    /**
     * 表达式快照JSON（只读）
     */
    @TableField("expr_snapshot_json")
    private String exprSnapshotJson;
    
    /**
     * 起始时间（含）
     */
    @TableField("date_from")
    private LocalDateTime dateFrom;
    
    /**
     * 结束时间（含）
     */
    @TableField("date_to")
    private LocalDateTime dateTo;
    
    /**
     * 计划状态
     */
    @TableField("status")
    private PlanStatus status;
}
