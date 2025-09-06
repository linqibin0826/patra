package com.patra.registry.api.rest.dto.request;

import lombok.Data;

/**
 * 更新文献数据源请求
 * docref: /docs/api/rest/dto/request/LiteratureProvenanceRequest.txt
 */
@Data
public class UpdateLiteratureProvenanceRequest {
    
    /**
     * 数据源名称，长度1-100
     */
    private String name;
    
    /**
     * 数据源描述，长度0-500
     */
    private String description;
    
    /**
     * 乐观锁版本号，必填，最小值0
     */
    private Long version;
}
