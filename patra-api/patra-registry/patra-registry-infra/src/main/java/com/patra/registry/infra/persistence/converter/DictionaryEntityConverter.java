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
 * MapStruct 转换器：实体 -> 领域对象（查询侧）。
 *
 * <p>用于 CQRS 查询管线，将数据库实体转换为领域对象；仅用于读操作，确保基础设施层与领域层边界清晰。</p>
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
     * 将字典类型实体转换为领域对象。
     *
     * @see com.patra.registry.infra.persistence.entity.dictionary.RegSysDictTypeDO
     * @see DictionaryType
     */
    DictionaryType toDomain(RegSysDictTypeDO entity);

    /**
     * 将字典项实体转换为领域对象。
     *
     * @see RegSysDictItemDO
     * @see DictionaryItem
     */
    @Mapping(target = "displayName", source = "itemName")
    @Mapping(target = "sortOrder", source = "displayOrder")
    DictionaryItem toDomain(RegSysDictItemDO entity);

    /**
     * 将字典别名实体转换为领域对象。
     *
     * @see RegSysDictItemAliasDO
     * @see DictionaryAlias
     */
    DictionaryAlias toDomain(RegSysDictItemAliasDO entity);

    /**
     * 批量：类型实体 -> 领域对象。
     *
     * @see #toDomain(RegSysDictTypeDO)
     */
    List<DictionaryType> toDomainList(List<RegSysDictTypeDO> entities);

    /**
     * 批量：项实体 -> 领域对象。
     *
     * @see #toDomain(RegSysDictItemDO)
     */
    List<DictionaryItem> toItemDomainList(List<RegSysDictItemDO> entities);

    /**
     * 批量：别名实体 -> 领域对象。
     *
     * @see #toDomain(RegSysDictItemAliasDO)
     */
    List<DictionaryAlias> toAliasDomainList(List<RegSysDictItemAliasDO> entities);
}
