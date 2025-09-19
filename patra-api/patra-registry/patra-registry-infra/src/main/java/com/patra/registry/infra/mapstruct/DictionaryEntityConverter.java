package com.patra.registry.infra.mapstruct;

import com.patra.registry.domain.model.vo.DictionaryAlias;
import com.patra.registry.domain.model.vo.DictionaryItem;
import com.patra.registry.domain.model.vo.DictionaryType;
import com.patra.registry.infra.persistence.entity.RegSysDictItemAliasDO;
import com.patra.registry.infra.persistence.entity.RegSysDictItemDO;
import com.patra.registry.infra.persistence.entity.RegSysDictTypeDO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

import java.util.List;

/**
 * MapStruct converter for dictionary entity to domain object mapping.
 * Handles conversion between database entities and domain objects in the CQRS read pipeline.
 * This converter is used exclusively for read operations and maintains clean boundaries
 * between infrastructure and domain layers.
 * 
 * @author linqibin
 * @since 0.1.0
 */
@Mapper(
    componentModel = MappingConstants.ComponentModel.SPRING,
    unmappedTargetPolicy = ReportingPolicy.IGNORE,
    unmappedSourcePolicy = ReportingPolicy.IGNORE
)
public interface DictionaryEntityConverter {

    /**
     * Convert dictionary type entity to domain object.
     * Maps database entity fields to domain object properties with appropriate transformations.
     * Handles null-safe conversion of Boolean wrapper types to primitive boolean.
     * 
     * @param entity the dictionary type entity from database, must not be null
     * @return domain object representing the dictionary type
     */
    DictionaryType toDomain(RegSysDictTypeDO entity);

    /**
     * Convert dictionary item entity to domain object.
     * Maps database entity fields to domain object properties with appropriate transformations.
     * Handles null-safe conversion of Boolean wrapper types to primitive boolean.
     * 
     * @param entity the dictionary item entity from database, must not be null
     * @return domain object representing the dictionary item
     */
    @Mapping(target = "displayName", source = "itemName")
    @Mapping(target = "sortOrder", source = "displayOrder")
    DictionaryItem toDomain(RegSysDictItemDO entity);

    /**
     * Convert dictionary alias entity to domain object.
     * Maps database entity fields to domain object properties with appropriate transformations.
     * Handles null-safe conversion and provides empty string defaults for optional fields.
     * 
     * @param entity the dictionary alias entity from database, must not be null
     * @return domain object representing the dictionary alias
     */
    DictionaryAlias toDomain(RegSysDictItemAliasDO entity);

    /**
     * Convert list of dictionary type entities to domain objects.
     * Provides batch conversion for efficient processing of multiple entities.
     * 
     * @param entities list of dictionary type entities from database, must not be null
     * @return list of domain objects representing dictionary types
     */
    List<DictionaryType> toDomainList(List<RegSysDictTypeDO> entities);

    /**
     * Convert list of dictionary item entities to domain objects.
     * Provides batch conversion for efficient processing of multiple entities.
     * 
     * @param entities list of dictionary item entities from database, must not be null
     * @return list of domain objects representing dictionary items
     */
    List<DictionaryItem> toItemDomainList(List<RegSysDictItemDO> entities);

    /**
     * Convert list of dictionary alias entities to domain objects.
     * Provides batch conversion for efficient processing of multiple entities.
     * 
     * @param entities list of dictionary alias entities from database, must not be null
     * @return list of domain objects representing dictionary aliases
     */
    List<DictionaryAlias> toAliasDomainList(List<RegSysDictItemAliasDO> entities);
}