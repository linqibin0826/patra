package dev.patra.ingest.infra.adapter.persistence.converter.mapper;

import dev.patra.common.ProvenanceCode;
import dev.patra.ingest.domain.exception.InfrastructureException;
import dev.patra.ingest.domain.model.aggregate.PlanSliceAggregate;
import dev.patra.ingest.domain.model.enums.OperationCode;
import dev.patra.ingest.domain.model.enums.SliceStatus;
import dev.patra.ingest.domain.model.vo.plan.PlanId;
import dev.patra.ingest.domain.model.vo.slice.PlanSliceId;
import dev.patra.ingest.infra.adapter.persistence.entity.PlanSliceEntity;
import dev.patra.tool.jpa.json.JsonColumnCodec;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;
import org.springframework.beans.factory.annotation.Autowired;

/// 计划切片 JPA 实体转换器，负责领域对象与 JPA 实体转换。
///
/// @author linqibin
/// @since 0.1.0
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public abstract class PlanSliceJpaMapper {

  /// JSON 列转换器，由容器注入。
  @Autowired protected JsonColumnCodec codec;

  @Mapping(target = "id", source = "id", qualifiedByName = "planSliceIdToLong")
  @Mapping(target = "planId", source = "planId", qualifiedByName = "planIdToLong")
  @Mapping(target = "windowSpec", expression = "java(codec.read(aggregate.getWindowSpecJson()))")
  @Mapping(
      target = "exprSnapshot",
      expression = "java(codec.read(aggregate.getExprSnapshotJson()))")
  @Mapping(target = "statusCode", source = "status", qualifiedByName = "sliceStatusToCode")
  public abstract PlanSliceEntity toEntity(PlanSliceAggregate aggregate);

  public PlanSliceAggregate toAggregate(PlanSliceEntity entity) {
    return toPlanSliceAggregate(entity);
  }

  public PlanSliceAggregate toPlanSliceAggregate(PlanSliceEntity entity) {
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
        codec.write(entity.getWindowSpec()),
        entity.getExprHash(),
        codec.write(entity.getExprSnapshot()),
        status,
        0L);
  }

  @Named("planSliceIdToLong")
  public static Long planSliceIdToLong(PlanSliceId id) {
    return id != null ? id.value() : null;
  }

  @Named("planIdToLong")
  public static Long planIdToLong(PlanId id) {
    return id != null ? id.value() : null;
  }

  @Named("sliceStatusToCode")
  public static String sliceStatusToCode(SliceStatus status) {
    return status == null ? null : status.getCode();
  }

  public static SliceStatus sliceStatusFromCode(String code) {
    return code == null ? SliceStatus.PENDING : SliceStatus.fromCode(code);
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
