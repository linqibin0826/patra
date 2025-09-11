package com.patra.registry.adapter.rest._internal.converter;

import com.patra.registry.api.rpc.dto.PlatformFieldDictApiResp;
import com.patra.registry.contract.query.view.PlatformFieldDictView;
import org.mapstruct.*;

import java.util.ArrayList;
import java.util.List;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING,
        unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface PlatformFieldDictApiConverter {

    PlatformFieldDictApiResp toApiResp(PlatformFieldDictView src);

    default List<PlatformFieldDictApiResp> toApiRespList(List<PlatformFieldDictView> list) {
        if (list == null || list.isEmpty()) return java.util.List.of();
        List<PlatformFieldDictApiResp> res = new ArrayList<>(list.size());
        for (PlatformFieldDictView it : list) res.add(toApiResp(it));
        return res;
    }
}
