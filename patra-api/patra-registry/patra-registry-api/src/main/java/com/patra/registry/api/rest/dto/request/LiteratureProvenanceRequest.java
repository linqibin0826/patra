package com.patra.registry.api.rest.dto.request;

/**
 * docref.aggregate: /docs/domain/aggregate/LiteratureProvenance.txt
 * docref.api: /docs/api/rest/dto/request/LiteratureProvenanceRequest.txt,/docs/api/rest/dto/response/LiteratureProvenanceResponse.txt
 * docref.adapter: /docs/adapter/rest/controller/literature-provenances.naming.txt
 */

import lombok.Data;

/**
 * 文献数据源请求 DTO
 */
@Data
public class LiteratureProvenanceRequest {
    
    /**
     * 数据源名称，长度1-100
     */
    private String name;
    
    /**
     * 数据源代码，全局唯一，小写字母数字下划线，长度1-50
     */
    private String code;
    
    /**
     * 数据源描述，长度0-500
     */
    private String description;
    
    /**
     * 乐观锁版本号
     */
    private Long version;
}
