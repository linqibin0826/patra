package com.patra.registry.domain.model.event;

/**
 * LiteratureProvenance 聚合的领域事件集合。
 *
 * <p>事件只描述“已发生的业务事实”，不承载技术性元数据。
 * @author linqibin
 * @since 0.1.0
 */

import lombok.Value;

import java.time.LocalDateTime;

public class LiteratureProvenanceEvents {

    /**
     * 文献数据源已创建事件。
     */
    @Value
    public static class LiteratureProvenanceCreated {
        /** 聚合ID（技术键） */
        Long aggregateId;
        /** 业务键（数据源 code 字符串） */
        String code;
        /** 数据源名称 */
        String name;
        /** 事件发生时间 */
        LocalDateTime occurredAt;
    }

    /**
     * 文献数据源已更新事件（通用）。
     */
    @Value
    public static class LiteratureProvenanceUpdated {
        /** 聚合ID */
        Long aggregateId;
        /** 业务键 */
        String code;
        /** 名称（更新后） */
        String name;
        /** 版本号（更新后或更新时） */
        Long version;
        /** 事件发生时间 */
        LocalDateTime occurredAt;
    }

    /**
     * 文献数据源状态已变更事件（如启用/停用）。
     */
    @Value
    public static class LiteratureProvenanceStatusChanged {
        /** 聚合ID */
        Long aggregateId;
        /** 业务键 */
        String code;
        /** 旧状态 */
        String oldStatus;
        /** 新状态 */
        String newStatus;
        /** 事件发生时间 */
        LocalDateTime occurredAt;
    }

    /**
     * 文献数据源配置已更新事件（可按 configType 指示更新类别）。
     */
    @Value
    public static class LiteratureProvenanceConfigUpdated {
        /** 聚合ID */
        Long aggregateId;
        /** 业务键 */
        String code;
        /** 配置更新类别（如 full/partial） */
        String configType;
        /** 事件发生时间 */
        LocalDateTime occurredAt;
    }

    /**
     * 文献数据源已删除事件。
     */
    @Value
    public static class LiteratureProvenanceDeleted {
        /** 聚合ID */
        Long aggregateId;
        /** 业务键 */
        String code;
        /** 事件发生时间 */
        LocalDateTime occurredAt;
    }
}
