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
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * MapStruct converter for transforming expression query DTOs to API response DTOs.
 *
 * <p>Maps read-side domain query objects to external API contract DTOs for consumption by Feign
 * clients from other microservices.
 *
 * @author linqibin
 * @since 0.1.0
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface ExprApiConverter {

  /**
   * Converts a single expression field query model to an API response DTO.
   *
   * @param query the field query model produced by application layer
   * @return the response DTO exposed by the RPC contract
   */
  ExprFieldResp toResp(ExprFieldQuery query);

  /**
   * Converts expression field query models to API response DTOs.
   *
   * @param queries the collection of field query models
   * @return the list of response DTOs preserving iteration order
   */
  List<ExprFieldResp> toResp(List<ExprFieldQuery> queries);

  /**
   * Converts an API parameter mapping query model to its response DTO.
   *
   * @param query the mapping query model produced by application layer
   * @return the API response DTO reflecting the mapping configuration
   */
  ApiParamMappingResp toResp(ApiParamMappingQuery query);

  /**
   * Converts an expression capability query model to an API response DTO.
   *
   * @param query the capability query model
   * @return the response DTO consumed by downstream clients
   */
  ExprCapabilityResp toResp(ExprCapabilityQuery query);

  /**
   * Converts an expression render rule query model to an API response DTO.
   *
   * @param query the render rule query model
   * @return the render rule response DTO
   */
  ExprRenderRuleResp toResp(ExprRenderRuleQuery query);

  /**
   * Converts an aggregated expression snapshot query model to an API response DTO.
   *
   * @param query the aggregated snapshot query model
   * @return the snapshot response DTO distributed to callers
   */
  ExprSnapshotResp toResp(ExprSnapshotQuery query);
}
