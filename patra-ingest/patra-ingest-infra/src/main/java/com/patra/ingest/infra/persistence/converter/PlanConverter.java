package com.patra.ingest.infra.persistence.converter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.patra.common.json.JsonMapperHolder;
import com.patra.common.json.JsonNodeMappings;
import com.patra.ingest.domain.model.aggregate.PlanAggregate;
import com.patra.ingest.domain.model.enums.PlanStatus;
import com.patra.ingest.domain.model.vo.WindowSpec;
import com.patra.ingest.infra.persistence.entity.PlanDO;
import java.util.Map;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;

/** Plan aggregate {@link PlanAggregate} ↔ data object {@link PlanDO} converter. */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface PlanConverter {

  @Mapping(
      target = "exprProtoSnapshot",
      expression =
          "java(com.patra.common.json.JsonNodeMappings.jsonStringToNode(aggregate.getExprProtoSnapshotJson()))")
  @Mapping(
      target = "provenanceConfigSnapshot",
      expression =
          "java(com.patra.common.json.JsonNodeMappings.jsonStringToNode(aggregate.getProvenanceConfigSnapshotJson()))")
  @Mapping(
      target = "sliceParams",
      expression =
          "java(com.patra.common.json.JsonNodeMappings.jsonStringToNode(aggregate.getSliceParamsJson()))")
  @Mapping(target = "statusCode", source = "status", qualifiedByName = "planStatusToCode")
  @Mapping(target = "windowSpec", source = "windowSpec", qualifiedByName = "windowSpecToJson")
  PlanDO toEntity(PlanAggregate aggregate);

  /**
   * Post-mapping hook to populate denormalized timestamp fields for TIME strategy.
   *
   * <p>This method extracts {@code from} and {@code to} timestamps from {@link WindowSpec.Time} and
   * populates the {@code windowFromTs} and {@code windowToTs} fields for query optimization. For
   * non-TIME strategies, these fields are set to {@code null}.
   *
   * @param aggregate source aggregate
   * @param entity target DO (will be mutated)
   */
  @AfterMapping
  default void populateDenormalizedTimestamps(
      PlanAggregate aggregate, @MappingTarget PlanDO entity) {
    WindowSpec windowSpec = aggregate.getWindowSpec();
    if (windowSpec instanceof WindowSpec.Time timeWindow) {
      entity.setWindowFromTs(timeWindow.from());
      entity.setWindowToTs(timeWindow.to());
    } else {
      entity.setWindowFromTs(null);
      entity.setWindowToTs(null);
    }
  }

  default PlanAggregate toAggregate(PlanDO entity) {
    return toPlanAggregate(entity);
  }

  static PlanAggregate toPlanAggregate(PlanDO entity) {
    if (entity == null) {
      return null;
    }
    PlanStatus status = planStatusFromCode(entity.getStatusCode());
    long version = entity.getVersion() == null ? 0L : entity.getVersion();
    WindowSpec windowSpec = jsonToWindowSpec(entity.getWindowSpec());
    return PlanAggregate.restore(
        entity.getId(),
        entity.getScheduleInstanceId(),
        entity.getPlanKey(),
        entity.getProvenanceCode(),
        entity.getOperationCode(),
        entity.getExprProtoHash(),
        JsonNodeMappings.jsonNodeToString(entity.getExprProtoSnapshot()),
        JsonNodeMappings.jsonNodeToString(entity.getProvenanceConfigSnapshot()),
        entity.getProvenanceConfigHash(),
        windowSpec,
        entity.getSliceStrategyCode(),
        JsonNodeMappings.jsonNodeToString(entity.getSliceParams()),
        status,
        version);
  }

  @Named("planStatusToCode")
  static String planStatusToCode(PlanStatus status) {
    return status == null ? null : status.getCode();
  }

  static PlanStatus planStatusFromCode(String code) {
    return code == null ? PlanStatus.DRAFT : PlanStatus.fromCode(code);
  }

  @Named("windowSpecToJson")
  static com.fasterxml.jackson.databind.JsonNode windowSpecToJson(WindowSpec spec) {
    if (spec == null) {
      return null;
    }
    ObjectMapper mapper = JsonMapperHolder.getObjectMapper();
    return mapper.valueToTree(spec.toMap());
  }

  static WindowSpec jsonToWindowSpec(com.fasterxml.jackson.databind.JsonNode json) {
    if (json == null || json.isNull()) {
      return null;
    }
    ObjectMapper mapper = JsonMapperHolder.getObjectMapper();
    @SuppressWarnings("unchecked")
    Map<String, Object> map = mapper.convertValue(json, Map.class);
    return WindowSpec.fromMap(map);
  }
}
