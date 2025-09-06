package com.patra.registry.api.rest.dto.request;

import lombok.Data;

/**
 * 创建文献数据源请求
 * docref: /docs/api/rest/dto/request/LiteratureProvenanceRequest.txt
 */
@Data
public class CreateLiteratureProvenanceRequest {
    
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
}
