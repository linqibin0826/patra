package com.patra.ingest.domain.model.entity;

import com.patra.common.enums.ProvenanceCode;
import com.patra.ingest.domain.model.enums.CursorAdvanceDirection;
import com.patra.ingest.domain.model.enums.CursorType;
import com.patra.ingest.domain.model.enums.IngestOperationType;
import com.patra.ingest.domain.model.enums.NamespaceScope;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 水位推进事件（不可变）· 实体。
 * <p>
 * 记录每次成功推进的变化事实：prev/new 值、覆盖窗口、方向；以及完整的 lineage 溯源（schedule/plan/slice/task/run/batch + exprHash）。
 * 事件不可变，具备幂等键以保证重复提交不会破坏时间线。
 * </p>
 *
 * @author linqibin
 * @since 0.1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CursorEvent {

    /** 实体标识 */
    private Long id;

    /** 来源代码 */
    private ProvenanceCode literatureProvenanceCode;

    /** 操作类型 */
    private IngestOperationType operation;

    /** 游标键 */
    private String cursorKey;

    /** 命名空间 */
    private NamespaceScope namespaceScope;

    /** 命名空间键 */
    private String namespaceKey;

    /** 游标类型 */
    private CursorType cursorType;

    /** 推进前值 */
    private String prevValue;

    /** 推进后值 */
    private String newValue;

    /** 观测到的最大边界 */
    private String observedMaxValue;

    /** 推进前归一化时间（cursorType=time） */
    private LocalDateTime prevInstant;

    /** 推进后归一化时间（cursorType=time） */
    private LocalDateTime newInstant;

    /** 推进前归一化数值（cursorType=id） */
    private BigDecimal prevNumeric;

    /** 推进后归一化数值（cursorType=id） */
    private BigDecimal newNumeric;

    /** 覆盖窗口起(UTC)[含] */
    private LocalDateTime windowFrom;

    /** 覆盖窗口止(UTC)[不含] */
    private LocalDateTime windowTo;

    /** 方向：forward/backfill */
    private CursorAdvanceDirection direction;

    /** 事件幂等键 */
    private String idempotentKey;

    /** 溯源：调度实例 */
    private Long scheduleInstanceId;

    /** 溯源：Plan */
    private Long planId;

    /** 溯源：Slice */
    private Long sliceId;

    /** 溯源：Task */
    private Long taskId;

    /** 溯源：Run */
    private Long runId;

    /** 溯源：Batch */
    private Long batchId;

    /** 溯源：表达式哈希 */
    private String exprHash;
}

