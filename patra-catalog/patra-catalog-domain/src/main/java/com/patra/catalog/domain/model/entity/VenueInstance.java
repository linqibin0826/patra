package com.patra.catalog.domain.model.entity;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import lombok.Getter;

/// 载体实例实体（Aggregate内实体，不是聚合根）。
///
/// 表示出版载体的具体实例：
///
/// - **期刊**：某卷某期（Volume 29, Issue 3）
///   - **书籍**：某版次（2nd Edition）
///   - **会议**：某届会议（AAAI 2024, San Francisco）
///
/// **业务规则**：
///
/// - 同一Venue的volume+issue组合必须唯一（期刊）
///   - 同一Venue的edition必须唯一（书籍）
///   - 会议实例必须有会议名称和日期
///   - publication_year必填，用于冗余到Publication表优化查询
///
/// **不变量**：
///
/// - venueId不能为空（必须属于某个Venue）
///   - publicationYear必填且在合理范围内（1800-2100）
///   - 会议结束日期不能早于开始日期
///
/// 使用示例：
///
/// ```java
/// // 创建期刊实例：Nature Vol.612, No.5
/// VenueInstance journalInstance = VenueInstance.forJournal(
///     venueId, "612", "5", 2024, 1, 15
/// );
///
/// // 创建书籍实例：第3版
/// VenueInstance bookInstance = VenueInstance.forBook(
///     venueId, "3rd Edition", 2024
/// );
///
/// // 创建会议实例：AAAI 2024
/// VenueInstance confInstance = VenueInstance.forConference(
///     venueId, "AAAI 2024",
///     LocalDate.of(2024, 2, 20),
///     LocalDate.of(2024, 2, 27),
///     "Vancouver, Canada",
///     2024
/// );
/// ```
///
/// @author linqibin
/// @since 0.1.0
@Getter
public class VenueInstance implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  // ========== 标识符 ==========

  /// 主键ID（由Repository在持久化时分配）
  private Long id;

  /// 关联的载体ID（外键）
  private final Long venueId;

  // ========== 期刊字段 ==========

  /// 卷号（如 "45", "2023"，期刊专用）
  private final String volume;

  /// 期号（如 "3", "Suppl 1"，期刊专用）
  private final String issue;

  // ========== 书籍字段 ==========

  /// 版次（如 "2nd Edition"，书籍专用）
  private final String edition;

  // ========== 出版日期 ==========

  /// 出版年份（必填，用于冗余到Publication表）
  private final Integer publicationYear;

  /// 出版月份（1-12，可选）
  private final Integer publicationMonth;

  /// 出版日期（1-31，可选）
  private final Integer publicationDay;

  // ========== 会议字段 ==========

  /// 会议名称（会议专用）
  private final String conferenceName;

  /// 会议开始日期（会议专用）
  private final LocalDate conferenceStartDate;

  /// 会议结束日期（会议专用）
  private final LocalDate conferenceEndDate;

  /// 会议地点（会议专用）
  private final String conferenceLocation;

  // ========== 扩展字段 ==========

  /// 实例元数据（JSON，灵活扩展）
  private String instanceMetadataJson;

  /// 私有构造函数。
  ///
  /// @param id 主键ID（新建时为null）
  /// @param venueId 载体ID
  /// @param volume 卷号
  /// @param issue 期号
  /// @param edition 版次
  /// @param publicationYear 出版年份
  /// @param publicationMonth 出版月份
  /// @param publicationDay 出版日期
  /// @param conferenceName 会议名称
  /// @param conferenceStartDate 会议开始日期
  /// @param conferenceEndDate 会议结束日期
  /// @param conferenceLocation 会议地点
  private VenueInstance(
      Long id,
      Long venueId,
      String volume,
      String issue,
      String edition,
      Integer publicationYear,
      Integer publicationMonth,
      Integer publicationDay,
      String conferenceName,
      LocalDate conferenceStartDate,
      LocalDate conferenceEndDate,
      String conferenceLocation) {
    // 必填字段验证
    Assert.notNull(venueId, "载体ID不能为空");
    Assert.notNull(publicationYear, "出版年份不能为空");

    // 出版年份范围验证
    Assert.isTrue(
        publicationYear >= 1800 && publicationYear <= 2100,
        "出版年份必须在1800-2100范围内：%d",
        publicationYear);

    // 月份范围验证
    if (publicationMonth != null) {
      Assert.isTrue(
          publicationMonth >= 1 && publicationMonth <= 12, "出版月份必须在1-12范围内：%d", publicationMonth);
    }

    // 日期范围验证
    if (publicationDay != null) {
      Assert.isTrue(
          publicationDay >= 1 && publicationDay <= 31, "出版日期必须在1-31范围内：%d", publicationDay);
    }

    // 会议日期一致性验证
    if (conferenceStartDate != null && conferenceEndDate != null) {
      Assert.isTrue(!conferenceEndDate.isBefore(conferenceStartDate), "会议结束日期不能早于开始日期");
    }

    // 赋值
    this.id = id;
    this.venueId = venueId;
    this.volume = volume;
    this.issue = issue;
    this.edition = edition;
    this.publicationYear = publicationYear;
    this.publicationMonth = publicationMonth;
    this.publicationDay = publicationDay;
    this.conferenceName = conferenceName;
    this.conferenceStartDate = conferenceStartDate;
    this.conferenceEndDate = conferenceEndDate;
    this.conferenceLocation = conferenceLocation;
  }

  // ========== 工厂方法 ==========

  /// 创建期刊实例（卷期）。
  ///
  /// @param venueId 载体ID
  /// @param volume 卷号
  /// @param issue 期号
  /// @param publicationYear 出版年份
  /// @param publicationMonth 出版月份（可选）
  /// @param publicationDay 出版日期（可选）
  /// @return 期刊实例
  public static VenueInstance forJournal(
      Long venueId,
      String volume,
      String issue,
      Integer publicationYear,
      Integer publicationMonth,
      Integer publicationDay) {
    return new VenueInstance(
        null, // 新建时ID为null
        venueId,
        volume,
        issue,
        null, // edition仅书籍使用
        publicationYear,
        publicationMonth,
        publicationDay,
        null, // 会议字段为null
        null,
        null,
        null);
  }

  /// 创建书籍实例（版次）。
  ///
  /// @param venueId 载体ID
  /// @param edition 版次
  /// @param publicationYear 出版年份
  /// @return 书籍实例
  public static VenueInstance forBook(Long venueId, String edition, Integer publicationYear) {
    return new VenueInstance(
        null,
        venueId,
        null, // volume/issue仅期刊使用
        null,
        edition,
        publicationYear,
        null, // 书籍通常只有年份
        null,
        null, // 会议字段为null
        null,
        null,
        null);
  }

  /// 创建会议实例。
  ///
  /// @param venueId 载体ID
  /// @param conferenceName 会议名称
  /// @param conferenceStartDate 会议开始日期
  /// @param conferenceEndDate 会议结束日期
  /// @param conferenceLocation 会议地点
  /// @param publicationYear 出版年份
  /// @return 会议实例
  public static VenueInstance forConference(
      Long venueId,
      String conferenceName,
      LocalDate conferenceStartDate,
      LocalDate conferenceEndDate,
      String conferenceLocation,
      Integer publicationYear) {
    return new VenueInstance(
        null,
        venueId,
        null, // volume/issue/edition不适用
        null,
        null,
        publicationYear,
        null, // 会议日期用专门字段
        null,
        conferenceName,
        conferenceStartDate,
        conferenceEndDate,
        conferenceLocation);
  }

  /// 从持久化状态重建实例（由Repository使用）。
  ///
  /// @param id 主键ID
  /// @param venueId 载体ID
  /// @param volume 卷号
  /// @param issue 期号
  /// @param edition 版次
  /// @param publicationYear 出版年份
  /// @param publicationMonth 出版月份
  /// @param publicationDay 出版日期
  /// @param conferenceName 会议名称
  /// @param conferenceStartDate 会议开始日期
  /// @param conferenceEndDate 会议结束日期
  /// @param conferenceLocation 会议地点
  /// @return 重建的实例
  public static VenueInstance restore(
      Long id,
      Long venueId,
      String volume,
      String issue,
      String edition,
      Integer publicationYear,
      Integer publicationMonth,
      Integer publicationDay,
      String conferenceName,
      LocalDate conferenceStartDate,
      LocalDate conferenceEndDate,
      String conferenceLocation) {
    return new VenueInstance(
        id,
        venueId,
        volume,
        issue,
        edition,
        publicationYear,
        publicationMonth,
        publicationDay,
        conferenceName,
        conferenceStartDate,
        conferenceEndDate,
        conferenceLocation);
  }

  // ========== 业务方法 ==========

  /// 设置ID（由Repository在持久化后回写）。
  ///
  /// @param id 主键ID
  public void assignId(Long id) {
    this.id = id;
  }

  /// 设置实例元数据JSON。
  ///
  /// @param json 元数据JSON字符串
  public void setInstanceMetadataJson(String json) {
    this.instanceMetadataJson = json;
  }

  // ========== 便捷判断方法 ==========

  /// 判断是否为期刊实例。
  ///
  /// @return true 如果有卷号或期号
  public boolean isJournalInstance() {
    return StrUtil.isNotBlank(volume) || StrUtil.isNotBlank(issue);
  }

  /// 判断是否为书籍实例。
  ///
  /// @return true 如果有版次
  public boolean isBookInstance() {
    return StrUtil.isNotBlank(edition);
  }

  /// 判断是否为会议实例。
  ///
  /// @return true 如果有会议名称
  public boolean isConferenceInstance() {
    return StrUtil.isNotBlank(conferenceName);
  }

  /// 获取卷期描述（期刊专用）。
  ///
  /// @return 卷期描述，如 "Vol.29, No.3"
  public String getVolumeIssueDescription() {
    if (StrUtil.isBlank(volume) && StrUtil.isBlank(issue)) {
      return "";
    }
    StringBuilder sb = new StringBuilder();
    if (StrUtil.isNotBlank(volume)) {
      sb.append("Vol.").append(volume);
    }
    if (StrUtil.isNotBlank(issue)) {
      if (sb.length() > 0) {
        sb.append(", ");
      }
      sb.append("No.").append(issue);
    }
    return sb.toString();
  }

  /// 获取会议时间范围描述。
  ///
  /// @return 会议时间描述，如 "2024-02-20 至 2024-02-27"
  public String getConferenceDateRange() {
    if (conferenceStartDate == null && conferenceEndDate == null) {
      return "";
    }
    if (conferenceStartDate != null && conferenceEndDate != null) {
      return String.format("%s 至 %s", conferenceStartDate, conferenceEndDate);
    }
    return conferenceStartDate != null
        ? conferenceStartDate.toString()
        : conferenceEndDate.toString();
  }

  @Override
  public String toString() {
    if (isJournalInstance()) {
      return String.format(
          "JournalInstance[%s, year=%d]", getVolumeIssueDescription(), publicationYear);
    } else if (isBookInstance()) {
      return String.format("BookInstance[%s, year=%d]", edition, publicationYear);
    } else if (isConferenceInstance()) {
      return String.format("ConferenceInstance[%s, %s]", conferenceName, getConferenceDateRange());
    } else {
      return String.format("VenueInstance[year=%d]", publicationYear);
    }
  }
}
