package com.patra.registry.app.mapping;

/**
 * docref.aggregate: /docs/domain/aggregate/LiteratureProvenance.txt
 * docref.api: /docs/api/rest/dto/request/LiteratureProvenanceRequest.txt,/docs/api/rest/dto/response/LiteratureProvenanceResponse.txt
 * docref.adapter: /docs/adapter/rest/controller/literature-provenances.naming.txt
 */

import com.patra.registry.api.rest.dto.request.LiteratureProvenanceRequest;
import com.patra.registry.api.rest.dto.response.LiteratureProvenanceResponse;
import com.patra.registry.app.usecase.command.LiteratureProvenanceCreateCommand;
import com.patra.registry.app.usecase.command.LiteratureProvenanceUpdateCommand;
import com.patra.registry.domain.model.aggregate.LiteratureProvenance;
import org.mapstruct.Mapper;

/**
 * 文献数据源应用层映射器
 */
@Mapper(componentModel = "spring")
public interface LiteratureProvenanceAppMapper {
    
    /**
     * 请求DTO转换为创建命令
     */
    LiteratureProvenanceCreateCommand toCreateCommand(LiteratureProvenanceRequest request);
    
    /**
     * 请求DTO转换为更新命令
     */
    LiteratureProvenanceUpdateCommand toUpdateCommand(LiteratureProvenanceRequest request);
    
    /**
     * 聚合转换为响应DTO
     */
    LiteratureProvenanceResponse toResponse(LiteratureProvenance aggregate);
}
