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
@TableName("ing_task")
/**
 * 采集任务表 (ing_task)
 * 六边形: infra / 持久化 DO
 * 语义: 对某个计划切片在特定来源(provenance)与操作(operation)下的一次采集执行意图。
 * 冗余: provenance_code + operation_code 方便按来源/操作维度快速筛选，不必跨多表 join。
 * 生命周期: scheduled -> running -> finished (成功/失败)，时间字段追踪调度与执行窗口。
 */
public class TaskDO extends BaseDO {
    /** 调度实例 ID（追踪属于哪次调度批次） */
    @TableField("schedule_instance_id") private Long scheduleInstanceId;
    /** 计划 ID（归属的高层计划） */
    @TableField("plan_id") private Long planId;
    /** 切片 ID（来源 plan_slice，用于窗口/分段） */
    @TableField("slice_id") private Long sliceId;
    /** 文献来源代码（冗余） */
    @TableField("provenance_code") private String provenanceCode;
    /** 操作代码（如 SEARCH/UPDATE，冗余） */
    @TableField("operation_code") private String operationCode;
    /** 使用的凭证/账号引用（可为空） */
    @TableField("credential_id") private Long credentialId;
    /** 参数 JSON（任务执行所需动态参数） */
    @TableField("params") private String params;
    /** 幂等键（防重复派发/提交） */
    @TableField("idempotent_key") private String idempotentKey;
    /** 表达式 hash（表达式编译快照标识） */
    @TableField("expr_hash") private String exprHash;
    /** 调度优先级（值越大/小——根据策略定义） */
    @TableField("priority") private Integer priority;
    /** 租约持有者标识 */
    @TableField("lease_owner") private String leaseOwner;
    /** 租约到期时间 */
    @TableField("leased_until") private java.time.Instant leasedUntil;
    /** 租约累计次数 */
    @TableField("lease_count") private Integer leaseCount;
    /** 状态代码（QUEUED/RUNNING/SUCCEEDED/FAILED/CANCELLED） */
    @TableField("status_code") private String statusCode;
    /** 计划调度时间（预计开始时间） */
    @TableField("scheduled_at") private java.time.Instant scheduledAt;
    /** 实际开始时间 */
    @TableField("started_at") private java.time.Instant startedAt;
    /** 结束时间 */
    @TableField("finished_at") private java.time.Instant finishedAt;
}
