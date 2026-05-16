package dev.linqibin.patra.ingest.infra.adapter.persistence.converter.mapper;

import dev.linqibin.commons.json.JsonMapperHolder;
import dev.linqibin.commons.json.JsonNodeMappings;
import dev.linqibin.patra.common.enums.ProvenanceCode;
import dev.linqibin.patra.ingest.domain.exception.InfrastructureException;
import dev.linqibin.patra.ingest.domain.model.aggregate.PlanAggregate;
import dev.linqibin.patra.ingest.domain.model.enums.OperationCode;
import dev.linqibin.patra.ingest.domain.model.enums.PlanStatus;
import dev.linqibin.patra.ingest.domain.model.vo.plan.PlanId;
import dev.linqibin.patra.ingest.domain.model.vo.plan.WindowSpec;
import dev.linqibin.patra.ingest.domain.model.vo.schedule.ScheduleInstanceId;
import dev.linqibin.patra.ingest.infra.adapter.persistence.entity.PlanEntity;
import java.util.Map;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;
import tools.jackson.databind.ObjectMapper;

/// 采集计划 JPA 实体转换器，负责领域对象与 JPA 实体转换。
///
/// 转换规则：计划聚合 {@link PlanAggregate} ↔ JPA 实体 {@link PlanEntity} 双向转换，
/// 处理 JSON 快照字段、枚举类型代码映射、窗口规格序列化/反序列化。
///
/// @author linqibin
/// @since 0.1.0
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface PlanJpaMapper {

  @Mapping(target = "id", source = "id", qualifiedByName = "planIdToLong")
  @Mapping(
      target = "scheduleInstanceId",
      source = "scheduleInstanceId",
      qualifiedByName = "scheduleInstanceIdToLong")
  @Mapping(
      target = "exprProtoSnapshot",
      expression =
          "java(dev.linqibin.commons.json.JsonNodeMappings.jsonStringToNode(aggregate.getExprProtoSnapshotJson()))")
  @Mapping(
      target = "provenanceConfigSnapshot",
      expression =
          "java(dev.linqibin.commons.json.JsonNodeMappings.jsonStringToNode(aggregate.getProvenanceConfigSnapshotJson()))")
  @Mapping(
      target = "sliceParams",
      expression =
          "java(dev.linqibin.commons.json.JsonNodeMappings.jsonStringToNode(aggregate.getSliceParamsJson()))")
  @Mapping(target = "statusCode", source = "status", qualifiedByName = "planStatusToCode")
  @Mapping(target = "windowSpec", source = "windowSpec", qualifiedByName = "windowSpecToJson")
  // 审计字段由 JPA 管理
  @Mapping(target = "version", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "createdBy", ignore = true)
  @Mapping(target = "createdByName", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  @Mapping(target = "updatedBy", ignore = true)
  @Mapping(target = "updatedByName", ignore = true)
  @Mapping(target = "ipAddress", ignore = true)
  @Mapping(target = "recordRemarks", ignore = true)
  PlanEntity toEntity(PlanAggregate aggregate);

  /// 后处理钩子：为 TIME 策略填充反规范化时间戳字段。
  @AfterMapping
  default void populateDenormalizedTimestamps(
      PlanAggregate aggregate, @MappingTarget PlanEntity entity) {
    WindowSpec windowSpec = aggregate.getWindowSpec();
    if (windowSpec instanceof WindowSpec.Time timeWindow) {
      entity.setWindowFromTs(timeWindow.from());
      entity.setWindowToTs(timeWindow.to());
    } else {
      entity.setWindowFromTs(null);
      entity.setWindowToTs(null);
    }
  }

  default PlanAggregate toAggregate(PlanEntity entity) {
    return toPlanAggregate(entity);
  }

  static PlanAggregate toPlanAggregate(PlanEntity entity) {
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
  static tools.jackson.databind.JsonNode windowSpecToJson(WindowSpec spec) {
    if (spec == null) {
      return null;
    }
    ObjectMapper mapper = JsonMapperHolder.getObjectMapper();
    return mapper.valueToTree(spec.toMap());
  }

  static WindowSpec jsonToWindowSpec(tools.jackson.databind.JsonNode json) {
    if (json == null || json.isNull()) {
      return null;
    }
    ObjectMapper mapper = JsonMapperHolder.getObjectMapper();
    @SuppressWarnings("unchecked")
    Map<String, Object> map = mapper.convertValue(json, Map.class);
    return WindowSpec.fromMap(map);
  }

  // ========== 枚举转换方法 ==========

  default String map(ProvenanceCode code) {
    return code == null ? null : code.getCode();
  }

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

  default String map(OperationCode code) {
    return code == null ? null : code.getCode();
  }

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
