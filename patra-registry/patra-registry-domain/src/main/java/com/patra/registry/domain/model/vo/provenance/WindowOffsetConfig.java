package com.patra.registry.domain.model.vo.provenance;

import com.patra.registry.domain.exception.DomainValidationException;
import java.time.Instant;

/// 时间窗口偏移配置值对象,定义任务如何分段时间窗口和推进增量偏移量。
///
/// **不可变性**:此对象一旦创建不可修改,通过值语义比较相等性。
///
/// **业务约束**:
///
/// - 配置ID和数据源ID必须为正整数
///   - 生效时间(effectiveFrom)不可为空,失效时间(effectiveTo)为null表示永久有效
///   - 窗口模式(windowModeCode)、窗口大小单位(windowSizeUnitCode)、偏移量类型(offsetTypeCode)不可为空白
///   - 对于DATE或COMPOSITE类型的偏移量,必须至少提供offsetFieldKey或windowDateFieldKey之一
///   - 操作类型(operationType)为null时表示适用于所有操作(HARVEST/UPDATE/BACKFILL)
///
/// **业务语义**:
///
/// - 支持SLIDING(滑动)和CALENDAR(日历对齐)两种窗口模式
///   - 支持DATE(日期)、ID(标识符)、COMPOSITE(复合)三种偏移量跟踪机制
///   - lookback(回溯)用于补偿延迟到达的数据
///   - overlap(重叠)确保相邻窗口之间的数据不丢失
///   - watermark(水位线)定义容忍乱序数据的最大延迟
///
/// @param id 配置主键,唯一标识此窗口偏移配置,必须为正整数
/// @param provenanceId 数据源ID外键,引用`reg_provenance.id`,必须为正整数
/// @param operationType 操作类型,取值为`HARVEST/UPDATE/BACKFILL`,null表示适用于所有操作
/// @param effectiveFrom 配置生效时间(包含),标记此配置开始生效的时刻,不可为null
/// @param effectiveTo 配置失效时间(不包含),null表示永久有效
/// @param windowModeCode 窗口模式代码(字典值),定义窗口策略(`SLIDING/CALENDAR`),不可为空白
/// @param windowSizeValue 窗口长度数值,必须为正整数
/// @param windowSizeUnitCode 窗口长度单位(字典值),如`SECOND/MINUTE/HOUR/DAY`,不可为空白
/// @param calendarAlignTo 日历对齐粒度,用于CALENDAR模式,如`HOUR/DAY/WEEK/MONTH`,SLIDING模式下为null
/// @param lookbackValue 回溯长度数值,用于补偿延迟数据,可为null
/// @param lookbackUnitCode 回溯长度单位(字典值),可为null
/// @param overlapValue 重叠长度数值,用于相邻窗口之间的重叠,可为null
/// @param overlapUnitCode 重叠长度单位(字典值),可为null
/// @param watermarkLagSeconds 水位线延迟秒数,定义容忍乱序数据的最大延迟,可为null
/// @param offsetTypeCode 偏移量类型代码(字典值),定义跟踪机制(`DATE/ID/COMPOSITE`),不可为空白
/// @param offsetFieldKey 统一字段键(std_key),用作偏移量基准点,可为null
/// @param offsetDateFormat 日期偏移量格式,如`ISO_INSTANT/epochMillis/YYYYMMDD`,可为null
/// @param windowDateFieldKey 统一日期字段键(std_key),用于DATE/COMPOSITE模式的时间切片,可为null
/// @param maxIdsPerWindow 单窗口最大ID数量,超出后分割窗口,可为null
/// @param maxWindowSpanSeconds 单窗口最大时间跨度(秒),过长窗口将被分割,可为null
/// @author linqibin
/// @since 0.1.0
public record WindowOffsetConfig(
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
  /// 规范构造器,强制执行时间窗口偏移配置的业务约束。
  ///
  /// 验证规则:
  ///
  /// - 配置ID和数据源ID必须为正整数
  ///   - 生效时间不可为空
  ///   - 窗口模式、窗口大小单位、偏移量类型不可为空白
  ///   - DATE/COMPOSITE偏移量类型必须至少提供offsetFieldKey或windowDateFieldKey之一
  ///   - 所有字符串字段自动trim去除首尾空白
  ///
  /// @throws DomainValidationException 如果验证失败
  public WindowOffsetConfig(
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
    validateRequiredFields(id, provenanceId, effectiveFrom);
    String modeTrimmed = DomainValidationException.notBlank(windowModeCode, "Window mode code");
    String sizeUnitTrimmed =
        DomainValidationException.notBlank(windowSizeUnitCode, "Window size unit code");
    String offsetTypeTrimmed =
        DomainValidationException.notBlank(offsetTypeCode, "Offset type code");

    this.id = id;
    this.provenanceId = provenanceId;
    this.operationType = DomainValidationException.trimOrNull(operationType);
    this.effectiveFrom = effectiveFrom;
    this.effectiveTo = effectiveTo;
    this.windowModeCode = modeTrimmed;
    this.windowSizeValue = windowSizeValue;
    this.windowSizeUnitCode = sizeUnitTrimmed;
    this.calendarAlignTo = DomainValidationException.trimOrNull(calendarAlignTo);
    this.lookbackValue = lookbackValue;
    this.lookbackUnitCode = DomainValidationException.trimOrNull(lookbackUnitCode);
    this.overlapValue = overlapValue;
    this.overlapUnitCode = DomainValidationException.trimOrNull(overlapUnitCode);
    this.watermarkLagSeconds = watermarkLagSeconds;

    String offsetFieldKeyNormalized = normalizeToNullIfEmpty(offsetFieldKey);
    String windowDateFieldKeyNormalized = normalizeToNullIfEmpty(windowDateFieldKey);
    validateDateOffsetKeys(
        offsetTypeTrimmed, offsetFieldKeyNormalized, windowDateFieldKeyNormalized);

    this.offsetTypeCode = offsetTypeTrimmed;
    this.offsetFieldKey = offsetFieldKeyNormalized;
    this.offsetDateFormat = DomainValidationException.trimOrNull(offsetDateFormat);
    this.windowDateFieldKey = windowDateFieldKeyNormalized;
    this.maxIdsPerWindow = maxIdsPerWindow;
    this.maxWindowSpanSeconds = maxWindowSpanSeconds;
  }

  /// 验证配置的必需字段。
  ///
  /// @param id 配置ID
  /// @param provenanceId 数据源ID
  /// @param effectiveFrom 生效时间
  /// @throws DomainValidationException 如果验证失败
  private static void validateRequiredFields(Long id, Long provenanceId, Instant effectiveFrom) {
    DomainValidationException.positive(id, "Window offset config id");
    DomainValidationException.positive(provenanceId, "Provenance id");
    DomainValidationException.nonNull(effectiveFrom, "Effective from");
  }

  /// 验证DATE/COMPOSITE偏移量类型必须提供所需的字段键。
  ///
  /// 业务规则:对于基于日期的偏移量跟踪,必须至少指定offsetFieldKey或windowDateFieldKey之一。
  ///
  /// @param offsetTypeCode 偏移量类型代码
  /// @param offsetFieldKey 标准化后的偏移量字段键
  /// @param windowDateFieldKey 标准化后的窗口日期字段键
  /// @throws DomainValidationException 如果DATE/COMPOSITE类型未提供必需字段键
  private static void validateDateOffsetKeys(
      String offsetTypeCode, String offsetFieldKey, String windowDateFieldKey) {
    boolean requiresDateKey =
        "DATE".equalsIgnoreCase(offsetTypeCode) || "COMPOSITE".equalsIgnoreCase(offsetTypeCode);
    if (requiresDateKey && offsetFieldKey == null && windowDateFieldKey == null) {
      throw new DomainValidationException(
          "DATE/COMPOSITE offset requires at least one std_key (offset or window date)");
    }
  }

  /// 标准化字符串为null(如果trim后为空)。
  ///
  /// @param value 待标准化的字符串
  /// @return trim后的字符串,如果为空则返回null
  private static String normalizeToNullIfEmpty(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }
}
