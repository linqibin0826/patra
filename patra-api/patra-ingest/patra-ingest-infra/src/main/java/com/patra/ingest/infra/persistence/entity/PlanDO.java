package com.patra.ingest.infra.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.patra.starter.mybatis.entity.BaseDO.BaseDO;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ing_plan")
/**
 * 采集计划表 (ing_plan)
 * 描述一次调度下针对某来源/操作的宏观采集范围（时间窗口 / 表达式原型）。
 * plan_key 可作为业务幂等或追踪标识（不同窗口或参数形成唯一键）。
 */
public class PlanDO extends BaseDO {
    /** 调度实例 ID */
    @TableField("schedule_instance_id") private Long scheduleInstanceId;
    /** 计划业务键（表达式 + 窗口规格等 hash/key） */
    @TableField("plan_key") private String planKey;
    /** 来源代码 */
    @TableField("provenance_code") private String provenanceCode;
    /** 端点名称（区分来源内部不同接口或子管道） */
    @TableField("endpoint_name") private String endpointName;
    /** 操作代码 */
    @TableField("operation_code") private String operationCode;
    /** 表达式原型哈希（未切片前的原始表达式编译指纹） */
    @TableField("expr_proto_hash") private String exprProtoHash;
    /** 表达式原型AST快照 */
    @TableField("expr_proto_snapshot") private String exprProtoSnapshot;
    /** 来源配置中立快照(JSON) */
    @TableField("provenance_config_snapshot") private String provenanceConfigSnapshot;
    /** 来源配置规范化哈希 */
    @TableField("provenance_config_hash") private String provenanceConfigHash;
    /** 逻辑时间窗口起 */
    @TableField("window_from") private java.time.Instant windowFrom;
    /** 逻辑时间窗口止 */
    @TableField("window_to") private java.time.Instant windowTo;
    /** 切片策略代码 */
    @TableField("slice_strategy_code") private String sliceStrategyCode;
    /** 切片参数JSON */
    @TableField("slice_params") private String sliceParams;
    /** 状态代码（DRAFT/SLICING/READY/PARTIAL/FAILED/COMPLETED） */
    @TableField("status_code") private String statusCode;
}
