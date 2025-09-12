package com.patra.registry.adapter.rest._public.convertor;

import cn.hutool.core.collection.CollUtil;
import com.patra.registry.adapter.rest._public.resp.dto.resp.ProvenanceSummaryResp;
import com.patra.registry.contract.query.view.ProvenanceSummaryView;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

import java.util.List;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING,
        unmappedTargetPolicy = org.mapstruct.ReportingPolicy.IGNORE
)
public interface ProvenanceWebConverter {

    ProvenanceSummaryResp toResp(ProvenanceSummaryView summary);

    default List<ProvenanceSummaryResp> toRespList(List<ProvenanceSummaryView> summaries) {
        if (CollUtil.isEmpty(summaries)) {
            return List.of();
        }
        return summaries.stream().map(this::toResp).toList();
    }
}
