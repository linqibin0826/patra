package com.patra.registry.app.converter;

import com.patra.registry.domain.model.read.dictionary.DictionaryItemQuery;
import com.patra.registry.domain.model.read.dictionary.DictionaryItemView;
import com.patra.registry.domain.model.read.dictionary.DictionaryTypeQuery;
import com.patra.registry.domain.model.read.dictionary.DictionaryTypeView;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import java.util.List;

/**
 * MapStruct 转换器：契约 Query 对象 -> View 对象。
 *
 * <p>用于对外 API 展示，排除内部系统字段。</p>
 *
 * @author linqibin
 * @since 0.1.0
 */
@Mapper(
    componentModel = "spring",
    unmappedTargetPolicy = ReportingPolicy.ERROR
)
public interface DictionaryViewConverter {
    
    /** 将 DictionaryItemQuery 转为对外展示的 DictionaryItemView。 */
    @Mapping(target = "typeCode", source = "typeCode")
    @Mapping(target = "itemCode", source = "itemCode")
    @Mapping(target = "displayName", source = "displayName")
    @Mapping(target = "description", source = "description")
    @Mapping(target = "isDefault", source = "isDefault")
    @Mapping(target = "sortOrder", source = "sortOrder")
    DictionaryItemView toView(DictionaryItemQuery query);

    /** 将 DictionaryTypeQuery 转为对外展示的 DictionaryTypeView。 */
    @Mapping(target = "typeCode", source = "typeCode")
    @Mapping(target = "typeName", source = "typeName")
    @Mapping(target = "description", source = "description")
    @Mapping(target = "itemCount", source = "enabledItemCount")
    DictionaryTypeView toView(DictionaryTypeQuery query);
    
    /** 批量转换类型视图。 */
    List<DictionaryTypeView> toViews(List<DictionaryTypeQuery> queries);
}
