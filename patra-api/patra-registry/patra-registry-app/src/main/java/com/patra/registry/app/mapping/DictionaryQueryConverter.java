package com.patra.registry.app.mapping;

import com.patra.registry.contract.query.view.DictionaryItemQuery;
import com.patra.registry.contract.query.view.DictionaryTypeQuery;
import com.patra.registry.domain.model.vo.DictionaryItem;
import com.patra.registry.domain.model.vo.DictionaryType;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/**
 * MapStruct converter for domain to contract Query object mapping.
 * Handles conversion between domain value objects and contract query objects
 * for dictionary items and types in CQRS read operations.
 * 
 * @author linqibin
 * @since 0.1.0
 */
@Mapper(
    componentModel = "spring",
    unmappedTargetPolicy = ReportingPolicy.ERROR
)
public interface DictionaryQueryConverter {
    
    /**
     * Converts a domain DictionaryItem to a contract DictionaryItemQuery.
     * Maps all relevant fields from the domain object to the query object,
     * excluding internal fields like 'deleted' that are not needed in query responses.
     * 
     * @param domainItem the domain dictionary item to convert
     * @param typeCode the type code to include in the query object
     * @return the converted DictionaryItemQuery object
     */
    @Mapping(target = "typeCode", source = "typeCode")
    @Mapping(target = "itemCode", source = "domainItem.itemCode")
    @Mapping(target = "displayName", source = "domainItem.displayName")
    @Mapping(target = "description", source = "domainItem.description")
    @Mapping(target = "isDefault", source = "domainItem.isDefault")
    @Mapping(target = "sortOrder", source = "domainItem.sortOrder")
    @Mapping(target = "enabled", source = "domainItem.enabled")
    DictionaryItemQuery toQuery(DictionaryItem domainItem, String typeCode);
    
    /**
     * Converts a domain DictionaryType to a contract DictionaryTypeQuery.
     * Maps the type metadata and includes additional computed fields like item counts.
     * 
     * @param domainType the domain dictionary type to convert
     * @param enabledItemCount the number of enabled items in this type
     * @param hasDefault whether this type has a default item
     * @return the converted DictionaryTypeQuery object
     */
    @Mapping(target = "typeCode", source = "domainType.typeCode")
    @Mapping(target = "typeName", source = "domainType.typeName")
    @Mapping(target = "description", source = "domainType.description")
    @Mapping(target = "enabledItemCount", source = "enabledItemCount")
    @Mapping(target = "hasDefault", source = "hasDefault")
    DictionaryTypeQuery toQuery(DictionaryType domainType, int enabledItemCount, boolean hasDefault);
}