package com.patra.catalog.domain.model.vo.venue;

import cn.hutool.core.util.StrUtil;
import com.patra.catalog.domain.model.enums.DataSourceCode;
import java.time.Instant;
import java.time.LocalDate;

/// 载体数据源值对象。
///
/// 存储各数据源的原始数据和提取字段，用于数据溯源和审计。
///
/// **设计说明**：
///
/// - 值对象：身份由 `(venueId, sourceCode)` 决定，无独立生命周期
/// - 不可变：所有字段在创建时确定，之后不可修改
/// - 支持数据溯源和审计
/// - 每个 Venue 可关联多个数据源记录
/// - 同一 Venue + 数据源代码唯一（uk_venue_source）
///
/// **业务规则**：
///
/// - venueId 和 sourceCode 必填
/// - sourceId 可选（不同数据源的 ID 格式不同）
/// - rawData 和 extractedData 为 JSON 字符串
/// - fetchedAt 记录数据获取时间
///
/// **数据来源**：
///
/// - OPENALEX：OpenAlex Source 对象
/// - PUBMED：NLM Catalog 记录
/// - DOAJ：DOAJ Journal 对象
/// - CROSSREF：Crossref Member 对象
/// - JCR：Journal Citation Reports 数据
///
/// @param venueId 关联的 Venue ID（必填）
/// @param sourceCode 数据源代码（必填）
/// @param sourceId 数据源系统中的 ID（如 OpenAlex 的 S1234567890）
/// @param rawData 原始 JSON 数据（完整保存用于审计）
/// @param extractedData 提取的关键字段（JSON 格式）
/// @param sourceCreatedAt 源数据创建时间（来自数据源）
/// @param sourceUpdatedAt 源数据更新时间（来自数据源）
/// @param fetchedAt 数据获取时间（本系统获取的时间）
/// @author linqibin
/// @since 0.1.0
public record VenueSourceData(
    Long venueId,
    DataSourceCode sourceCode,
    String sourceId,
    String rawData,
    String extractedData,
    LocalDate sourceCreatedAt,
    LocalDate sourceUpdatedAt,
    Instant fetchedAt) {

  /// 紧凑构造函数，用于参数验证。
  public VenueSourceData {
    if (venueId == null) {
      throw new IllegalArgumentException("Venue ID 不能为空");
    }
    if (sourceCode == null) {
      throw new IllegalArgumentException("数据源代码不能为空");
    }
  }

  // ========== 工厂方法 ==========

  /// 创建数据源记录。
  ///
  /// @param venueId 关联的 Venue ID
  /// @param sourceCode 数据源代码
  /// @param sourceId 数据源系统中的 ID（可选）
  /// @param rawData 原始 JSON 数据
  /// @param extractedData 提取的关键字段
  /// @return 数据源值对象
  public static VenueSourceData create(
      Long venueId,
      DataSourceCode sourceCode,
      String sourceId,
      String rawData,
      String extractedData) {
    return new VenueSourceData(
        venueId, sourceCode, sourceId, rawData, extractedData, null, null, Instant.now());
  }

  /// 创建数据源记录（仅必填字段）。
  ///
  /// @param venueId 关联的 Venue ID
  /// @param sourceCode 数据源代码
  /// @return 数据源值对象
  public static VenueSourceData create(Long venueId, DataSourceCode sourceCode) {
    return new VenueSourceData(venueId, sourceCode, null, null, null, null, null, Instant.now());
  }

  /// 创建完整的数据源记录。
  ///
  /// @param venueId 关联的 Venue ID
  /// @param sourceCode 数据源代码
  /// @param sourceId 数据源 ID
  /// @param rawData 原始 JSON 数据
  /// @param extractedData 提取的字段 JSON
  /// @param sourceCreatedAt 源数据创建时间
  /// @param sourceUpdatedAt 源数据更新时间
  /// @param fetchedAt 数据获取时间
  /// @return 数据源值对象
  public static VenueSourceData of(
      Long venueId,
      DataSourceCode sourceCode,
      String sourceId,
      String rawData,
      String extractedData,
      LocalDate sourceCreatedAt,
      LocalDate sourceUpdatedAt,
      Instant fetchedAt) {
    return new VenueSourceData(
        venueId,
        sourceCode,
        sourceId,
        rawData,
        extractedData,
        sourceCreatedAt,
        sourceUpdatedAt,
        fetchedAt != null ? fetchedAt : Instant.now());
  }

  // ========== 查询方法 ==========

  /// 判断是否有原始数据。
  ///
  /// @return true 如果有原始数据
  public boolean hasRawData() {
    return StrUtil.isNotBlank(rawData);
  }

  /// 判断是否有提取数据。
  ///
  /// @return true 如果有提取数据
  public boolean hasExtractedData() {
    return StrUtil.isNotBlank(extractedData);
  }

  /// 判断是否有数据源 ID。
  ///
  /// @return true 如果有数据源 ID
  public boolean hasSourceId() {
    return StrUtil.isNotBlank(sourceId);
  }

  /// 判断是否为 PubMed 数据源。
  ///
  /// @return true 如果为 PubMed
  public boolean isFromPubMed() {
    return sourceCode.isPubMed();
  }

  /// 判断是否为 DOAJ 数据源。
  ///
  /// @return true 如果为 DOAJ
  public boolean isFromDoaj() {
    return sourceCode.isDoaj();
  }

  /// 判断是否为 Crossref 数据源。
  ///
  /// @return true 如果为 Crossref
  public boolean isFromCrossref() {
    return sourceCode.isCrossref();
  }

  /// 判断是否为 JCR 数据源。
  ///
  /// @return true 如果为 JCR
  public boolean isFromJcr() {
    return sourceCode.isJcr();
  }
}
