package com.patra.registry.adapter.rest._internal.convertor;

import com.patra.registry.api.rpc.dto.provenance.BatchingConfigResp;
import com.patra.registry.api.rpc.dto.provenance.CredentialResp;
import com.patra.registry.api.rpc.dto.provenance.EndpointDefinitionResp;
import com.patra.registry.api.rpc.dto.provenance.HttpConfigResp;
import com.patra.registry.api.rpc.dto.provenance.PaginationConfigResp;
import com.patra.registry.api.rpc.dto.provenance.ProvenanceConfigResp;
import com.patra.registry.api.rpc.dto.provenance.ProvenanceResp;
import com.patra.registry.api.rpc.dto.provenance.RateLimitConfigResp;
import com.patra.registry.api.rpc.dto.provenance.RetryConfigResp;
import com.patra.registry.api.rpc.dto.provenance.WindowOffsetResp;
import com.patra.registry.contract.query.view.provenance.*;
import com.patra.registry.contract.query.view.provenance.ProvenanceConfigQuery;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

import java.util.List;

/**
 * Provenance 领域 Query -> API DTO 的转换器。
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface ProvenanceApiConvertor {

    ProvenanceResp toResp(ProvenanceQuery query);

    List<ProvenanceResp> toResp(List<ProvenanceQuery> queries);

    EndpointDefinitionResp toResp(EndpointDefinitionQuery query);

    WindowOffsetResp toResp(WindowOffsetQuery query);

    PaginationConfigResp toResp(PaginationConfigQuery query);

    HttpConfigResp toResp(HttpConfigQuery query);

    BatchingConfigResp toResp(BatchingConfigQuery query);

    RetryConfigResp toResp(RetryConfigQuery query);

    RateLimitConfigResp toResp(RateLimitConfigQuery query);

    CredentialResp toResp(CredentialQuery query);

    List<CredentialResp> toCredentialResp(List<CredentialQuery> queries);

    ProvenanceConfigResp toResp(ProvenanceConfigQuery query);
}
