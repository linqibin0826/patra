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

    ProvenanceQuery toQuery(Provenance provenance);

    WindowOffsetQuery toQuery(WindowOffsetConfig config);

    PaginationConfigQuery toQuery(PaginationConfig config);

    HttpConfigQuery toQuery(HttpConfig config);

    BatchingConfigQuery toQuery(BatchingConfig config);

    RetryConfigQuery toQuery(RetryConfig config);

    RateLimitConfigQuery toQuery(RateLimitConfig config);

    // Credential dimension removed

    ProvenanceConfigQuery toQuery(ProvenanceConfiguration configuration);
}
