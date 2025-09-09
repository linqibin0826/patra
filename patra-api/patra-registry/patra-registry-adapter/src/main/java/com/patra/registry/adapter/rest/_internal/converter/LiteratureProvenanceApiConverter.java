package com.patra.registry.adapter.rest._internal.converter;

import com.patra.registry.api.rpc.dto.LiteratureProvenanceConfigApiResp;
import com.patra.registry.contract.query.view.LiteratureProvenanceConfigView;
import org.mapstruct.*;

@Mapper(
    componentModel = "spring",
    unmappedTargetPolicy = ReportingPolicy.ERROR // 字段漏映射就编译报错，避免线上惊喜
)
public interface LiteratureProvenanceApiConverter {

    LiteratureProvenanceConfigApiResp toConfigApiResp(LiteratureProvenanceConfigView src);
}
