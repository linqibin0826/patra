package com.patra.registry.app.converter;

import com.patra.registry.domain.model.read.provenance.BatchingConfigQuery;
import com.patra.registry.domain.model.read.provenance.HttpConfigQuery;
import com.patra.registry.domain.model.read.provenance.PaginationConfigQuery;
import com.patra.registry.domain.model.read.provenance.ProvenanceConfigQuery;
import com.patra.registry.domain.model.read.provenance.ProvenanceQuery;
import com.patra.registry.domain.model.read.provenance.RateLimitConfigQuery;
import com.patra.registry.domain.model.read.provenance.RetryConfigQuery;
import com.patra.registry.domain.model.read.provenance.WindowOffsetQuery;
import com.patra.registry.domain.model.aggregate.ProvenanceConfiguration;
import com.patra.registry.domain.model.vo.provenance.BatchingConfig;
import com.patra.registry.domain.model.vo.provenance.HttpConfig;
import com.patra.registry.domain.model.vo.provenance.PaginationConfig;
import com.patra.registry.domain.model.vo.provenance.Provenance;
import com.patra.registry.domain.model.vo.provenance.RateLimitConfig;
import com.patra.registry.domain.model.vo.provenance.RetryConfig;
import com.patra.registry.domain.model.vo.provenance.WindowOffsetConfig;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

import java.util.List;

/**
 * MapStruct assembler for converting provenance domain objects to query DTOs.
 *
 * <p>Transforms domain value objects and aggregates into read-side contract DTOs
 * for consumption by external clients (REST APIs, Feign clients, etc.).</p>
 *
 * @author linqibin
 * @since 0.1.0
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface ProvenanceQueryAssembler {
    /**
     * Converts a provenance aggregate root to its query DTO.
     *
     * @param provenance the domain provenance aggregate
     * @return the query DTO exposing provenance metadata
     */
    ProvenanceQuery toQuery(Provenance provenance);

    /**
     * Converts a window offset configuration to its query DTO representation.
     *
     * @param config the domain window offset configuration
     * @return the query DTO describing offset windows
     */
    WindowOffsetQuery toQuery(WindowOffsetConfig config);

    /**
     * Converts pagination configuration to its query DTO form.
     *
     * @param config the domain pagination configuration
     * @return the query DTO conveying pagination rules
     */
    PaginationConfigQuery toQuery(PaginationConfig config);

    /**
     * Converts an HTTP configuration to its query DTO representation.
     *
     * @param config the domain HTTP configuration
     * @return the query DTO carrying HTTP interaction settings
     */
    HttpConfigQuery toQuery(HttpConfig config);

    /**
     * Converts batching configuration to its query DTO form.
     *
     * @param config the domain batching configuration
     * @return the query DTO outlining batching behavior
     */
    BatchingConfigQuery toQuery(BatchingConfig config);

    /**
     * Converts retry configuration to its query DTO representation.
     *
     * @param config the domain retry configuration
     * @return the query DTO carrying retry policy attributes
     */
    RetryConfigQuery toQuery(RetryConfig config);

    /**
     * Converts rate limit configuration to its query DTO representation.
     *
     * @param config the domain rate limit configuration
     * @return the query DTO describing throttling rules
     */
    RateLimitConfigQuery toQuery(RateLimitConfig config);

    // Credential dimension removed

    /**
     * Converts the provenance configuration aggregate to its query DTO.
     *
     * @param configuration the domain provenance configuration aggregate
     * @return the query DTO consolidating all dimension configurations
     */
    ProvenanceConfigQuery toQuery(ProvenanceConfiguration configuration);
}
