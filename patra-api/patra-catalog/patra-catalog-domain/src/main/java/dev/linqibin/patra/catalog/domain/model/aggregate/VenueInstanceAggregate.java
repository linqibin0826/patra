package dev.linqibin.patra.catalog.domain.model.aggregate;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import dev.linqibin.commons.domain.AggregateRoot;
import dev.linqibin.patra.catalog.domain.model.vo.venue.VenueId;
import dev.linqibin.patra.catalog.domain.model.vo.venue.VenueInstanceId;
import java.io.Serial;
import java.time.LocalDate;
import lombok.Getter;

/// 载体实例聚合根。表示出版载体的具体实例。
///
/// **实例类型**：
///
/// | 类型 | 示例 | 关键字段 |
/// |------|------|----------|
/// | 期刊 | Nature Vol.612, No.5 | volume, issue |
/// | 书籍 | 2nd Edition | edition |
/// | 会议 | AAAI 2024, Vancouver | conferenceName, conferenceStartDate |
///
/// **聚合边界**：
///
/// 独立聚合根，通过 `venueId` 外键关联 VenueAggregate。
/// 与 VenueAggregate 解耦，支持独立查询和更新。
///
/// **不变量**：
///
/// - venueId 不能为空（必须属于某个 Venue）
/// - publicationYear 必填且在 1800-2100 范围内
/// - publicationMonth 必须在 1-12 范围内（如果有）
/// - publicationDay 必须在 1-31 范围内（如果有）
/// - 会议结束日期不能早于开始日期
///
/// **使用示例**：
///
/// ```java
/// // 创建期刊实例：Nature Vol.612, No.5
/// VenueInstanceAggregate journalInstance = VenueInstanceAggregate.forJournal(
///     venueId, "612", "5", 2024, 1, 15
/// );
///
/// // 创建书籍实例：第3版
/// VenueInstanceAggregate bookInstance = VenueInstanceAggregate.forBook(
///     venueId, "3rd Edition", 2024
/// );
///
/// // 创建会议实例：AAAI 2024
/// VenueInstanceAggregate confInstance = VenueInstanceAggregate.forConference(
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
public class VenueInstanceAggregate extends AggregateRoot<VenueInstanceId> {

  @Serial private static final long serialVersionUID = 1L;

  // ========== 关联引用 ==========

  /// 关联的载体 ID（外键，不可变）
  private final VenueId venueId;

  // ========== 期刊字段 ==========

  /// 卷号（如 "45", "2023"，期刊专用）
  private final String volume;

  /// 期号（如 "3", "Suppl 1"，期刊专用）
  private final String issue;

  // ========== 书籍字段 ==========

  /// 版次（如 "2nd Edition"，书籍专用）
  private final String edition;

  // ========== 出版日期 ==========

  /// 出版年份（必填，用于冗余到 Publication 表）
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
  /// @param id 主键 ID（新建时为 null）
  /// @param venueId 载体 ID
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
  private VenueInstanceAggregate(
      VenueInstanceId id,
      VenueId venueId,
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
    super(id);

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
  /// @param venueId 载体 ID
  /// @param volume 卷号
  /// @param issue 期号
  /// @param publicationYear 出版年份
  /// @param publicationMonth 出版月份（可选）
  /// @param publicationDay 出版日期（可选）
  /// @return 期刊实例聚合根
  public static VenueInstanceAggregate forJournal(
      VenueId venueId,
      String volume,
      String issue,
      Integer publicationYear,
      Integer publicationMonth,
      Integer publicationDay) {
    return new VenueInstanceAggregate(
        null,
        venueId,
        volume,
        issue,
        null,
        publicationYear,
        publicationMonth,
        publicationDay,
        null,
        null,
        null,
        null);
  }

  /// 创建书籍实例（版次）。
  ///
  /// @param venueId 载体 ID
  /// @param edition 版次
  /// @param publicationYear 出版年份
  /// @return 书籍实例聚合根
  public static VenueInstanceAggregate forBook(
      VenueId venueId, String edition, Integer publicationYear) {
    return new VenueInstanceAggregate(
        null, venueId, null, null, edition, publicationYear, null, null, null, null, null, null);
  }

  /// 创建会议实例。
  ///
  /// @param venueId 载体 ID
  /// @param conferenceName 会议名称
  /// @param conferenceStartDate 会议开始日期
  /// @param conferenceEndDate 会议结束日期
  /// @param conferenceLocation 会议地点
  /// @param publicationYear 出版年份
  /// @return 会议实例聚合根
  public static VenueInstanceAggregate forConference(
      VenueId venueId,
      String conferenceName,
      LocalDate conferenceStartDate,
      LocalDate conferenceEndDate,
      String conferenceLocation,
      Integer publicationYear) {
    return new VenueInstanceAggregate(
        null,
        venueId,
        null,
        null,
        null,
        publicationYear,
        null,
        null,
        conferenceName,
        conferenceStartDate,
        conferenceEndDate,
        conferenceLocation);
  }

  /// 从持久化状态重建聚合根（由 Repository 使用）。
  ///
  /// @param id 主键 ID（VenueInstanceId 值对象）
  /// @param venueId 载体 ID
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
  /// @param version 乐观锁版本
  /// @return 重建的聚合根
  public static VenueInstanceAggregate restore(
      VenueInstanceId id,
      VenueId venueId,
      String volume,
      String issue,
      String edition,
      Integer publicationYear,
      Integer publicationMonth,
      Integer publicationDay,
      String conferenceName,
      LocalDate conferenceStartDate,
      LocalDate conferenceEndDate,
      String conferenceLocation,
      Long version) {
    VenueInstanceAggregate aggregate =
        new VenueInstanceAggregate(
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
    aggregate.assignVersion(version != null ? version : 0L);
    return aggregate;
  }

  // ========== 业务方法 ==========

  /// 设置实例元数据 JSON。
  ///
  /// @param json 元数据 JSON 字符串
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
      if (!sb.isEmpty()) {
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

  // ========== 不变量验证 ==========

  /// 验证聚合根的业务不变量。
  ///
  /// @throws IllegalStateException 如果不变量被违反
  @Override
  protected void assertInvariants() {
    if (venueId == null) {
      throw new IllegalStateException("载体ID不能为空");
    }
    if (publicationYear == null) {
      throw new IllegalStateException("出版年份不能为空");
    }
    if (publicationYear < 1800 || publicationYear > 2100) {
      throw new IllegalStateException("出版年份必须在1800-2100范围内");
    }
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
