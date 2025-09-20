package com.patra.registry.app.mapping;

import com.patra.registry.contract.query.view.expr.ApiParamMappingQuery;
import com.patra.registry.contract.query.view.expr.ExprCapabilityQuery;
import com.patra.registry.contract.query.view.expr.ExprFieldQuery;
import com.patra.registry.contract.query.view.expr.ExprRenderRuleQuery;
import com.patra.registry.domain.model.vo.expr.ApiParamMapping;
import com.patra.registry.domain.model.vo.expr.ExprCapability;
import com.patra.registry.domain.model.vo.expr.ExprField;
import com.patra.registry.domain.model.vo.expr.ExprRenderRule;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * Expr 领域对象 -> 契约 Query 的转换器。
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface ExprQueryAssembler {

    ExprFieldQuery toQuery(ExprField field);

    ApiParamMappingQuery toQuery(ApiParamMapping mapping);

    ExprCapabilityQuery toQuery(ExprCapability capability);

    ExprRenderRuleQuery toQuery(ExprRenderRule rule);
}
