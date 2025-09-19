package com.patra.registry.app.mapping;

import com.patra.registry.contract.query.view.DictionaryHealthQuery;
import com.patra.registry.contract.query.view.DictionaryValidationQuery;
import com.patra.registry.domain.model.vo.DictionaryHealthStatus;
import com.patra.registry.domain.model.vo.ValidationResult;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/**
 * MapStruct converter for domain to contract validation Query object mapping.
 * Handles conversion between domain validation objects and contract query objects
 * for dictionary validation and health status in CQRS read operations.
 * 
 * @author linqibin
 * @since 0.1.0
 */
@Mapper(
    componentModel = "spring",
    unmappedTargetPolicy = ReportingPolicy.ERROR
)
public interface DictionaryValidationConverter {
    
    /**
     * Converts a domain ValidationResult to a contract DictionaryValidationQuery.
     * Maps validation outcome and error information for API responses.
     * 
     * @param validationResult the domain validation result to convert
     * @param typeCode the dictionary type code that was validated
     * @param itemCode the dictionary item code that was validated
     * @return the converted DictionaryValidationQuery object
     */
    @Mapping(target = "typeCode", source = "typeCode")
    @Mapping(target = "itemCode", source = "itemCode")
    @Mapping(target = "isValid", source = "validationResult.isValid")
    @Mapping(target = "errorMessage", source = "validationResult.errorMessage")
    DictionaryValidationQuery toQuery(ValidationResult validationResult, String typeCode, String itemCode);
    
    /**
     * Converts a domain DictionaryHealthStatus to a contract DictionaryHealthQuery.
     * Maps all health metrics and integrity issue information for monitoring endpoints.
     * 
     * @param healthStatus the domain health status to convert
     * @return the converted DictionaryHealthQuery object
     */
    @Mapping(target = "totalTypes", source = "totalTypes")
    @Mapping(target = "totalItems", source = "totalItems")
    @Mapping(target = "enabledItems", source = "enabledItems")
    @Mapping(target = "typesWithoutDefault", source = "typesWithoutDefault")
    @Mapping(target = "typesWithMultipleDefaults", source = "typesWithMultipleDefaults")
    DictionaryHealthQuery toQuery(DictionaryHealthStatus healthStatus);
}