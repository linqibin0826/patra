package com.patra.registry.adapter.rest._internal.convertor;

import com.patra.registry.api.rpc.dto.expr.ApiParamMappingResp;
import com.patra.registry.api.rpc.dto.expr.ExprCapabilityResp;
import com.patra.registry.api.rpc.dto.expr.ExprFieldResp;
import com.patra.registry.api.rpc.dto.expr.ExprRenderRuleResp;
import com.patra.registry.contract.query.view.expr.ApiParamMappingQuery;
import com.patra.registry.contract.query.view.expr.ExprCapabilityQuery;
import com.patra.registry.contract.query.view.expr.ExprFieldQuery;
import com.patra.registry.contract.query.view.expr.ExprRenderRuleQuery;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

import java.util.List;

/**
 * Expr 领域 Query -> API DTO 的转换器。
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface ExprApiConvertor {

    ExprFieldResp toResp(ExprFieldQuery query);

    List<ExprFieldResp> toResp(List<ExprFieldQuery> queries);

    ApiParamMappingResp toResp(ApiParamMappingQuery query);

    ExprCapabilityResp toResp(ExprCapabilityQuery query);

    ExprRenderRuleResp toResp(ExprRenderRuleQuery query);
}
