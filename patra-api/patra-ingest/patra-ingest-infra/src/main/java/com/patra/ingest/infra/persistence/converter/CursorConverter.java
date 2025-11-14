package com.patra.ingest.infra.persistence.converter;

import com.patra.common.enums.ProvenanceCode;
import com.patra.ingest.domain.exception.InfrastructureException;
import com.patra.ingest.domain.model.entity.Cursor;
import com.patra.ingest.domain.model.enums.CursorType;
import com.patra.ingest.domain.model.enums.NamespaceScope;
import com.patra.ingest.domain.model.enums.OperationCode;
import com.patra.ingest.domain.model.vo.cursor.CursorLineage;
import com.patra.ingest.domain.model.vo.cursor.CursorValue;
import com.patra.ingest.domain.model.vo.cursor.CursorWatermark;
import com.patra.ingest.infra.persistence.entity.CursorDO;
import java.math.BigDecimal;
import java.time.Instant;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;

/** Cursor（增量同步游标）聚合 ↔ DO 转换器。 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface CursorConverter {

  @Mapping(target = "cursorTypeCode", source = "cursorType", qualifiedByName = "cursorTypeToCode")
  @Mapping(
      target = "namespaceScopeCode",
      source = "namespaceScope",
      qualifiedByName = "namespaceScopeToCode")
  @Mapping(target = "cursorValue", source = "value.raw")
  @Mapping(
      target = "normalizedInstant",
      expression = "java(CursorConverter.normalizedInstant(source))")
  @Mapping(
      target = "normalizedNumeric",
      expression = "java(CursorConverter.normalizedNumeric(source))")
  @Mapping(
      target = "observedMaxValue",
      expression = "java(CursorConverter.observedMaxValue(source))")
  @Mapping(target = "scheduleInstanceId", source = "lineage.scheduleInstanceId")
  @Mapping(target = "planId", source = "lineage.planId")
  @Mapping(target = "sliceId", source = "lineage.sliceId")
  @Mapping(target = "taskId", source = "lineage.taskId")
  @Mapping(target = "lastRunId", source = "lineage.runId")
  @Mapping(target = "lastBatchId", source = "lineage.batchId")
  CursorDO toDO(Cursor source);

  default Cursor toDomain(CursorDO entity) {
    return toCursor(entity);
  }

  static Cursor toCursor(CursorDO entity) {
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

  /** ProvenanceCode 枚举 → String（用于 Domain → DO） */
  default String map(ProvenanceCode code) {
    return code == null ? null : code.getCode();
  }

  /** String → ProvenanceCode 枚举（用于 DO → Domain） */
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

  /** OperationCode 枚举 → String */
  default String map(OperationCode code) {
    return code == null ? null : code.getCode();
  }

  /** String → OperationCode 枚举 */
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
