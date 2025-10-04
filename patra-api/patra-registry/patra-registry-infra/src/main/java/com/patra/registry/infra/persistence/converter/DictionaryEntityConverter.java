package com.patra.registry.infra.persistence.converter;

import com.patra.registry.domain.model.vo.dictionary.DictionaryAlias;
import com.patra.registry.domain.model.vo.dictionary.DictionaryItem;
import com.patra.registry.domain.model.vo.dictionary.DictionaryType;
import com.patra.registry.infra.persistence.entity.dictionary.RegSysDictItemAliasDO;
import com.patra.registry.infra.persistence.entity.dictionary.RegSysDictItemDO;
import com.patra.registry.infra.persistence.entity.dictionary.RegSysDictTypeDO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

import java.util.List;

/**
 * MapStruct converter for dictionary entities on the query side of CQRS.
 * Translates persistence layer records into domain view objects.
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
     * Converts a dictionary type entity to its domain representation.
     */
    DictionaryType toDomain(RegSysDictTypeDO entity);

    /**
     * Converts a dictionary item entity to its domain representation.
     */
    @Mapping(target = "displayName", source = "itemName")
    @Mapping(target = "sortOrder", source = "displayOrder")
    DictionaryItem toDomain(RegSysDictItemDO entity);

    /**
     * Converts a dictionary alias entity to its domain representation.
     */
    DictionaryAlias toDomain(RegSysDictItemAliasDO entity);

    /**
     * Batch conversion helper for dictionary type entities.
     */
    List<DictionaryType> toDomainList(List<RegSysDictTypeDO> entities);

    /**
     * Batch conversion helper for dictionary item entities.
     */
    List<DictionaryItem> toItemDomainList(List<RegSysDictItemDO> entities);

    /**
     * Batch conversion helper for dictionary alias entities.
     */
    List<DictionaryAlias> toAliasDomainList(List<RegSysDictItemAliasDO> entities);
}
