package com.patra.registry.adapter.inbound.rest.feign.converter;

import com.patra.registry.api.rpc.dto.expr.ApiParamMappingResp;
import com.patra.registry.api.rpc.dto.expr.ExprCapabilityResp;
import com.patra.registry.api.rpc.dto.expr.ExprFieldResp;
import com.patra.registry.api.rpc.dto.expr.ExprRenderRuleResp;
import com.patra.registry.api.rpc.dto.expr.ExprSnapshotResp;
import com.patra.registry.domain.model.read.expr.ApiParamMappingQuery;
import com.patra.registry.domain.model.read.expr.ExprCapabilityQuery;
import com.patra.registry.domain.model.read.expr.ExprFieldQuery;
import com.patra.registry.domain.model.read.expr.ExprRenderRuleQuery;
import com.patra.registry.domain.model.read.expr.ExprSnapshotQuery;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

import java.util.List;

/**
 * MapStruct converter for transforming expression query DTOs to API response DTOs.
 *
 * <p>Maps read-side domain query objects to external API contract DTOs for
 * consumption by Feign clients from other microservices.</p>
 *
 * @author linqibin
 * @since 0.1.0
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface ExprApiConverter {

    ExprFieldResp toResp(ExprFieldQuery query);

    List<ExprFieldResp> toResp(List<ExprFieldQuery> queries);

    ApiParamMappingResp toResp(ApiParamMappingQuery query);

    ExprCapabilityResp toResp(ExprCapabilityQuery query);

    ExprRenderRuleResp toResp(ExprRenderRuleQuery query);

    ExprSnapshotResp toResp(ExprSnapshotQuery query);
}
