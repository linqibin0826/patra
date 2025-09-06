package com.patra.registry.app.usecase.command;

/**
 * docref.aggregate: /docs/domain/aggregate/LiteratureProvenance.txt
 * docref.api: /docs/api/rest/dto/request/LiteratureProvenanceRequest.txt,/docs/api/rest/dto/response/LiteratureProvenanceResponse.txt
 * docref.adapter: /docs/adapter/rest/controller/literature-provenances.naming.txt
 */

import lombok.Data;

/**
 * 更新文献数据源命令
 */
@Data
public class LiteratureProvenanceUpdateCommand {
    
    /**
     * 数据源代码（业务键）
     */
    private String code;
    
    /**
     * 数据源名称
     */
    private String name;
    
    /**
     * 数据源描述
     */
    private String description;
    
    /**
     * 乐观锁版本号
     */
    private Long version;
    
    /**
     * 操作人ID
     */
    private Long operatorId;
    
    /**
     * 操作人姓名
     */
    private String operatorName;
}
