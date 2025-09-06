package com.patra.registry.app.usecase.query;

/**
 * docref.aggregate: /docs/domain/aggregate/LiteratureProvenance.txt
 * docref.api: /docs/api/rest/dto/request/LiteratureProvenanceRequest.txt,/docs/api/rest/dto/response/LiteratureProvenanceResponse.txt
 * docref.adapter: /docs/adapter/rest/controller/literature-provenances.naming.txt
 */

import lombok.Data;

/**
 * 根据业务键查询文献数据源查询
 */
@Data
public class LiteratureProvenanceByCodeQuery {
    
    /**
     * 数据源代码（业务键）
     */
    private String code;
    
    /**
     * 是否包含配置信息
     */
    private boolean includeConfig = false;
    
    /**
     * 是否包含查询能力
     */
    private boolean includeCapability = false;
    
    /**
     * 是否包含参数映射
     */
    private boolean includeParamMappings = false;
    
    /**
     * 是否包含渲染规则
     */
    private boolean includeRenderRules = false;
}
