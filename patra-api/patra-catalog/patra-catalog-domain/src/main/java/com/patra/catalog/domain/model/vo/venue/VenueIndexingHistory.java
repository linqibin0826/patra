package com.patra.catalog.domain.model.vo.venue;

import cn.hutool.core.lang.Assert;
import com.patra.catalog.domain.model.enums.CitationSubset;
import com.patra.catalog.domain.model.enums.IndexingTreatment;
import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

/// 期刊索引历史值对象（不可变）。
///
/// **设计说明**：
///
/// - 作为值对象存在（不是实体）
/// - 使用 Record 实现不可变性
/// - 通过 `VenueRepository` 统一管理
/// - 记录期刊在各索引数据库（MEDLINE、PMC 等）的索引历史
/// - 数据主要来源于 NLM Serfile 的 IndexingHistoryList 元素
///
/// **索引状态说明**：
///
/// | 状态 | 含义 |
/// |------|------|
/// | currentlyIndexed=true | 期刊当前正在被索引 |
/// | currentlyIndexed=false | 期刊曾被索引但已停止 |
///
/// **索引处理方式（IndexingTreatment）**：
///
/// - **FULL**：全文索引，期刊的所有文章都被索引
/// - **SELECTIVE**：选择性索引，只有部分文章被索引
///
/// **示例**：
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
/// @param indexingSource 索引来源（MEDLINE、PMC 等，必填）
/// @param currentlyIndexed 当前是否被索引
/// @param indexingTreatment 索引处理方式（可选）
/// @param citationSubset 引用子集（可选）
/// @param startYear 索引开始年份（可选）
/// @param startVolume 索引开始卷号（可选）
/// @param startIssue 索引开始期号（可选）
/// @param endYear 索引结束年份（仍在索引则为 null）
/// @param endVolume 索引结束卷号（可选）
/// @param endIssue 索引结束期号（可选）
/// @author linqibin
/// @since 0.1.0
@SuppressWarnings("java:S6218") // Record 有自定义 equals/hashCode，已实现正确的业务相等性
public record VenueIndexingHistory(
    String indexingSource,
    boolean currentlyIndexed,
    IndexingTreatment indexingTreatment,
    CitationSubset citationSubset,
    Integer startYear,
    String startVolume,
    String startIssue,
    Integer endYear,
    String endVolume,
    String endIssue)
    implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /// 紧凑构造器：验证参数。
  public VenueIndexingHistory {
    Assert.notBlank(indexingSource, "索引来源不能为空");
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
  /// @return 索引历史值对象
  public static VenueIndexingHistory createCurrentIndexing(
      String indexingSource,
      IndexingTreatment indexingTreatment,
      CitationSubset citationSubset,
      Integer startYear,
      String startVolume,
      String startIssue) {
    return new VenueIndexingHistory(
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
  /// @return 索引历史值对象
  public static VenueIndexingHistory createHistoricalIndexing(
      String indexingSource,
      Integer startYear,
      String startVolume,
      String startIssue,
      Integer endYear,
      String endVolume,
      String endIssue) {
    return new VenueIndexingHistory(
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
  /// @return 索引历史值对象
  public static VenueIndexingHistory createSimpleCurrentIndexing(String indexingSource) {
    return new VenueIndexingHistory(
        indexingSource, true, null, null, null, null, null, null, null, null);
  }

  // ========== 查询方法 ==========

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

  /// 业务相等性：索引来源 + 开始年份。
  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof VenueIndexingHistory that)) {
      return false;
    }
    return Objects.equals(indexingSource, that.indexingSource)
        && Objects.equals(startYear, that.startYear);
  }

  @Override
  public int hashCode() {
    return Objects.hash(indexingSource, startYear);
  }
}
