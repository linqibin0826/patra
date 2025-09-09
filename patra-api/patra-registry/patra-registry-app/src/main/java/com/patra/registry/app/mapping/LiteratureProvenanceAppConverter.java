package com.patra.registry.app.mapping;

/**
 * docref.aggregate: /docs/domain/aggregate/LiteratureProvenance.txt
 * docref.api: /docs/api/rest/dto/request/LiteratureProvenanceRequest.txt,/docs/api/rest/dto/response/LiteratureProvenanceResponse.txt
 * docref.adapter: /docs/adapter/rest/controller/literature-provenances.naming.txt
 */

import org.mapstruct.Mapper;

/**
 * 文献数据源应用层映射器
 */
@Mapper(componentModel = "spring")
public interface LiteratureProvenanceAppConverter {

}
