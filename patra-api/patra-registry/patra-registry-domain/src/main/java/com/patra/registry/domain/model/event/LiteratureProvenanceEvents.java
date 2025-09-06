package com.patra.registry.domain.model.event;

/**
 * docref.aggregate: /docs/domain/aggregate/LiteratureProvenance.txt
 * docref.api: /docs/api/rest/dto/request/LiteratureProvenanceRequest.txt,/docs/api/rest/dto/response/LiteratureProvenanceResponse.txt
 * docref.adapter: /docs/adapter/rest/controller/literature-provenances.naming.txt
 */

import lombok.Value;

import java.time.LocalDateTime;

/**
 * 文献数据源领域事件
 */
public class LiteratureProvenanceEvents {

    /**
     * 文献数据源已创建事件
     */
    @Value
    public static class LiteratureProvenanceCreated {
        Long aggregateId;
        String code;
        String name;
        LocalDateTime occurredAt;
    }

    /**
     * 文献数据源已更新事件
     */
    @Value
    public static class LiteratureProvenanceUpdated {
        Long aggregateId;
        String code;
        String name;
        Long version;
        LocalDateTime occurredAt;
    }

    /**
     * 文献数据源状态已变更事件
     */
    @Value
    public static class LiteratureProvenanceStatusChanged {
        Long aggregateId;
        String code;
        String oldStatus;
        String newStatus;
        LocalDateTime occurredAt;
    }

    /**
     * 文献数据源配置已更新事件
     */
    @Value
    public static class LiteratureProvenanceConfigUpdated {
        Long aggregateId;
        String code;
        String configType;
        LocalDateTime occurredAt;
    }

    /**
     * 文献数据源已删除事件
     */
    @Value
    public static class LiteratureProvenanceDeleted {
        Long aggregateId;
        String code;
        LocalDateTime occurredAt;
    }
}
