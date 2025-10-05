package com.patra.registry.adapter.inbound.rest.feign.converter;

import com.patra.registry.api.rpc.dto.provenance.BatchingConfigResp;
import com.patra.registry.api.rpc.dto.provenance.HttpConfigResp;
import com.patra.registry.api.rpc.dto.provenance.PaginationConfigResp;
import com.patra.registry.api.rpc.dto.provenance.ProvenanceConfigResp;
import com.patra.registry.api.rpc.dto.provenance.ProvenanceResp;
import com.patra.registry.api.rpc.dto.provenance.RateLimitConfigResp;
import com.patra.registry.api.rpc.dto.provenance.RetryConfigResp;
import com.patra.registry.api.rpc.dto.provenance.WindowOffsetResp;
import com.patra.registry.domain.model.read.provenance.*;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

import java.util.List;

/**
 * MapStruct converter for transforming provenance query DTOs to API response DTOs.
 *
 * <p>Maps read-side domain query objects to external API contract DTOs for
 * consumption by Feign clients from other microservices.</p>
 *
 * @author linqibin
 * @since 0.1.0
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface ProvenanceApiConverter {

    ProvenanceResp toResp(ProvenanceQuery query);

    List<ProvenanceResp> toResp(List<ProvenanceQuery> queries);

    WindowOffsetResp toResp(WindowOffsetQuery query);

    PaginationConfigResp toResp(PaginationConfigQuery query);

    HttpConfigResp toResp(HttpConfigQuery query);

    BatchingConfigResp toResp(BatchingConfigQuery query);

    RetryConfigResp toResp(RetryConfigQuery query);

    RateLimitConfigResp toResp(RateLimitConfigQuery query);

    // Credential dimension removed

    ProvenanceConfigResp toResp(ProvenanceConfigQuery query);
}
