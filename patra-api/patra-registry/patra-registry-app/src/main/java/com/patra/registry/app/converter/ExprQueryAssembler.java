package com.patra.registry.app.converter;

import com.patra.registry.domain.model.read.expr.ApiParamMappingQuery;
import com.patra.registry.domain.model.read.expr.ExprCapabilityQuery;
import com.patra.registry.domain.model.read.expr.ExprFieldQuery;
import com.patra.registry.domain.model.read.expr.ExprRenderRuleQuery;
import com.patra.registry.domain.model.read.expr.ExprSnapshotQuery;
import com.patra.registry.domain.model.vo.expr.ApiParamMapping;
import com.patra.registry.domain.model.vo.expr.ExprCapability;
import com.patra.registry.domain.model.vo.expr.ExprField;
import com.patra.registry.domain.model.vo.expr.ExprRenderRule;
import com.patra.registry.domain.model.vo.expr.ExprSnapshot;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

import java.util.List;

/**
 * MapStruct assembler for converting expression domain objects to query DTOs.
 *
 * <p>Transforms domain value objects (fields, capabilities, render rules, mappings)
 * into read-side contract DTOs for consumption by external clients.</p>
 *
 * @author linqibin
 * @since 0.1.0
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface ExprQueryAssembler {

    ExprFieldQuery toQuery(ExprField field);

    List<ExprFieldQuery> toFieldQueries(List<ExprField> fields);

    ApiParamMappingQuery toQuery(ApiParamMapping mapping);

    List<ApiParamMappingQuery> toMappingQueries(List<ApiParamMapping> mappings);

    ExprCapabilityQuery toQuery(ExprCapability capability);

    List<ExprCapabilityQuery> toCapabilityQueries(List<ExprCapability> capabilities);

    ExprRenderRuleQuery toQuery(ExprRenderRule rule);

    List<ExprRenderRuleQuery> toRenderRuleQueries(List<ExprRenderRule> rules);

    default ExprSnapshotQuery toQuery(ExprSnapshot snapshot) {
        return new ExprSnapshotQuery(
                toFieldQueries(snapshot.fields()),
                toCapabilityQueries(snapshot.capabilities()),
                toRenderRuleQueries(snapshot.renderRules()),
                toMappingQueries(snapshot.apiParamMappings())
        );
    }
}
