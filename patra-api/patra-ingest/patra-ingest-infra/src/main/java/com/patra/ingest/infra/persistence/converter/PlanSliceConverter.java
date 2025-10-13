package com.patra.ingest.infra.persistence.converter;

import com.patra.common.json.JsonNodeMappings;
import com.patra.ingest.domain.model.aggregate.PlanSliceAggregate;
import com.patra.ingest.domain.model.enums.SliceStatus;
import com.patra.ingest.infra.persistence.entity.PlanSliceDO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;

/** 计划切片聚合 ↔ 数据对象转换器。 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface PlanSliceConverter {

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
    return PlanSliceAggregate.restore(
        entity.getId(),
        entity.getPlanId(),
        entity.getProvenanceCode(),
        entity.getSliceNo() == null ? 0 : entity.getSliceNo(),
        entity.getSliceSignatureHash(),
        JsonNodeMappings.jsonNodeToString(entity.getWindowSpec()),
        entity.getExprHash(),
        JsonNodeMappings.jsonNodeToString(entity.getExprSnapshot()),
        status,
        version);
  }

  @Named("sliceStatusToCode")
  static String sliceStatusToCode(SliceStatus status) {
    return status == null ? null : status.getCode();
  }

  static SliceStatus sliceStatusFromCode(String code) {
    return code == null ? SliceStatus.PENDING : SliceStatus.fromCode(code);
  }
}
