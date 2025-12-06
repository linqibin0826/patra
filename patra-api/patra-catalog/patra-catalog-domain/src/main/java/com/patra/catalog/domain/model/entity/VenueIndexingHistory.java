package com.patra.catalog.domain.model.entity;

import cn.hutool.core.lang.Assert;
import com.patra.catalog.domain.model.enums.CitationSubset;
import com.patra.catalog.domain.model.enums.IndexingTreatment;
import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;
import lombok.Getter;

/// 期刊索引历史实体（聚合内实体，不是聚合根）。
///
/// 设计说明：
///
/// - 作为 VenueAggregate 的聚合内实体存在
/// - 记录期刊在各索引数据库（MEDLINE、PMC 等）的索引历史
/// - 数据主要来源于 NLM Serfile 的 IndexingHistoryList 元素
///
/// 索引状态说明：
///
/// | 状态 | 含义 |
/// |------|------|
/// | currentlyIndexed=true | 期刊当前正在被索引 |
/// | currentlyIndexed=false | 期刊曾被索引但已停止 |
///
/// 索引处理方式（IndexingTreatment）：
///
/// - **FULL**：全文索引，期刊的所有文章都被索引
/// - **SELECTIVE**：选择性索引，只有部分文章被索引
///
/// 使用示例：
///
/// ```java
/// // 创建当前 MEDLINE 索引记录
/// VenueIndexingHistory history = VenueIndexingHistory.createCurrentIndexing(
///     "MEDLINE",
///     IndexingTreatment.FULL,
///     CitationSubset.IM,
///     1966,
///     "1",
///     "1"
/// );
///
/// // 创建历史索引记录（已停止索引）
/// VenueIndexingHistory historicalRecord = VenueIndexingHistory.createHistoricalIndexing(
///     "MEDLINE",
///     1950, "1", "1",
///     1965, "15", "12"
/// );
/// ```
///
/// @author linqibin
/// @since 0.1.0
@Getter
public class VenueIndexingHistory implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  // ========== 标识符 ==========

  /// 主键 ID（由 Repository 在持久化时分配）
  private Long id;

  // ========== 业务字段 ==========

  /// 索引来源（MEDLINE、PMC 等）
  private final String indexingSource;

  /// 当前是否被索引
  private final boolean currentlyIndexed;

  /// 索引处理方式
  private final IndexingTreatment indexingTreatment;

  /// 引用子集
  private final CitationSubset citationSubset;

  /// 索引开始年份
  private final Integer startYear;

  /// 索引开始卷号
  private final String startVolume;

  /// 索引开始期号
  private final String startIssue;

  /// 索引结束年份（仍在索引则为 null）
  private final Integer endYear;

  /// 索引结束卷号
  private final String endVolume;

  /// 索引结束期号
  private final String endIssue;

  /// 私有构造函数。
  @SuppressWarnings("java:S107") // 参数数量多是必要的，因为实体字段多
  private VenueIndexingHistory(
      Long id,
      String indexingSource,
      boolean currentlyIndexed,
      IndexingTreatment indexingTreatment,
      CitationSubset citationSubset,
      Integer startYear,
      String startVolume,
      String startIssue,
      Integer endYear,
      String endVolume,
      String endIssue) {
    Assert.notBlank(indexingSource, "索引来源不能为空");

    this.id = id;
    this.indexingSource = indexingSource;
    this.currentlyIndexed = currentlyIndexed;
    this.indexingTreatment = indexingTreatment;
    this.citationSubset = citationSubset;
    this.startYear = startYear;
    this.startVolume = startVolume;
    this.startIssue = startIssue;
    this.endYear = endYear;
    this.endVolume = endVolume;
    this.endIssue = endIssue;
  }

  // ========== 工厂方法 ==========

  /// 创建当前正在索引的记录。
  ///
  /// @param indexingSource 索引来源
  /// @param indexingTreatment 索引处理方式
  /// @param citationSubset 引用子集
  /// @param startYear 索引开始年份
  /// @param startVolume 索引开始卷号
  /// @param startIssue 索引开始期号
  /// @return 索引历史实体
  public static VenueIndexingHistory createCurrentIndexing(
      String indexingSource,
      IndexingTreatment indexingTreatment,
      CitationSubset citationSubset,
      Integer startYear,
      String startVolume,
      String startIssue) {
    return new VenueIndexingHistory(
        null,
        indexingSource,
        true,
        indexingTreatment,
        citationSubset,
        startYear,
        startVolume,
        startIssue,
        null,
        null,
        null);
  }

  /// 创建历史索引记录（已停止索引）。
  ///
  /// @param indexingSource 索引来源
  /// @param startYear 索引开始年份
  /// @param startVolume 索引开始卷号
  /// @param startIssue 索引开始期号
  /// @param endYear 索引结束年份
  /// @param endVolume 索引结束卷号
  /// @param endIssue 索引结束期号
  /// @return 索引历史实体
  public static VenueIndexingHistory createHistoricalIndexing(
      String indexingSource,
      Integer startYear,
      String startVolume,
      String startIssue,
      Integer endYear,
      String endVolume,
      String endIssue) {
    return new VenueIndexingHistory(
        null,
        indexingSource,
        false,
        null,
        null,
        startYear,
        startVolume,
        startIssue,
        endYear,
        endVolume,
        endIssue);
  }

  /// 创建简单的当前索引记录。
  ///
  /// @param indexingSource 索引来源
  /// @return 索引历史实体
  public static VenueIndexingHistory createSimpleCurrentIndexing(String indexingSource) {
    return new VenueIndexingHistory(
        null, indexingSource, true, null, null, null, null, null, null, null, null);
  }

  /// 从持久化状态重建实体（由 Repository 使用）。
  @SuppressWarnings("java:S107")
  public static VenueIndexingHistory restore(
      Long id,
      String indexingSource,
      boolean currentlyIndexed,
      IndexingTreatment indexingTreatment,
      CitationSubset citationSubset,
      Integer startYear,
      String startVolume,
      String startIssue,
      Integer endYear,
      String endVolume,
      String endIssue) {
    return new VenueIndexingHistory(
        id,
        indexingSource,
        currentlyIndexed,
        indexingTreatment,
        citationSubset,
        startYear,
        startVolume,
        startIssue,
        endYear,
        endVolume,
        endIssue);
  }

  // ========== 业务方法 ==========

  /// 设置 ID（由 Repository 在持久化后回写）。
  ///
  /// @param id 主键 ID
  public void assignId(Long id) {
    this.id = id;
  }

  /// 判断是否为 MEDLINE 索引。
  ///
  /// @return true 如果为 MEDLINE 索引
  public boolean isMedline() {
    return "MEDLINE".equalsIgnoreCase(indexingSource);
  }

  /// 判断是否为 PMC 索引。
  ///
  /// @return true 如果为 PMC 索引
  public boolean isPmc() {
    return "PMC".equalsIgnoreCase(indexingSource);
  }

  /// 判断是否为全文索引。
  ///
  /// @return true 如果为全文索引
  public boolean isFullIndexing() {
    return indexingTreatment != null && indexingTreatment.isFullIndexing();
  }

  /// 判断是否为核心医学期刊索引（IM 或 AIM）。
  ///
  /// @return true 如果为核心医学期刊
  public boolean isCoreMedical() {
    return citationSubset != null && citationSubset.isCoreMedical();
  }

  /// 判断是否有开始信息。
  ///
  /// @return true 如果有开始年份
  public boolean hasStartInfo() {
    return startYear != null;
  }

  /// 判断是否有结束信息。
  ///
  /// @return true 如果有结束年份
  public boolean hasEndInfo() {
    return endYear != null;
  }

  /// 获取索引年限范围描述。
  ///
  /// @return 年限范围，如 "1966-present" 或 "1950-1965"
  public String getYearRange() {
    if (startYear == null) {
      return "unknown";
    }
    if (currentlyIndexed || endYear == null) {
      return startYear + "-present";
    }
    return startYear + "-" + endYear;
  }

  /// 获取开始位置描述。
  ///
  /// @return 开始位置，如 "Vol.1, Issue 1 (1966)"
  public String getStartPosition() {
    if (startYear == null) {
      return null;
    }
    StringBuilder sb = new StringBuilder();
    if (startVolume != null) {
      sb.append("Vol.").append(startVolume);
    }
    if (startIssue != null) {
      if (!sb.isEmpty()) {
        sb.append(", ");
      }
      sb.append("Issue ").append(startIssue);
    }
    if (!sb.isEmpty()) {
      sb.append(" (").append(startYear).append(")");
    } else {
      sb.append(startYear);
    }
    return sb.toString();
  }

  @Override
  public String toString() {
    return String.format(
        "VenueIndexingHistory[source=%s, current=%b, range=%s]",
        indexingSource, currentlyIndexed, getYearRange());
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof VenueIndexingHistory that)) {
      return false;
    }
    // 业务相等性：索引来源 + 开始年份
    return Objects.equals(indexingSource, that.indexingSource)
        && Objects.equals(startYear, that.startYear);
  }

  @Override
  public int hashCode() {
    return Objects.hash(indexingSource, startYear);
  }
}
