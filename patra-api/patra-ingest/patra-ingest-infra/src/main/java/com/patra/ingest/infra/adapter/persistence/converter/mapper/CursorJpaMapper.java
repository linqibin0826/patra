package com.patra.ingest.infra.adapter.persistence.converter.mapper;

import com.patra.ingest.domain.exception.InfrastructureException;
import com.patra.ingest.domain.model.entity.Cursor;
import com.patra.ingest.domain.model.enums.CursorType;
import com.patra.ingest.domain.model.enums.NamespaceScope;
import com.patra.ingest.domain.model.enums.OperationCode;
import com.patra.ingest.domain.model.vo.cursor.CursorLineage;
import com.patra.ingest.domain.model.vo.cursor.CursorValue;
import com.patra.ingest.domain.model.vo.cursor.CursorWatermark;
import com.patra.ingest.infra.adapter.persistence.entity.CursorEntity;
import dev.linqibin.patra.common.enums.ProvenanceCode;
import java.math.BigDecimal;
import java.time.Instant;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;

/// 游标 JPA 实体转换器，负责领域对象与 JPA 实体转换。
///
/// @author linqibin
/// @since 0.1.0
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface CursorJpaMapper {

  @Mapping(target = "cursorTypeCode", source = "cursorType", qualifiedByName = "cursorTypeToCode")
  @Mapping(
      target = "namespaceScopeCode",
      source = "namespaceScope",
      qualifiedByName = "namespaceScopeToCode")
  @Mapping(target = "cursorValue", source = "value.raw")
  @Mapping(
      target = "normalizedInstant",
      expression = "java(CursorJpaMapper.normalizedInstant(source))")
  @Mapping(
      target = "normalizedNumeric",
      expression = "java(CursorJpaMapper.normalizedNumeric(source))")
  @Mapping(
      target = "observedMaxValue",
      expression = "java(CursorJpaMapper.observedMaxValue(source))")
  @Mapping(target = "scheduleInstanceId", source = "lineage.scheduleInstanceId")
  @Mapping(target = "planId", source = "lineage.planId")
  @Mapping(target = "sliceId", source = "lineage.sliceId")
  @Mapping(target = "taskId", source = "lineage.taskId")
  @Mapping(target = "lastRunId", source = "lineage.runId")
  @Mapping(target = "lastBatchId", source = "lineage.batchId")
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
  CursorEntity toEntity(Cursor source);

  default Cursor toAggregate(CursorEntity entity) {
    return toCursor(entity);
  }

  static Cursor toCursor(CursorEntity entity) {
    if (entity == null) {
      return null;
    }
    CursorType type = cursorTypeFromCode(entity.getCursorTypeCode());
    NamespaceScope scope = namespaceScopeFromCode(entity.getNamespaceScopeCode());
    CursorValue value =
        new CursorValue(
            type,
            entity.getCursorValue(),
            entity.getNormalizedInstant(),
            entity.getNormalizedNumeric());
    CursorWatermark watermark =
        new CursorWatermark(
            entity.getObservedMaxValue(),
            entity.getNormalizedInstant(),
            entity.getNormalizedNumeric());
    CursorLineage lineage =
        new CursorLineage(
            entity.getScheduleInstanceId(),
            entity.getPlanId(),
            entity.getSliceId(),
            entity.getTaskId(),
            entity.getLastRunId(),
            entity.getLastBatchId());
    return Cursor.restore(
        entity.getId(),
        ProvenanceCode.parse(entity.getProvenanceCode()),
        entity.getOperationCode(),
        entity.getCursorKey(),
        scope,
        entity.getNamespaceKey(),
        type,
        value,
        watermark,
        lineage,
        entity.getExprHash());
  }

  @Named("cursorTypeToCode")
  static String cursorTypeToCode(CursorType type) {
    return type == null ? null : type.getCode();
  }

  static CursorType cursorTypeFromCode(String code) {
    return code == null ? null : CursorType.fromCode(code);
  }

  @Named("namespaceScopeToCode")
  static String namespaceScopeToCode(NamespaceScope scope) {
    return scope == null ? null : scope.getCode();
  }

  static NamespaceScope namespaceScopeFromCode(String code) {
    return code == null ? null : NamespaceScope.fromCode(code);
  }

  static Instant normalizedInstant(Cursor source) {
    if (source == null) {
      return null;
    }
    CursorWatermark watermark = source.getWatermark();
    if (watermark != null && watermark.hasInstant()) {
      return watermark.normalizedInstant();
    }
    CursorValue value = source.getValue();
    return value == null ? null : value.instant();
  }

  static BigDecimal normalizedNumeric(Cursor source) {
    if (source == null) {
      return null;
    }
    CursorWatermark watermark = source.getWatermark();
    if (watermark != null && watermark.hasNumeric()) {
      return watermark.normalizedNumeric();
    }
    CursorValue value = source.getValue();
    return value == null ? null : value.numeric();
  }

  static String observedMaxValue(Cursor source) {
    if (source == null || source.getWatermark() == null) {
      return null;
    }
    return source.getWatermark().observedMaxValue();
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
