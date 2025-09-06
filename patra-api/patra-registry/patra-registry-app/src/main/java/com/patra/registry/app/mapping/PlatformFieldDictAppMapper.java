/**
 * docref:/docs/app/mapping/README.md
 * docref:/docs/api/rest/dto/README.md
 * docref:/docs/domain/aggregates.discovery.md
 */
package com.patra.registry.app.mapping;

import com.patra.registry.api.rest.dto.request.PlatformFieldDictRequest;
import com.patra.registry.api.rest.dto.response.PlatformFieldDictResponse;
import com.patra.registry.app.usecase.command.PlatformFieldDictCommand;
import com.patra.registry.app.usecase.query.PlatformFieldDictQuery;
import com.patra.registry.domain.model.aggregate.PlatformFieldDict;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface PlatformFieldDictAppMapper {

    // Request to Command mapping
    @Mapping(target = "code", source = "code")
    @Mapping(target = "name", source = "name")
    @Mapping(target = "description", source = "description")
    @Mapping(target = "dataType", source = "dataType")
    @Mapping(target = "cardinality", source = "cardinality")
    @Mapping(target = "dateType", source = "dateType")
    @Mapping(target = "defaultValue", source = "defaultValue")
    @Mapping(target = "validationRules", source = "validationRules")
    @Mapping(target = "category", source = "category")
    @Mapping(target = "remarks", source = "remarks")
    PlatformFieldDictCommand.CreateDict toCreateCommand(PlatformFieldDictRequest request);

    @Mapping(target = "code", source = "code")
    @Mapping(target = "name", source = "name")
    @Mapping(target = "description", source = "description")
    @Mapping(target = "dataType", source = "dataType")
    @Mapping(target = "cardinality", source = "cardinality")
    @Mapping(target = "dateType", source = "dateType")
    @Mapping(target = "defaultValue", source = "defaultValue")
    @Mapping(target = "validationRules", source = "validationRules")
    @Mapping(target = "category", source = "category")
    @Mapping(target = "remarks", source = "remarks")
    PlatformFieldDictCommand.UpdateDict toUpdateCommand(PlatformFieldDictRequest request);

    // Domain to Response mapping
    @Mapping(target = "code", source = "code")
    @Mapping(target = "name", source = "name")
    @Mapping(target = "description", source = "description")
    @Mapping(target = "dataType", source = "dataType")
    @Mapping(target = "cardinality", source = "cardinality")
    @Mapping(target = "dateType", source = "dateType")
    @Mapping(target = "defaultValue", source = "defaultValue")
    @Mapping(target = "validationRules", source = "validationRules")
    @Mapping(target = "category", source = "category")
    @Mapping(target = "remarks", source = "remarks")
    @Mapping(target = "active", source = "active")
    @Mapping(target = "createdAt", source = "createdAt")
    @Mapping(target = "updatedAt", source = "updatedAt")
    PlatformFieldDictResponse toResponse(PlatformFieldDict domain);

    // Query parameter mapping
    default PlatformFieldDictQuery.FindByCode toFindByCodeQuery(String code) {
        return PlatformFieldDictQuery.FindByCode.builder()
                .code(code)
                .build();
    }

    default PlatformFieldDictQuery.SearchDicts toSearchQuery(String keyword, int page, int size) {
        return PlatformFieldDictQuery.SearchDicts.builder()
                .keyword(keyword)
                .page(page)
                .size(size)
                .build();
    }
}
