package com.patra.ingest.infra.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.patra.common.enums.ProvenanceCode;
import com.patra.ingest.domain.model.enums.CursorAdvanceDirection;
import com.patra.ingest.domain.model.enums.CursorType;
import com.patra.ingest.domain.model.enums.IngestOperationType;
import com.patra.ingest.domain.model.enums.NamespaceScope;
import com.patra.starter.mybatis.entity.BaseDO.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 水位推进事件（不可变）· 去前缀实体，对应表：ing_cursor_event。
 * <p>每次成功推进记一条事件；支持回放与全链路回溯。</p>
 *
 * 字段要点：
 * - operation：{@link IngestOperationType}
 * - namespaceScope：{@link NamespaceScope}
 * - cursorType：{@link CursorType}
 * - direction：{@link CursorAdvanceDirection}
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
@TableName(value = "ing_cursor_event", autoResultMap = true)
public class CursorEventDO extends BaseDO {

    /** 来源代码：pubmed/epmc/crossref 等 */
    private ProvenanceCode literatureProvenanceCode;

    /** 操作类型 */
    private IngestOperationType operation;

    /** 游标键：updated_at/published_at/seq_id/cursor_token 等 */
    private String cursorKey;

    /** 命名空间 */
    private NamespaceScope namespaceScope;

    /** 命名空间键：expr_hash 或自定义哈希；global=全0 */
    private String namespaceKey;

    /** 游标类型 */
    private CursorType cursorType;

    /** 推进前值 */
    private String prevValue;

    /** 推进后值 */
    private String newValue;

    /** 观测到的最大边界 */
    private String observedMaxValue;

    /** 推进前归一化时间（cursorType=time 时填充，UTC） */
    private LocalDateTime prevInstant;

    /** 推进后归一化时间（cursorType=time 时填充，UTC） */
    private LocalDateTime newInstant;

    /** 推进前归一化数值（cursorType=id 时填充） */
    private BigDecimal prevNumeric;

    /** 推进后归一化数值（cursorType=id 时填充） */
    private BigDecimal newNumeric;

    /** 覆盖窗口起(UTC)[含] */
    private LocalDateTime windowFrom;

    /** 覆盖窗口止(UTC)[不含] */
    private LocalDateTime windowTo;

    /** 方向：forward/backfill */
    private CursorAdvanceDirection direction;

    /** 事件幂等键 */
    private String idempotentKey;

    /** 血缘：调度实例 */
    private Long scheduleInstanceId;

    /** 血缘：Plan */
    private Long planId;

    /** 血缘：Slice */
    private Long sliceId;

    /** 血缘：Task */
    private Long taskId;

    /** 血缘：Run */
    private Long runId;

    /** 血缘：Batch */
    private Long batchId;

    /** 血缘：表达式哈希 */
    private String exprHash;
}

