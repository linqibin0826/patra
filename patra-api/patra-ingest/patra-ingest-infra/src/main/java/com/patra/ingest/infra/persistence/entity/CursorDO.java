package com.patra.ingest.infra.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.patra.common.enums.ProvenanceCode;
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
 * 通用水位（当前值）数据对象，对应表：ing_cursor。
 * <p>
 * 语义：维护某来源 + 操作类型 + 游标键 + 命名空间 下的当前游标值，
 * 兼容 time/id/token 三种游标类型，并冗余归一化的时间/数值字段以支持排序与范围查询。
 * </p>
 * <p>
 * 复合唯一键（uk_cursor_ns）：(literature_provenance_code + operation + cursor_key + namespace_scope + namespace_key)
 * </p>
 * <p>
 * 说明：
 * - operation 使用 {@link IngestOperationType}；
 * - namespaceScope 使用 {@link NamespaceScope}；
 * - cursorType 使用 {@link CursorType}。
 * 这些枚举均实现 CodeEnum<String>，无需显式 TypeHandler，将由基础设施自动处理。
 * </p>
 *
 * 继承 {@link BaseDO}：包含 id、recordRemarks、created/updatedBy/At、version（乐观锁）、ipAddress、deleted。
 *
 * @author linqibin
 * @since 0.1.0
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName(value = "ing_cursor", autoResultMap = true)
public class CursorDO extends BaseDO {

    /** 来源代码：pubmed/epmc/crossref 等 */
    private ProvenanceCode literatureProvenanceCode;

    /** 操作类型：harvest/backfill/update/metrics */
    private IngestOperationType operation;

    /** 游标键：updated_at/published_at/seq_id/cursor_token 等 */
    private String cursorKey;

    /** 命名空间：global/expr/custom */
    private NamespaceScope namespaceScope;

    /** 命名空间键：expr_hash 或自定义哈希；global=全0 */
    private String namespaceKey;

    /** 游标类型：time/id/token */
    private CursorType cursorType;

    /** 当前有效游标值（UTC ISO-8601 / 十进制字符串 / 不透明串） */
    private String cursorValue;

    /** 观测到的最大边界 */
    private String observedMaxValue;

    /** 归一化时间（cursorType=time 时填充，UTC） */
    private LocalDateTime normalizedInstant;

    /** 归一化数值（cursorType=id 时填充） */
    private BigDecimal normalizedNumeric;

    /** 最近一次推进的调度实例 */
    private Long scheduleInstanceId;

    /** 最近一次推进关联 Plan */
    private Long planId;

    /** 最近一次推进关联 Slice */
    private Long sliceId;

    /** 最近一次推进关联 Task */
    private Long taskId;

    /** 最近一次推进的 Run */
    private Long lastRunId;

    /** 最近一次推进的 Batch */
    private Long lastBatchId;

    /** 最近推进使用的表达式哈希 */
    private String exprHash;
}

