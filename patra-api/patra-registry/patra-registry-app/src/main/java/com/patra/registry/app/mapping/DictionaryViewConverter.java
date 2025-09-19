package com.patra.registry.app.mapping;

import com.patra.registry.contract.query.view.DictionaryItemQuery;
import com.patra.registry.contract.query.view.DictionaryItemView;
import com.patra.registry.contract.query.view.DictionaryTypeQuery;
import com.patra.registry.contract.query.view.DictionaryTypeView;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import java.util.List;

/**
 * MapStruct converter for contract Query to View object mapping.
 * Handles conversion between contract query objects and view objects
 * for external API consumption, excluding internal system fields.
 * 
 * @author linqibin
 * @since 0.1.0
 */
@Mapper(
    componentModel = "spring",
    unmappedTargetPolicy = ReportingPolicy.ERROR
)
public interface DictionaryViewConverter {
    
    /**
     * Converts a contract DictionaryItemQuery to a DictionaryItemView.
     * Maps fields suitable for external consumption, excluding internal status fields.
     * 
     * @param query the dictionary item query to convert
     * @return the converted DictionaryItemView object for external consumption
     */
    @Mapping(target = "typeCode", source = "typeCode")
    @Mapping(target = "itemCode", source = "itemCode")
    @Mapping(target = "displayName", source = "displayName")
    @Mapping(target = "description", source = "description")
    @Mapping(target = "isDefault", source = "isDefault")
    @Mapping(target = "sortOrder", source = "sortOrder")
    DictionaryItemView toView(DictionaryItemQuery query);

    /**
     * Converts a contract DictionaryTypeQuery to a DictionaryTypeView.
     * Maps type metadata suitable for external consumption.
     * 
     * @param query the dictionary type query to convert
     * @return the converted DictionaryTypeView object for external consumption
     */
    @Mapping(target = "typeCode", source = "typeCode")
    @Mapping(target = "typeName", source = "typeName")
    @Mapping(target = "description", source = "description")
    @Mapping(target = "itemCount", source = "enabledItemCount")
    DictionaryTypeView toView(DictionaryTypeQuery query);
    
    /**
     * Converts a list of DictionaryTypeQuery objects to DictionaryTypeView objects.
     * 
     * @param queries the list of dictionary type queries to convert
     * @return the converted list of DictionaryTypeView objects
     */
    List<DictionaryTypeView> toViews(List<DictionaryTypeQuery> queries);
}
