package com.patra.ingest.domain.model.entity;

import com.patra.ingest.domain.model.enums.CursorDirection;
import com.patra.ingest.domain.model.enums.CursorType;
import com.patra.ingest.domain.model.vo.CursorLineage;
import java.time.Instant;
import java.math.BigDecimal;
import lombok.Getter;

/**
 * 游标推进事件（append-only）。
 *
 * <p>用于记录某一命名空间下特定游标的推进（或回退）操作轨迹，支撑：
 * <ul>
 *   <li>审计：追踪每次窗口推进来源与值变化。</li>
 *   <li>重建：在需要恢复游标状态时回放事件。</li>
 *   <li>监控：分析推进速度、回退比例、滞后情况。</li>
 * </ul>
 * </p>
 * <p>设计原则：不可变；不修改历史记录；必要字段覆盖字符串/时间/数值多类型表示，避免类型切换损失精度。</p>
 */
@SuppressWarnings("unused")
@Getter
public class CursorEvent {
    /** 主键 ID。 */
    private final Long id;
    /** 来源代码。 */
    private final String provenanceCode;
    /** 操作代码。 */
    private final String operationCode;
    /** 游标逻辑键（区分同一来源下不同维度）。 */
    private final String cursorKey;
    /** 命名空间范围代码（如租户 / 数据域）。 */
    private final String namespaceScopeCode;
    /** 命名空间业务键。 */
    private final String namespaceKey;
    /** 游标类型（时间 / 数值 / 字典序等）。 */
    private final CursorType cursorType;
    /** 旧值（原始字符串表示）。 */
    private final String prevValue;
    /** 新值（原始字符串表示）。 */
    private final String newValue;
    /** 窗口起（若推进基于窗口）。 */
    private final Instant windowFrom;
    /** 窗口止（半开区间右边界）。 */
    private final Instant windowTo;
    /** 推进方向（前进 / 回退）。 */
    private final CursorDirection direction;
    /** 幂等键（防重复写入）。 */
    private final String idempotentKey;
    /** 在推进时观察到的最大可能值（用于延迟评估）。 */
    private final String observedMaxValue;
    /** 旧值解析出的时间表示（若适用）。 */
    private final Instant prevInstant;
    /** 新值解析出的时间表示（若适用）。 */
    private final Instant newInstant;
    /** 旧值解析出的数值表示（若适用）。 */
    private final BigDecimal prevNumeric;
    /** 新值解析出的数值表示（若适用）。 */
    private final BigDecimal newNumeric;
    /** 血缘信息（表达式依赖链）。 */
    private final CursorLineage lineage;
    /** 推进表达式哈希（用于追踪策略变更）。 */
    private final String exprHash;

    private CursorEvent(Long id,
                        String provenanceCode,
                        String operationCode,
                        String cursorKey,
                        String namespaceScopeCode,
                        String namespaceKey,
                        CursorType cursorType,
                        String prevValue,
                        String newValue,
                        Instant windowFrom,
                        Instant windowTo,
                        CursorDirection direction,
                        String idempotentKey,
                        String observedMaxValue,
                        Instant prevInstant,
                        Instant newInstant,
                        BigDecimal prevNumeric,
                        BigDecimal newNumeric,
                        CursorLineage lineage,
                        String exprHash) {
        this.id = id;
        this.provenanceCode = provenanceCode;
        this.operationCode = operationCode;
        this.cursorKey = cursorKey;
        this.namespaceScopeCode = namespaceScopeCode;
        this.namespaceKey = namespaceKey;
        this.cursorType = cursorType;
        this.prevValue = prevValue;
        this.newValue = newValue;
        this.windowFrom = windowFrom;
        this.windowTo = windowTo;
        this.direction = direction;
        this.idempotentKey = idempotentKey;
        this.observedMaxValue = observedMaxValue;
        this.prevInstant = prevInstant;
        this.newInstant = newInstant;
        this.prevNumeric = prevNumeric;
        this.newNumeric = newNumeric;
        this.lineage = lineage == null ? CursorLineage.empty() : lineage;
        this.exprHash = exprHash;
    }

    /**
     * 还原（反序列化）事件对象。
     * @param id 主键ID
     * @param provenanceCode 来源代码
     * @param operationCode 操作代码
     * @param cursorKey 游标键
     * @param namespaceScopeCode 命名空间范围
     * @param namespaceKey 命名空间键
     * @param cursorType 游标类型
     * @param prevValue 旧值(字符串)
     * @param newValue 新值(字符串)
     * @param windowFrom 窗口起
     * @param windowTo 窗口止
     * @param direction 推进方向
     * @param idempotentKey 幂等键
     * @param observedMaxValue 观察最大值
     * @param prevInstant 旧时间值
     * @param newInstant 新时间值
     * @param prevNumeric 旧数值
     * @param newNumeric 新数值
     * @param lineage 血缘
     * @param exprHash 表达式哈希
     * @return 事件实例
     */
    public static CursorEvent restore(Long id,
                                      String provenanceCode,
                                      String operationCode,
                                      String cursorKey,
                                      String namespaceScopeCode,
                                      String namespaceKey,
                                      CursorType cursorType,
                                      String prevValue,
                                      String newValue,
                                      Instant windowFrom,
                                      Instant windowTo,
                                      CursorDirection direction,
                                      String idempotentKey,
                                      String observedMaxValue,
                                      Instant prevInstant,
                                      Instant newInstant,
                                      BigDecimal prevNumeric,
                                      BigDecimal newNumeric,
                                      CursorLineage lineage,
                                      String exprHash) {
        return new CursorEvent(id,
                provenanceCode,
                operationCode,
                cursorKey,
                namespaceScopeCode,
                namespaceKey,
                cursorType,
                prevValue,
                newValue,
                windowFrom,
                windowTo,
                direction,
                idempotentKey,
                observedMaxValue,
                prevInstant,
                newInstant,
                prevNumeric,
                newNumeric,
                lineage,
                exprHash);
    }
}
