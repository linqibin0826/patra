package com.patra.registry.api.events;

/**
 * docref.aggregate: /docs/domain/aggregate/PlatformFieldDict.txt
 * docref.api: /docs/api/rest/dto/request/PlatformFieldDictRequest.txt,/docs/api/rest/dto/response/PlatformFieldDictResponse.txt
 * docref.adapter: /docs/adapter/rest/controller/platform-field-dicts.naming.txt
 */

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 平台字段字典集成事件
 */
public class PlatformFieldDictIntegrationEvents {
    
    /**
     * 平台字段字典已创建事件
     */
    @Data
    public static class PlatformFieldDictCreated {
        private Long id;
        private String fieldKey;
        private String dataType;
        private LocalDateTime occurredAt;
    }
    
    /**
     * 平台字段字典已更新事件
     */
    @Data
    public static class PlatformFieldDictUpdated {
        private Long id;
        private String fieldKey;
        private String dataType;
        private Long version;
        private LocalDateTime occurredAt;
    }
    
    /**
     * 平台字段字典已发布事件
     */
    @Data
    public static class PlatformFieldDictPublished {
        private Long id;
        private String fieldKey;
        private LocalDateTime occurredAt;
    }
    
    /**
     * 平台字段字典已废弃事件
     */
    @Data
    public static class PlatformFieldDictDeprecated {
        private Long id;
        private String fieldKey;
        private LocalDateTime occurredAt;
    }
    
    /**
     * 平台字段字典已删除事件
     */
    @Data
    public static class PlatformFieldDictDeleted {
        private Long id;
        private String fieldKey;
        private LocalDateTime occurredAt;
    }
}
