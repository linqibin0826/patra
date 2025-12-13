package com.patra.ingest.infra.persistence.converter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.patra.common.enums.ProvenanceCode;
import com.patra.common.json.JsonMapperHolder;
import com.patra.common.json.JsonNodeMappings;
import com.patra.ingest.domain.exception.InfrastructureException;
import com.patra.ingest.domain.model.aggregate.PlanAggregate;
import com.patra.ingest.domain.model.enums.OperationCode;
import com.patra.ingest.domain.model.enums.PlanStatus;
import com.patra.ingest.domain.model.vo.plan.PlanId;
import com.patra.ingest.domain.model.vo.plan.WindowSpec;
import com.patra.ingest.domain.model.vo.schedule.ScheduleInstanceId;
import com.patra.ingest.infra.persistence.entity.PlanDO;
import java.util.Map;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;

/// 采集计划对象转换器,负责领域对象与数据库实体转换。
///
/// 转换规则: 计划聚合 {@link PlanAggregate} ↔ 数据对象 {@link PlanDO} 双向转换,处理 JSON
/// 快照字段、枚举类型代码映射、窗口规格序列化/反序列化。
///
/// @author linqibin
/// @since 0.1.0
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface PlanConverter {

  @Mapping(target = "id", source = "id", qualifiedByName = "planIdToLong")
  @Mapping(
      target = "scheduleInstanceId",
      source = "scheduleInstanceId",
      qualifiedByName = "scheduleInstanceIdToLong")
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

  /// Post-mapping hook to populate denormalized timestamp fields for TIME strategy.
  ///
  /// This method extracts `from` and `to` timestamps from {@link WindowSpec.Time} and
  /// populates the `windowFromTs` and `windowToTs` fields for query optimization. For
  /// non-TIME strategies, these fields are set to `null`.
  ///
  /// @param aggregate source aggregate
  /// @param entity target DO (will be mutated)
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
        PlanId.of(entity.getId()),
        ScheduleInstanceId.of(entity.getScheduleInstanceId()),
        entity.getPlanKey(),
        ProvenanceCode.parse(entity.getProvenanceCode()),
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

  @Named("planIdToLong")
  static Long planIdToLong(PlanId id) {
    return id != null ? id.value() : null;
  }

  @Named("scheduleInstanceIdToLong")
  static Long scheduleInstanceIdToLong(ScheduleInstanceId id) {
    return id != null ? id.value() : null;
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

  // ========== 枚举转换方法 ==========

  /// ProvenanceCode 枚举 → String（用于 Domain → DO）
  default String map(ProvenanceCode code) {
    return code == null ? null : code.getCode();
  }

  /// String → ProvenanceCode 枚举（用于 DO → Domain）
  default ProvenanceCode mapProvenanceCode(String code) {
    if (code == null || code.isBlank()) {
      return null;
    }
    try {
      return ProvenanceCode.parse(code);
    } catch (IllegalArgumentException e) {
      throw new InfrastructureException("数据库中存在无效的 provenance_code: " + code, e);
    }
  }

  /// OperationCode 枚举 → String
  default String map(OperationCode code) {
    return code == null ? null : code.getCode();
  }

  /// String → OperationCode 枚举
  default OperationCode mapOperationCode(String code) {
    if (code == null || code.isBlank()) {
      return null;
    }
    try {
      return OperationCode.fromCode(code);
    } catch (IllegalArgumentException e) {
      throw new InfrastructureException("数据库中存在无效的 operation_code: " + code, e);
    }
  }
}
