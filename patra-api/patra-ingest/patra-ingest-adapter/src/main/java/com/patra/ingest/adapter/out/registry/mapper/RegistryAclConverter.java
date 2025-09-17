package com.patra.ingest.adapter.out.registry.mapper;

import com.patra.ingest.app.model.registry.ProvenanceConfigSnapshot;
import com.patra.registry.api.rpc.dto.LiteratureProvenanceConfigApiResp;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING,
        unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface RegistryAclConverter {

    @Mapping(target = "timezone", expression = "java(resp.timezone() == null ? ZoneId.of(\"UTC\") : ZoneId.of(resp.timezone()))")
    @Mapping(target = "retryPolicy", expression =
            "java(new ProvenanceConfigSnapshot.RetryPolicy(resp.retryMax(), resp.backoffMs(), resp.retryJitter()==null?0.0:resp.retryJitter()))")
    @Mapping(target = "rateLimitPolicy", expression =
            "java(new ProvenanceConfigSnapshot.RateLimitPolicy(resp.rateLimitPerSec()==null?0:resp.rateLimitPerSec()))")
    @Mapping(target = "pagingPolicy", expression =
            "java(new ProvenanceConfigSnapshot.PagingPolicy(resp.searchPageSize(), resp.fetchBatchSize()))")
    @Mapping(target = "windowPolicy", expression =
            "java(new ProvenanceConfigSnapshot.WindowPolicy(resp.overlapDays()==null?0:resp.overlapDays()," +
                    " resp.maxSearchIdsPerWindow()==null?0:resp.maxSearchIdsPerWindow()))")
    @Mapping(target = "publicHeaders", source = "headers")
    ProvenanceConfigSnapshot toProvenanceConfigSnapshot(LiteratureProvenanceConfigApiResp resp);
}
