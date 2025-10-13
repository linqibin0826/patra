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
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * MapStruct assembler for converting expression domain objects to query DTOs.
 *
 * <p>Transforms domain value objects (fields, capabilities, render rules, mappings) into read-side
 * contract DTOs for consumption by external clients.
 *
 * @author linqibin
 * @since 0.1.0
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface ExprQueryAssembler {
  /**
   * Converts a single expression field value object to its read-model DTO.
   *
   * @param field the domain expression field to convert
   * @return the corresponding query DTO
   */
  ExprFieldQuery toQuery(ExprField field);

  /**
   * Converts a collection of expression fields to read-model DTOs.
   *
   * @param fields the domain expression field collection
   * @return the list of query DTOs preserving iteration order
   */
  List<ExprFieldQuery> toFieldQueries(List<ExprField> fields);

  /**
   * Converts an API parameter mapping to its query DTO representation.
   *
   * @param mapping the domain API parameter mapping
   * @return the query DTO mirroring the mapping properties
   */
  ApiParamMappingQuery toQuery(ApiParamMapping mapping);

  /**
   * Converts an API parameter mapping collection to query DTOs.
   *
   * @param mappings the domain API parameter mapping collection
   * @return the list of query DTOs aligned with the provided ordering
   */
  List<ApiParamMappingQuery> toMappingQueries(List<ApiParamMapping> mappings);

  /**
   * Converts an expression capability to its read-model DTO.
   *
   * @param capability the domain capability value object
   * @return the query DTO describing the capability
   */
  ExprCapabilityQuery toQuery(ExprCapability capability);

  /**
   * Converts expression capabilities to query DTOs.
   *
   * @param capabilities the domain capability collection
   * @return the list of query DTOs mirroring the provided capabilities
   */
  List<ExprCapabilityQuery> toCapabilityQueries(List<ExprCapability> capabilities);

  /**
   * Converts a render rule to its query DTO form.
   *
   * @param rule the domain render rule value object
   * @return the query DTO describing the render rule
   */
  ExprRenderRuleQuery toQuery(ExprRenderRule rule);

  /**
   * Converts render rules to a list of query DTOs.
   *
   * @param rules the domain render rule collection
   * @return the list of query DTOs matching the render rules
   */
  List<ExprRenderRuleQuery> toRenderRuleQueries(List<ExprRenderRule> rules);

  /**
   * Converts an expression snapshot aggregate to its read-model counterpart.
   *
   * @param snapshot the domain expression snapshot aggregate
   * @return the query DTO containing fields, capabilities, render rules, and mappings
   */
  default ExprSnapshotQuery toQuery(ExprSnapshot snapshot) {
    return new ExprSnapshotQuery(
        toFieldQueries(snapshot.fields()),
        toCapabilityQueries(snapshot.capabilities()),
        toRenderRuleQueries(snapshot.renderRules()),
        toMappingQueries(snapshot.apiParamMappings()));
  }
}
