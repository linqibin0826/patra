package com.patra.registry.app.usecase.query;

/**
 * docref.aggregate: /docs/domain/aggregate/LiteratureProvenance.txt
 * docref.api: /docs/api/rest/dto/request/LiteratureProvenanceRequest.txt,/docs/api/rest/dto/response/LiteratureProvenanceResponse.txt
 * docref.adapter: /docs/adapter/rest/controller/literature-provenances.naming.txt
 */

import lombok.Data;

/**
 * 分页查询文献数据源查询
 */
@Data
public class LiteratureProvenancePageQuery {
    
    /**
     * 偏移量
     */
    private int offset = 0;
    
    /**
     * 限制数量
     */
    private int limit = 20;
    
    /**
     * 数据源名称过滤（可选）
     */
    private String nameFilter;
    
    /**
     * 数据源代码过滤（可选）
     */
    private String codeFilter;
    
    /**
     * 状态过滤（可选）
     */
    private String statusFilter;
}
