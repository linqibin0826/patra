package com.patra.registry.app.mapping;

import com.patra.registry.domain.model.read.dictionary.DictionaryItemQuery;
import com.patra.registry.domain.model.read.dictionary.DictionaryTypeQuery;
import com.patra.registry.domain.model.vo.dictionary.DictionaryItem;
import com.patra.registry.domain.model.vo.dictionary.DictionaryType;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/**
 * MapStruct 转换器：领域对象 -> 契约层 Query 对象。
 *
 * <p>用于 CQRS 查询侧，将领域 VO 转换为对外 Query 对象（字典项/类型）。</p>
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
     * 领域 DictionaryItem -> 契约 DictionaryItemQuery。
     *
     * <p>排除内部字段（如 deleted）。</p>
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
     * 领域 DictionaryType -> 契约 DictionaryTypeQuery（含计算字段）。
     */
    @Mapping(target = "typeCode", source = "domainType.typeCode")
    @Mapping(target = "typeName", source = "domainType.typeName")
    @Mapping(target = "description", source = "domainType.description")
    @Mapping(target = "enabledItemCount", source = "enabledItemCount")
    @Mapping(target = "hasDefault", source = "hasDefault")
    DictionaryTypeQuery toQuery(DictionaryType domainType, int enabledItemCount, boolean hasDefault);
}
