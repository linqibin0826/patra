package com.patra.ingest.domain.model.event;

import com.patra.common.enums.ProvenanceCode;
import com.patra.ingest.domain.model.enums.CursorAdvanceDirection;
import com.patra.ingest.domain.model.enums.CursorType;
import com.patra.ingest.domain.model.enums.NamespaceScope;
import com.patra.ingest.domain.model.enums.IngestOperationType;
import lombok.Value;

import java.time.LocalDateTime;

/**
 * 水位已推进领域事件。
 * <p>
 * 当水位成功推进时触发，记录推进的详细信息。
 * 用于追踪数据采集的进度和增量同步状态。
 * </p>
 *
 * @author linqibin
 * @since 0.1.0
 */
@Value
public class CursorAdvancedEvent {

    /** 来源代码 */
    ProvenanceCode provenanceCode;

    /** 操作类型 */
    IngestOperationType operation;

    /** 游标键 */
    String cursorKey;

    /** 命名空间 */
    NamespaceScope namespaceScope;

    /** 命名空间键 */
    String namespaceKey;

    /** 游标类型 */
    CursorType cursorType;

    /** 推进前值 */
    String prevValue;

    /** 推进后值 */
    String newValue;

    /** 推进方向 */
    CursorAdvanceDirection direction;

    /** 覆盖窗口起 */
    LocalDateTime windowFrom;

    /** 覆盖窗口止 */
    LocalDateTime windowTo;

    /** 事件发生时间 */
    LocalDateTime occurredAt;

    /**
     * 创建水位已推进事件。
     *
     * @param provenanceCode  来源代码
     * @param operation       操作类型
     * @param cursorKey       游标键
     * @param namespaceScope  命名空间
     * @param namespaceKey    命名空间键
     * @param cursorType      游标类型
     * @param prevValue       推进前值
     * @param newValue        推进后值
     * @param direction       推进方向
     * @param windowFrom      覆盖窗口起
     * @param windowTo        覆盖窗口止
     * @return 领域事件
     */
    public static CursorAdvancedEvent of(ProvenanceCode provenanceCode,
                                        IngestOperationType operation,
                                        String cursorKey,
                                        NamespaceScope namespaceScope,
                                        String namespaceKey,
                                        CursorType cursorType,
                                        String prevValue,
                                        String newValue,
                                        CursorAdvanceDirection direction,
                                        LocalDateTime windowFrom,
                                        LocalDateTime windowTo) {
        return new CursorAdvancedEvent(
            provenanceCode,
            operation,
            cursorKey,
            namespaceScope,
            namespaceKey,
            cursorType,
            prevValue,
            newValue,
            direction,
            windowFrom,
            windowTo,
            LocalDateTime.now()
        );
    }
}
