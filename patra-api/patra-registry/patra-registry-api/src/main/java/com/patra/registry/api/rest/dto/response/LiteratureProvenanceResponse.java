package com.patra.registry.api.rest.dto.response;

/**
 * docref.aggregate: /docs/domain/aggregate/LiteratureProvenance.txt
 * docref.api: /docs/api/rest/dto/request/LiteratureProvenanceRequest.txt,/docs/api/rest/dto/response/LiteratureProvenanceResponse.txt
 * docref.adapter: /docs/adapter/rest/controller/literature-provenances.naming.txt
 */

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文献数据源响应 DTO
 */
@Data
public class LiteratureProvenanceResponse {
    
    /**
     * 聚合根ID
     */
    private Long id;
    
    /**
     * 数据源名称
     */
    private String name;
    
    /**
     * 数据源代码
     */
    private String code;
    
    /**
     * 数据源描述
     */
    private String description;
    
    /**
     * 乐观锁版本号
     */
    private Long version;
    
    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
    
    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}
