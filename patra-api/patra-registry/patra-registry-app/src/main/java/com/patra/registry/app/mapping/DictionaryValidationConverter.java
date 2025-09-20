package com.patra.registry.app.mapping;

import com.patra.registry.contract.query.view.DictionaryHealthQuery;
import com.patra.registry.contract.query.view.DictionaryValidationQuery;
import com.patra.registry.domain.model.vo.DictionaryHealthStatus;
import com.patra.registry.domain.model.vo.ValidationResult;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/**
 * MapStruct 转换器：领域校验/健康对象 -> 契约层 Query 对象。
 *
 * <p>用于 CQRS 查询侧，将领域校验结果与健康状态映射为对外查询对象。</p>
 *
 * @author linqibin
 * @since 0.1.0
 */
@Mapper(
    componentModel = "spring",
    unmappedTargetPolicy = ReportingPolicy.ERROR
)
public interface DictionaryValidationConverter {
    
    /** 领域 ValidationResult -> 契约 DictionaryValidationQuery。 */
    @Mapping(target = "typeCode", source = "typeCode")
    @Mapping(target = "itemCode", source = "itemCode")
    @Mapping(target = "isValid", source = "validationResult.isValid")
    @Mapping(target = "errorMessage", source = "validationResult.errorMessage")
    DictionaryValidationQuery toQuery(ValidationResult validationResult, String typeCode, String itemCode);
    
    /** 领域 DictionaryHealthStatus -> 契约 DictionaryHealthQuery。 */
    @Mapping(target = "totalTypes", source = "totalTypes")
    @Mapping(target = "totalItems", source = "totalItems")
    @Mapping(target = "enabledItems", source = "enabledItems")
    @Mapping(target = "typesWithoutDefault", source = "typesWithoutDefault")
    @Mapping(target = "typesWithMultipleDefaults", source = "typesWithMultipleDefaults")
    DictionaryHealthQuery toQuery(DictionaryHealthStatus healthStatus);
}
