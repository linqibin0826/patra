package com.patra.ingest.infra.adapter.persistence.converter.mapper;

import com.patra.common.enums.ProvenanceCode;
import com.patra.ingest.domain.exception.InfrastructureException;
import com.patra.ingest.domain.model.aggregate.PlanSliceAggregate;
import com.patra.ingest.domain.model.enums.OperationCode;
import com.patra.ingest.domain.model.enums.SliceStatus;
import com.patra.ingest.domain.model.vo.plan.PlanId;
import com.patra.ingest.domain.model.vo.slice.PlanSliceId;
import com.patra.ingest.infra.adapter.persistence.entity.PlanSliceEntity;
import dev.linqibin.commons.json.JsonNodeMappings;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;

/// 计划切片 JPA 实体转换器，负责领域对象与 JPA 实体转换。
///
/// @author linqibin
/// @since 0.1.0
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface PlanSliceJpaMapper {

  @Mapping(target = "id", source = "id", qualifiedByName = "planSliceIdToLong")
  @Mapping(target = "planId", source = "planId", qualifiedByName = "planIdToLong")
  @Mapping(
      target = "windowSpec",
      expression =
          "java(dev.linqibin.commons.json.JsonNodeMappings.jsonStringToNode(aggregate.getWindowSpecJson()))")
  @Mapping(
      target = "exprSnapshot",
      expression =
          "java(dev.linqibin.commons.json.JsonNodeMappings.jsonStringToNode(aggregate.getExprSnapshotJson()))")
  @Mapping(target = "statusCode", source = "status", qualifiedByName = "sliceStatusToCode")
  PlanSliceEntity toEntity(PlanSliceAggregate aggregate);

  default PlanSliceAggregate toAggregate(PlanSliceEntity entity) {
    return toPlanSliceAggregate(entity);
  }

  static PlanSliceAggregate toPlanSliceAggregate(PlanSliceEntity entity) {
    if (entity == null) {
      return null;
    }
    SliceStatus status = sliceStatusFromCode(entity.getStatusCode());
    // ValueObjectJpaEntity 使用 DELETE/INSERT 模式，无需乐观锁
    PlanId planId = entity.getPlanId() != null ? PlanId.of(entity.getPlanId()) : null;
    return PlanSliceAggregate.restore(
        PlanSliceId.of(entity.getId()),
        planId,
        ProvenanceCode.parse(entity.getProvenanceCode()),
        entity.getSliceNo() == null ? 0 : entity.getSliceNo(),
        entity.getSliceSignatureHash(),
        JsonNodeMappings.jsonNodeToString(entity.getWindowSpec()),
        entity.getExprHash(),
        JsonNodeMappings.jsonNodeToString(entity.getExprSnapshot()),
        status,
        0L);
  }

  @Named("planSliceIdToLong")
  static Long planSliceIdToLong(PlanSliceId id) {
    return id != null ? id.value() : null;
  }

  @Named("planIdToLong")
  static Long planIdToLong(PlanId id) {
    return id != null ? id.value() : null;
  }

  @Named("sliceStatusToCode")
  static String sliceStatusToCode(SliceStatus status) {
    return status == null ? null : status.getCode();
  }

  static SliceStatus sliceStatusFromCode(String code) {
    return code == null ? SliceStatus.PENDING : SliceStatus.fromCode(code);
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
