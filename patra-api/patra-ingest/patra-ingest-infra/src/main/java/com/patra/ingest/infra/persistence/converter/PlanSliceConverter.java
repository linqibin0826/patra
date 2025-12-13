package com.patra.ingest.infra.persistence.converter;

import com.patra.common.enums.ProvenanceCode;
import com.patra.common.json.JsonNodeMappings;
import com.patra.ingest.domain.exception.InfrastructureException;
import com.patra.ingest.domain.model.aggregate.PlanSliceAggregate;
import com.patra.ingest.domain.model.enums.OperationCode;
import com.patra.ingest.domain.model.enums.SliceStatus;
import com.patra.ingest.domain.model.vo.plan.PlanId;
import com.patra.ingest.domain.model.vo.slice.PlanSliceId;
import com.patra.ingest.infra.persistence.entity.PlanSliceDO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;

/// 计划切片聚合 ↔ 数据对象转换器。
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface PlanSliceConverter {

  @Mapping(target = "id", source = "id", qualifiedByName = "planSliceIdToLong")
  @Mapping(target = "planId", source = "planId", qualifiedByName = "planIdToLong")
  @Mapping(
      target = "windowSpec",
      expression =
          "java(com.patra.common.json.JsonNodeMappings.jsonStringToNode(aggregate.getWindowSpecJson()))")
  @Mapping(
      target = "exprSnapshot",
      expression =
          "java(com.patra.common.json.JsonNodeMappings.jsonStringToNode(aggregate.getExprSnapshotJson()))")
  @Mapping(target = "statusCode", source = "status", qualifiedByName = "sliceStatusToCode")
  PlanSliceDO toEntity(PlanSliceAggregate aggregate);

  default PlanSliceAggregate toAggregate(PlanSliceDO entity) {
    return toPlanSliceAggregate(entity);
  }

  static PlanSliceAggregate toPlanSliceAggregate(PlanSliceDO entity) {
    if (entity == null) {
      return null;
    }
    SliceStatus status = sliceStatusFromCode(entity.getStatusCode());
    long version = entity.getVersion() == null ? 0L : entity.getVersion();
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
        version);
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
