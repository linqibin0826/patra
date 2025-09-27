package com.patra.ingest.infra.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.patra.starter.mybatis.entity.BaseDO.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.Instant;

/**
 * <p><b>计划蓝图 DO</b> —— 映射表：<code>ing_plan</code></p>
 * <p>语义：一次采集批次的蓝图，固化来源配置、表达式原型与切片策略。</p>
 * <p>要点：
 * <ul>
 *   <li><code>plan_key</code> 是对外可读且幂等的业务键，唯一约束（UK：uk_plan_key）。</li>
 *   <li><code>expr_proto_snapshot</code> / <code>provenance_config_snapshot</code> 以 JSON 存储快照，允许回放与比对。</li>
 *   <li><code>slice_strategy_code</code> + <code>slice_params</code> 决定如何派生子切片。</li>
 * </ul>
 * </p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "ing_plan", autoResultMap = true)
public class PlanDO extends BaseDO {

    /** 关联调度实例 ID */
    @TableField("schedule_instance_id")
    private Long scheduleInstanceId;

    /** 对外幂等键（人类可读） */
    @TableField("plan_key")
    private String planKey;

    /** 来源代码冗余（便于按来源聚合） */
    @TableField("provenance_code")
    private String provenanceCode;

    /** 来源端点标识（如 search/detail/metrics） */
    @TableField("endpoint_name")
    private String endpointName;

    /** 操作类型编码（DICT：ing_operation） */
    @TableField("operation_code")
    private String operationCode;

    /** 表达式原型哈希（规范化 AST 指纹） */
    @TableField("expr_proto_hash")
    private String exprProtoHash;

    /** 表达式原型快照（JSON AST，不含局部条件） */
    @TableField(value = "expr_proto_snapshot", typeHandler = JacksonTypeHandler.class)
    private JsonNode exprProtoSnapshot;

    /** 来源配置快照（JSON，运行期不变参数） */
    @TableField(value = "provenance_config_snapshot", typeHandler = JacksonTypeHandler.class)
    private JsonNode provenanceConfigSnapshot;

    /** 来源配置快照哈希（变更检测用） */
    @TableField("provenance_config_hash")
    private String provenanceConfigHash;

    /** 切片策略编码（TIME/ID_RANGE/CURSOR 等） */
    @TableField("slice_strategy_code")
    private String sliceStrategyCode;

    /** 切片参数快照（JSON，策略配套细节） */
    @TableField(value = "slice_params", typeHandler = JacksonTypeHandler.class)
    private JsonNode sliceParams;

    /** 总窗口起点（UTC，含） */
    @TableField("window_from")
    private Instant windowFrom;

    /** 总窗口终点（UTC，不含） */
    @TableField("window_to")
    private Instant windowTo;

    /** 状态编码（DICT：ing_plan_status） */
    @TableField("status_code")
    private String statusCode;
}
