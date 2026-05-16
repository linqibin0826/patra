package dev.linqibin.patra.registry.domain.model.read.provenance;

import dev.linqibin.patra.registry.domain.exception.DomainValidationException;
import java.time.Instant;

/// 时间窗口与偏移配置查询视图。
///
/// 用于查询时间窗口和偏移配置的读优化投影。定义了窗口模式、大小、回看期、重叠期、水位延迟、偏移类型等时间分片参数。
///
/// @author linqibin
/// @since 0.1.0
public record WindowOffsetQuery(
    Long id,
    Long provenanceId,
    String operationType,
    Instant effectiveFrom,
    Instant effectiveTo,
    String windowModeCode,
    Integer windowSizeValue,
    String windowSizeUnitCode,
    String calendarAlignTo,
    Integer lookbackValue,
    String lookbackUnitCode,
    Integer overlapValue,
    String overlapUnitCode,
    Integer watermarkLagSeconds,
    String offsetTypeCode,
    String offsetFieldKey,
    String offsetDateFormat,
    String windowDateFieldKey,
    Integer maxIdsPerWindow,
    Integer maxWindowSpanSeconds) {
  /// 规范构造器,强制执行时间窗口偏移配置查询视图的验证规则。
  ///
  /// 验证规则：
  ///
  /// - 窗口偏移配置 ID 必须为正数
  ///   - 数据源 ID 必须为正数
  ///   - 窗口模式代码不能为空
  ///   - 窗口大小单位代码不能为空
  ///   - 偏移类型代码不能为空
  ///   - 生效时间不能为 null
  ///   - 字符串字段自动执行 trim 操作
  ///   - 空字符串字段归一化为 null
  ///
  /// @throws DomainValidationException 如果验证失败
  public WindowOffsetQuery {
    if (id == null || id <= 0) {
      throw new DomainValidationException("窗口偏移配置ID必须为正数");
    }
    if (provenanceId == null || provenanceId <= 0) {
      throw new DomainValidationException("来源ID必须为正数");
    }
    if (windowModeCode == null || windowModeCode.isBlank()) {
      throw new DomainValidationException("窗口模式代码不能为空");
    }
    if (windowSizeUnitCode == null || windowSizeUnitCode.isBlank()) {
      throw new DomainValidationException("窗口大小单位代码不能为空");
    }
    if (offsetTypeCode == null || offsetTypeCode.isBlank()) {
      throw new DomainValidationException("偏移类型代码不能为空");
    }
    if (effectiveFrom == null) {
      throw new DomainValidationException("生效时间不能为null");
    }
    operationType = operationType != null ? operationType.trim() : null;
    windowModeCode = windowModeCode.trim();
    windowSizeUnitCode = windowSizeUnitCode.trim();
    calendarAlignTo = calendarAlignTo != null ? calendarAlignTo.trim() : null;
    lookbackUnitCode = lookbackUnitCode != null ? lookbackUnitCode.trim() : null;
    overlapUnitCode = overlapUnitCode != null ? overlapUnitCode.trim() : null;
    offsetTypeCode = offsetTypeCode.trim();
    offsetFieldKey = offsetFieldKey != null ? offsetFieldKey.trim() : null;
    if (offsetFieldKey != null && offsetFieldKey.isEmpty()) {
      offsetFieldKey = null;
    }
    offsetDateFormat = offsetDateFormat != null ? offsetDateFormat.trim() : null;
    windowDateFieldKey = windowDateFieldKey != null ? windowDateFieldKey.trim() : null;
    if (windowDateFieldKey != null && windowDateFieldKey.isEmpty()) {
      windowDateFieldKey = null;
    }
  }
}
