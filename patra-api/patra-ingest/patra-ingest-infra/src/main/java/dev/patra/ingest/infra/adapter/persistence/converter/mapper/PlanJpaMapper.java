package dev.patra.ingest.infra.adapter.persistence.converter.mapper;

import dev.patra.common.ProvenanceCode;
import dev.patra.ingest.domain.exception.InfrastructureException;
import dev.patra.ingest.domain.model.aggregate.PlanAggregate;
import dev.patra.ingest.domain.model.enums.OperationCode;
import dev.patra.ingest.domain.model.enums.PlanStatus;
import dev.patra.ingest.domain.model.vo.plan.PlanId;
import dev.patra.ingest.domain.model.vo.plan.WindowSpec;
import dev.patra.ingest.domain.model.vo.schedule.ScheduleInstanceId;
import dev.patra.ingest.infra.adapter.persistence.entity.PlanEntity;
import dev.patra.tool.jpa.json.JsonColumnCodec;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

/// 采集计划 JPA 实体转换器，负责领域对象与 JPA 实体转换。
///
/// 转换规则：计划聚合 {@link PlanAggregate} ↔ JPA 实体 {@link PlanEntity} 双向转换，
/// 处理 JSON 快照字段、枚举类型代码映射、窗口规格序列化/反序列化。
///
/// @author linqibin
/// @since 0.1.0
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public abstract class PlanJpaMapper {

  /// JSON 列转换器，由容器注入。
  @Autowired protected JsonColumnCodec codec;

  @Mapping(target = "id", source = "id", qualifiedByName = "planIdToLong")
  @Mapping(
      target = "scheduleInstanceId",
      source = "scheduleInstanceId",
      qualifiedByName = "scheduleInstanceIdToLong")
  @Mapping(
      target = "exprProtoSnapshot",
      expression = "java(codec.read(aggregate.getExprProtoSnapshotJson()))")
  @Mapping(
      target = "provenanceConfigSnapshot",
      expression = "java(codec.read(aggregate.getProvenanceConfigSnapshotJson()))")
  @Mapping(target = "sliceParams", expression = "java(codec.read(aggregate.getSliceParamsJson()))")
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
  public abstract PlanEntity toEntity(PlanAggregate aggregate);

  /// 后处理钩子：为 TIME 策略填充反规范化时间戳字段。
  @AfterMapping
  public void populateDenormalizedTimestamps(
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

  public PlanAggregate toAggregate(PlanEntity entity) {
    return toPlanAggregate(entity);
  }

  public PlanAggregate toPlanAggregate(PlanEntity entity) {
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
        codec.write(entity.getExprProtoSnapshot()),
        codec.write(entity.getProvenanceConfigSnapshot()),
        entity.getProvenanceConfigHash(),
        windowSpec,
        entity.getSliceStrategyCode(),
        codec.write(entity.getSliceParams()),
        status,
        version);
  }

  @Named("planIdToLong")
  public static Long planIdToLong(PlanId id) {
    return id != null ? id.value() : null;
  }

  @Named("scheduleInstanceIdToLong")
  public static Long scheduleInstanceIdToLong(ScheduleInstanceId id) {
    return id != null ? id.value() : null;
  }

  @Named("planStatusToCode")
  public static String planStatusToCode(PlanStatus status) {
    return status == null ? null : status.getCode();
  }

  public static PlanStatus planStatusFromCode(String code) {
    return code == null ? PlanStatus.DRAFT : PlanStatus.fromCode(code);
  }

  @Named("windowSpecToJson")
  public JsonNode windowSpecToJson(WindowSpec spec) {
    if (spec == null) {
      return null;
    }
    return codec.fromMap(spec.toMap());
  }

  public WindowSpec jsonToWindowSpec(JsonNode json) {
    if (json == null || json.isNull()) {
      return null;
    }
    return WindowSpec.fromMap(codec.toMap(json));
  }

  // ========== 枚举转换方法 ==========

  public String map(ProvenanceCode code) {
    return code == null ? null : code.getCode();
  }

  public ProvenanceCode mapProvenanceCode(String code) {
    if (code == null || code.isBlank()) {
      return null;
    }
    try {
      return ProvenanceCode.parse(code);
    } catch (IllegalArgumentException e) {
      throw new InfrastructureException("数据库中存在无效的 provenance_code: " + code, e);
    }
  }

  public String map(OperationCode code) {
    return code == null ? null : code.getCode();
  }

  public OperationCode mapOperationCode(String code) {
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
