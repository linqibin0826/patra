package com.patra.registry.adapter.rest._internal.convertor;

import com.patra.registry.api.rpc.dto.dict.DictionaryHealthResp;
import com.patra.registry.api.rpc.dto.dict.DictionaryItemResp;
import com.patra.registry.api.rpc.dto.dict.DictionaryReferenceReq;
import com.patra.registry.api.rpc.dto.dict.DictionaryTypeResp;
import com.patra.registry.api.rpc.dto.dict.DictionaryValidationResp;
import com.patra.registry.domain.model.read.dictionary.DictionaryHealthQuery;
import com.patra.registry.domain.model.read.dictionary.DictionaryItemQuery;
import com.patra.registry.domain.model.read.dictionary.DictionaryTypeQuery;
import com.patra.registry.domain.model.read.dictionary.DictionaryValidationQuery;
import com.patra.registry.domain.model.vo.dictionary.DictionaryReference;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

import java.util.List;

/**
 * MapStruct 转换器：契约 Query 对象 <-> API DTO（字典内部端点）。
 */
@Mapper(
        componentModel = MappingConstants.ComponentModel.SPRING,
        unmappedTargetPolicy = ReportingPolicy.ERROR
)
public interface DictionaryApiConvertor {

    DictionaryItemResp toItemResp(DictionaryItemQuery query);

    List<DictionaryItemResp> toItemResp(List<DictionaryItemQuery> queries);

    DictionaryTypeResp toTypeResp(DictionaryTypeQuery query);

    List<DictionaryTypeResp> toTypeResp(List<DictionaryTypeQuery> queries);

    @Mapping(target = "valid", source = "isValid")
    DictionaryValidationResp toValidationResp(DictionaryValidationQuery query);

    List<DictionaryValidationResp> toValidationResp(List<DictionaryValidationQuery> queries);

    DictionaryHealthResp toHealthResp(DictionaryHealthQuery query);

    DictionaryReference toReference(DictionaryReferenceReq request);

    List<DictionaryReference> toReference(List<DictionaryReferenceReq> requests);
}
