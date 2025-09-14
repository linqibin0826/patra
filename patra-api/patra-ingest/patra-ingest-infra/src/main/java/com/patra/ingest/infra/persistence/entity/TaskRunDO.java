package com.patra.ingest.infra.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.JsonNode;
import com.patra.ingest.domain.model.enums.TaskRunStatus;
import com.patra.starter.mybatis.entity.BaseDO.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

/**
 * 任务运行（attempt）· 去前缀实体，对应表：ing_task_run。
 * <p>一次具体尝试；失败重试/回放各自新增记录。</p>
 *
 * 字段要点：
 * - status：{@link TaskRunStatus}
 * - checkpoint/stats：JSON
 * - windowFrom/windowTo/startedAt/finishedAt/lastHeartbeat：UTC
 *
 * 继承 BaseDO：id、recordRemarks、created/updatedBy/At、version、ipAddress、deleted。
 *
 * @author linqibin
 * @since 0.1.0
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName(value = "ing_task_run", autoResultMap = true)
public class TaskRunDO extends BaseDO {

    /** 关联任务ID */
    private Long taskId;

    /** 尝试序号(1起) */
    private Integer attemptNo;

    /** 运行状态 */
    private TaskRunStatus status;

    /** 运行级检查点（JSON，如 nextHint / resumeToken 等） */
    private JsonNode checkpoint;

    /** 统计（JSON）：fetched/upserted/failed/pages 等 */
    private JsonNode stats;

    /** 失败原因 */
    private String error;

    /** 时间型切片时冗余窗口起(UTC)[含] */
    private LocalDateTime windowFrom;

    /** 时间型切片时冗余窗口止(UTC)[不含] */
    private LocalDateTime windowTo;

    /** 开始时间(UTC) */
    private LocalDateTime startedAt;

    /** 结束时间(UTC) */
    private LocalDateTime finishedAt;

    /** 最近心跳时间(UTC) */
    private LocalDateTime lastHeartbeat;

    /** 外部调度运行ID（若逐片触发） */
    private String schedulerRunId;

    /** Trace/CID */
    private String correlationId;
}

