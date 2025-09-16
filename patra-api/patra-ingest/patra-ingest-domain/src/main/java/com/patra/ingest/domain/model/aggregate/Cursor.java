package com.patra.ingest.domain.model.aggregate;

import com.patra.common.enums.ProvenanceCode;
import com.patra.ingest.domain.model.enums.CursorAdvanceDirection;
import com.patra.ingest.domain.model.enums.CursorType;
import com.patra.ingest.domain.model.enums.IngestOperationType;
import com.patra.ingest.domain.model.enums.NamespaceScope;
import com.patra.ingest.domain.model.event.CursorAdvancedEvent;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 通用水位聚合根。
 * <p>
 * 为某来源 + 操作类型 + 游标键 + 命名空间维护"当前水位"，统一 time/id/token 三形态。
 * 支持归一化时间/数值以进行排序与范围对比；保留最近一次推进的 lineage 以支撑端到端追踪。
 * </p>
 * <p>
 * 聚合根职责：
 * - 管理水位的推进和回退
 * - 确保水位变更的有效性
 * - 维护水位溯源信息
 * - 产生水位推进事件
 * </p>
 *
 * @author linqibin
 * @since 0.1.0
 */
@Getter
@Builder
public class Cursor {

    /** 聚合标识 */
    private final Long id;

    /** 来源代码 */
    private final ProvenanceCode literatureProvenanceCode;

    /** 操作类型 */
    private final IngestOperationType operation;

    /** 游标键 */
    private final String cursorKey;

    /** 命名空间 */
    private final NamespaceScope namespaceScope;

    /** 命名空间键 */
    private final String namespaceKey;

    /** 游标类型 */
    private final CursorType cursorType;

    /** 当前有效游标值 */
    private String cursorValue;

    /** 观测到的最大边界 */
    private String observedMaxValue;

    /** 归一化时间（cursorType=time 时填充） */
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

    /** 领域事件列表 */
    @Builder.Default
    private final List<Object> domainEvents = new ArrayList<>();

    /**
     * 创建新的水位游标。
     *
     * @param literatureProvenanceCode 来源代码
     * @param operation               操作类型
     * @param cursorKey               游标键
     * @param namespaceScope          命名空间
     * @param namespaceKey            命名空间键
     * @param cursorType              游标类型
     * @param initialValue            初始值
     * @return 游标实例
     */
    public static Cursor create(ProvenanceCode literatureProvenanceCode,
                               IngestOperationType operation,
                               String cursorKey,
                               NamespaceScope namespaceScope,
                               String namespaceKey,
                               CursorType cursorType,
                               String initialValue) {

        validateCreationParameters(literatureProvenanceCode, operation, cursorKey,
                                 namespaceScope, namespaceKey, cursorType);

        Cursor cursor = Cursor.builder()
                .literatureProvenanceCode(literatureProvenanceCode)
                .operation(operation)
                .cursorKey(cursorKey)
                .namespaceScope(namespaceScope)
                .namespaceKey(namespaceKey != null ? namespaceKey : "")
                .cursorType(cursorType)
                .cursorValue(initialValue)
                .build();

        // 设置归一化值
        cursor.updateNormalizedValues(initialValue);

        return cursor;
    }

    /**
     * 验证创建参数。
     */
    private static void validateCreationParameters(ProvenanceCode literatureProvenanceCode,
                                                 IngestOperationType operation,
                                                 String cursorKey,
                                                 NamespaceScope namespaceScope,
                                                 String namespaceKey,
                                                 CursorType cursorType) {
        if (literatureProvenanceCode == null) {
            throw new IllegalArgumentException("来源代码不能为空");
        }
        if (operation == null) {
            throw new IllegalArgumentException("操作类型不能为空");
        }
        if (cursorKey == null || cursorKey.trim().isEmpty()) {
            throw new IllegalArgumentException("游标键不能为空");
        }
        if (namespaceScope == null) {
            throw new IllegalArgumentException("命名空间不能为空");
        }
        if (cursorType == null) {
            throw new IllegalArgumentException("游标类型不能为空");
        }
    }

    /**
     * 推进水位。
     *
     * @param newValue    新的水位值
     * @param direction   推进方向
     * @param windowFrom  覆盖窗口起
     * @param windowTo    覆盖窗口止
     * @param taskId      关联任务ID
     * @param runId       关联运行ID
     * @param batchId     关联批次ID
     * @param exprHash    表达式哈希
     * @throws IllegalArgumentException 如果新值无效
     */
    public void advance(String newValue,
                       CursorAdvanceDirection direction,
                       LocalDateTime windowFrom,
                       LocalDateTime windowTo,
                       Long taskId,
                       Long runId,
                       Long batchId,
                       String exprHash) {

        if (newValue == null) {
            throw new IllegalArgumentException("新的水位值不能为空");
        }

        // 验证推进方向的合法性
        if (CursorAdvanceDirection.FORWARD.equals(direction)) {
            validateForwardAdvance(newValue);
        }

        String oldValue = this.cursorValue;
        this.cursorValue = newValue;
        updateNormalizedValues(newValue);

        // 更新溯源信息
        updateLineage(taskId, runId, batchId, exprHash);

        // 产生推进事件
        addDomainEvent(CursorAdvancedEvent.of(
                literatureProvenanceCode,
                operation,
                cursorKey,
                namespaceScope,
                namespaceKey,
                cursorType,
                oldValue,
                newValue,
                direction,
                windowFrom,
                windowTo
        ));
    }

    /**
     * 验证前向推进的合法性。
     */
    private void validateForwardAdvance(String newValue) {
        if (cursorValue == null) {
            return; // 首次设置，无需验证
        }

        switch (cursorType) {
            case TIME:
                validateTimeAdvance(newValue);
                break;
            case ID:
                validateIdAdvance(newValue);
                break;
            case TOKEN:
                // TOKEN 类型无需验证顺序，任��值都可以
                break;
        }
    }

    /**
     * 验证时间类型的推进。
     */
    private void validateTimeAdvance(String newValue) {
        try {
            LocalDateTime newTime = LocalDateTime.parse(newValue);
            if (normalizedInstant != null && newTime.isBefore(normalizedInstant)) {
                throw new IllegalArgumentException("时间类型的水位不能后退");
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("无效的时间格式: " + newValue);
        }
    }

    /**
     * 验证ID类型的推进。
     */
    private void validateIdAdvance(String newValue) {
        try {
            BigDecimal newId = new BigDecimal(newValue);
            if (normalizedNumeric != null && newId.compareTo(normalizedNumeric) < 0) {
                throw new IllegalArgumentException("ID类���的水位不能后退");
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("无效的数值格式: " + newValue);
        }
    }

    /**
     * 更新归一化值。
     */
    private void updateNormalizedValues(String value) {
        if (value == null) {
            return;
        }

        switch (cursorType) {
            case TIME:
                try {
                    this.normalizedInstant = LocalDateTime.parse(value);
                } catch (Exception e) {
                    // 忽略解析错误，保持原值
                }
                break;
            case ID:
                try {
                    this.normalizedNumeric = new BigDecimal(value);
                } catch (Exception e) {
                    // 忽略解析错误，保持原值
                }
                break;
            case TOKEN:
                // TOKEN 类型不需要归一化
                break;
        }
    }

    /**
     * 更新溯源信息。
     */
    private void updateLineage(Long taskId, Long runId, Long batchId, String exprHash) {
        this.taskId = taskId;
        this.lastRunId = runId;
        this.lastBatchId = batchId;
        this.exprHash = exprHash;
    }

    /**
     * 获取唯一标识键。
     *
     * @return 唯一标识字符串
     */
    public String getUniqueKey() {
        return String.format("%s:%s:%s:%s:%s",
                literatureProvenanceCode.getCode(),
                operation.name(),
                cursorKey,
                namespaceScope.name(),
                namespaceKey != null ? namespaceKey : "");
    }

    /**
     * 检查是否为全局命名空间。
     *
     * @return 如果是全局命名空间返回 true
     */
    public boolean isGlobalNamespace() {
        return NamespaceScope.GLOBAL.equals(namespaceScope);
    }

    /**
     * 检查是否为表达式命名空间。
     *
     * @return 如果是表达式命名空间返回 true
     */
    public boolean isExprNamespace() {
        return NamespaceScope.EXPR.equals(namespaceScope);
    }

    /**
     * 添加领域事件。
     */
    private void addDomainEvent(Object event) {
        domainEvents.add(event);
    }

    /**
     * 获取领域事件列表（只读）。
     */
    public List<Object> getDomainEvents() {
        return Collections.unmodifiableList(domainEvents);
    }

    /**
     * 清除领域事件。
     */
    public void clearDomainEvents() {
        domainEvents.clear();
    }
}
