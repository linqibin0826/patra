package com.patra.catalog.domain.model.entity;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import com.patra.catalog.domain.model.enums.DataSourceCode;
import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import lombok.Getter;

/// 载体数据源实体（独立实体，非聚合成员）。
///
/// 设计说明：
///
/// - 存储各数据源的原始数据和提取字段
/// - 支持数据溯源和审计
/// - 每个 Venue 可关联多个数据源记录
/// - 同一 Venue + 数据源代码唯一（uk_venue_source）
///
/// 业务规则：
///
/// - venue_id 和 source_code 必填
/// - source_id 可选（不同数据源的 ID 格式不同）
/// - raw_data 和 extracted_data 为 JSON 字符串
/// - fetched_at 记录数据获取时间
///
/// 数据来源：
///
/// - OPENALEX：OpenAlex Source 对象
/// - PUBMED：NLM Catalog 记录
/// - DOAJ：DOAJ Journal 对象
/// - CROSSREF：Crossref Member 对象
/// - JCR：Journal Citation Reports 数据
///
/// 使用示例：
///
/// ```java
/// // 创建 OpenAlex 数据源记录
/// VenueSourceData sourceData = VenueSourceData.create(
///     123L,
///     DataSourceCode.OPENALEX,
///     "S1234567890",
///     rawJson,
///     extractedJson
/// );
///
/// // 设置源数据时间戳
/// sourceData.withSourceTimestamps(createdDate, updatedDate);
/// ```
///
/// @author linqibin
/// @since 0.1.0
@Getter
public class VenueSourceData implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  // ========== 标识符 ==========

  /// 主键 ID（由 Repository 在持久化时分配）
  private Long id;

  /// 关联的 Venue ID（必填）
  private final Long venueId;

  // ========== 数据源信息 ==========

  /// 数据源代码（必填）
  private final DataSourceCode sourceCode;

  /// 数据源系统中的 ID（如 OpenAlex 的 S1234567890）
  private String sourceId;

  // ========== 原始数据 ==========

  /// 原始 JSON 数据（完整保存用于审计）
  private String rawData;

  /// 提取的关键字段（JSON 格式）
  private String extractedData;

  // ========== 时间戳 ==========

  /// 源数据创建时间（来自数据源）
  private LocalDate sourceCreatedAt;

  /// 源数据更新时间（来自数据源）
  private LocalDate sourceUpdatedAt;

  /// 数据获取时间（本系统获取的时间）
  private Instant fetchedAt;

  /// 私有构造函数。
  ///
  /// @param id 主键 ID（新建时为 null）
  /// @param venueId 关联的 Venue ID
  /// @param sourceCode 数据源代码
  private VenueSourceData(Long id, Long venueId, DataSourceCode sourceCode) {
    Assert.notNull(venueId, "Venue ID 不能为空");
    Assert.notNull(sourceCode, "数据源代码不能为空");

    this.id = id;
    this.venueId = venueId;
    this.sourceCode = sourceCode;
  }

  // ========== 工厂方法 ==========

  /// 创建数据源记录。
  ///
  /// @param venueId 关联的 Venue ID
  /// @param sourceCode 数据源代码
  /// @param sourceId 数据源系统中的 ID（可选）
  /// @param rawData 原始 JSON 数据
  /// @param extractedData 提取的关键字段
  /// @return 数据源实体
  public static VenueSourceData create(
      Long venueId,
      DataSourceCode sourceCode,
      String sourceId,
      String rawData,
      String extractedData) {
    VenueSourceData data = new VenueSourceData(null, venueId, sourceCode);
    data.sourceId = sourceId;
    data.rawData = rawData;
    data.extractedData = extractedData;
    data.fetchedAt = Instant.now();
    return data;
  }

  /// 创建数据源记录（仅必填字段）。
  ///
  /// @param venueId 关联的 Venue ID
  /// @param sourceCode 数据源代码
  /// @return 数据源实体
  public static VenueSourceData create(Long venueId, DataSourceCode sourceCode) {
    VenueSourceData data = new VenueSourceData(null, venueId, sourceCode);
    data.fetchedAt = Instant.now();
    return data;
  }

  /// 从持久化状态重建实体（由 Repository 使用）。
  ///
  /// @param id 主键 ID
  /// @param venueId 关联的 Venue ID
  /// @param sourceCode 数据源代码
  /// @return 重建的实体
  public static VenueSourceData restore(Long id, Long venueId, DataSourceCode sourceCode) {
    return new VenueSourceData(id, venueId, sourceCode);
  }

  // ========== 设置方法（链式调用） ==========

  /// 设置 ID（由 Repository 在持久化后回写）。
  ///
  /// @param id 主键 ID
  public void assignId(Long id) {
    this.id = id;
  }

  /// 设置数据源系统中的 ID。
  ///
  /// @param sourceId 数据源 ID
  /// @return 当前对象
  public VenueSourceData withSourceId(String sourceId) {
    this.sourceId = sourceId;
    return this;
  }

  /// 设置原始 JSON 数据。
  ///
  /// @param rawData 原始 JSON 数据
  /// @return 当前对象
  public VenueSourceData withRawData(String rawData) {
    this.rawData = rawData;
    return this;
  }

  /// 设置提取的关键字段。
  ///
  /// @param extractedData 提取的字段 JSON
  /// @return 当前对象
  public VenueSourceData withExtractedData(String extractedData) {
    this.extractedData = extractedData;
    return this;
  }

  /// 设置源数据时间戳。
  ///
  /// @param createdAt 源数据创建时间
  /// @param updatedAt 源数据更新时间
  /// @return 当前对象
  public VenueSourceData withSourceTimestamps(LocalDate createdAt, LocalDate updatedAt) {
    this.sourceCreatedAt = createdAt;
    this.sourceUpdatedAt = updatedAt;
    return this;
  }

  /// 设置数据获取时间。
  ///
  /// @param fetchedAt 数据获取时间
  /// @return 当前对象
  public VenueSourceData withFetchedAt(Instant fetchedAt) {
    this.fetchedAt = fetchedAt;
    return this;
  }

  // ========== 业务方法 ==========

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

  /// 判断是否为 OpenAlex 数据源。
  ///
  /// @return true 如果为 OpenAlex
  public boolean isFromOpenAlex() {
    return sourceCode.isOpenAlex();
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

  /// 更新原始数据和提取字段。
  ///
  /// @param rawData 新的原始数据
  /// @param extractedData 新的提取字段
  public void updateData(String rawData, String extractedData) {
    this.rawData = rawData;
    this.extractedData = extractedData;
    this.fetchedAt = Instant.now();
  }

  @Override
  public String toString() {
    return String.format(
        "VenueSourceData[venueId=%d, source=%s, sourceId=%s, hasRaw=%s]",
        venueId, sourceCode.getCode(), sourceId, hasRawData());
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof VenueSourceData that)) {
      return false;
    }
    // 业务相等性：venueId + sourceCode
    return Objects.equals(venueId, that.venueId) && sourceCode == that.sourceCode;
  }

  @Override
  public int hashCode() {
    return Objects.hash(venueId, sourceCode);
  }
}
