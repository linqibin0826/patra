package com.patra.registry.api.events;

/**
 * docref.aggregate: /docs/domain/aggregate/LiteratureProvenance.txt
 * docref.api: /docs/api/rest/dto/request/LiteratureProvenanceRequest.txt,/docs/api/rest/dto/response/LiteratureProvenanceResponse.txt
 * docref.adapter: /docs/adapter/rest/controller/literature-provenances.naming.txt
 */

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 文献数据源集成事件
 */
public class LiteratureProvenanceIntegrationEvents {
    
    /**
     * 文献数据源已创建事件
     */
    @Data
    public static class LiteratureProvenanceCreated {
        private Long id;
        private String code;
        private String name;
        private LocalDateTime occurredAt;
    }
    
    /**
     * 文献数据源已更新事件
     */
    @Data
    public static class LiteratureProvenanceUpdated {
        private Long id;
        private String code;
        private String name;
        private Long version;
        private LocalDateTime occurredAt;
    }
    
    /**
     * 文献数据源已激活事件
     */
    @Data
    public static class LiteratureProvenanceActivated {
        private Long id;
        private String code;
        private LocalDateTime occurredAt;
    }
    
    /**
     * 文献数据源已停用事件
     */
    @Data
    public static class LiteratureProvenanceDeactivated {
        private Long id;
        private String code;
        private LocalDateTime occurredAt;
    }
    
    /**
     * 文献数据源已删除事件
     */
    @Data
    public static class LiteratureProvenanceDeleted {
        private Long id;
        private String code;
        private LocalDateTime occurredAt;
    }
}
