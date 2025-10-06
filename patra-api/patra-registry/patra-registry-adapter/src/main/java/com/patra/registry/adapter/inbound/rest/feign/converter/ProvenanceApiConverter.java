package com.patra.registry.adapter.inbound.rest.feign.converter;

import com.patra.registry.api.rpc.dto.provenance.BatchingConfigResp;
import com.patra.registry.api.rpc.dto.provenance.HttpConfigResp;
import com.patra.registry.api.rpc.dto.provenance.PaginationConfigResp;
import com.patra.registry.api.rpc.dto.provenance.ProvenanceConfigResp;
import com.patra.registry.api.rpc.dto.provenance.ProvenanceResp;
import com.patra.registry.api.rpc.dto.provenance.RateLimitConfigResp;
import com.patra.registry.api.rpc.dto.provenance.RetryConfigResp;
import com.patra.registry.api.rpc.dto.provenance.WindowOffsetResp;
import com.patra.registry.domain.model.read.provenance.BatchingConfigQuery;
import com.patra.registry.domain.model.read.provenance.HttpConfigQuery;
import com.patra.registry.domain.model.read.provenance.PaginationConfigQuery;
import com.patra.registry.domain.model.read.provenance.ProvenanceConfigQuery;
import com.patra.registry.domain.model.read.provenance.ProvenanceQuery;
import com.patra.registry.domain.model.read.provenance.RateLimitConfigQuery;
import com.patra.registry.domain.model.read.provenance.RetryConfigQuery;
import com.patra.registry.domain.model.read.provenance.WindowOffsetQuery;
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

    /**
     * Converts a provenance query model to an API response DTO.
     *
     * @param query the provenance query model produced by application layer
     * @return the provenance response DTO exposed by the RPC contract
     */
    ProvenanceResp toResp(ProvenanceQuery query);

    /**
     * Converts provenance query models to API response DTOs.
     *
     * @param queries the collection of provenance query models
     * @return the list of response DTOs preserving iteration order
     */
    List<ProvenanceResp> toResp(List<ProvenanceQuery> queries);

    /**
     * Converts window offset query model to an API response DTO.
     *
     * @param query the window offset query model
     * @return the window offset response DTO
     */
    WindowOffsetResp toResp(WindowOffsetQuery query);

    /**
     * Converts pagination configuration query model to an API response DTO.
     *
     * @param query the pagination configuration query model
     * @return the pagination configuration response DTO
     */
    PaginationConfigResp toResp(PaginationConfigQuery query);

    /**
     * Converts HTTP configuration query model to an API response DTO.
     *
     * @param query the HTTP configuration query model
     * @return the HTTP configuration response DTO
     */
    HttpConfigResp toResp(HttpConfigQuery query);

    /**
     * Converts batching configuration query model to an API response DTO.
     *
     * @param query the batching configuration query model
     * @return the batching configuration response DTO
     */
    BatchingConfigResp toResp(BatchingConfigQuery query);

    /**
     * Converts retry configuration query model to an API response DTO.
     *
     * @param query the retry configuration query model
     * @return the retry configuration response DTO
     */
    RetryConfigResp toResp(RetryConfigQuery query);

    /**
     * Converts rate limit configuration query model to an API response DTO.
     *
     * @param query the rate limit configuration query model
     * @return the rate limit configuration response DTO
     */
    RateLimitConfigResp toResp(RateLimitConfigQuery query);

    // Credential dimension removed

    /**
     * Converts aggregated provenance configuration query model to an API response DTO.
     *
     * @param query the aggregated provenance configuration query model
     * @return the configuration response DTO consolidating all dimensions
     */
    ProvenanceConfigResp toResp(ProvenanceConfigQuery query);
}
