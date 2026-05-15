package dev.linqibin.patra.catalog.domain.model.vo.publication;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import dev.linqibin.patra.catalog.domain.model.enums.DatePrecision;
import dev.linqibin.patra.catalog.domain.model.enums.PublicationDateType;
import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;

/// 文献日期值对象。
///
/// 封装文献生命周期中的各类日期信息（投稿、接收、发表、修订等）。
///
/// **支持不完整日期**：
///
/// 医学文献的日期经常不完整，本值对象支持三种精度级别：
///
/// - **DAY** - 精确到日（如 2024-03-15）
/// - **MONTH** - 精确到月（如 2024-03，日期未知）
/// - **YEAR** - 仅有年份（如 2024，月/日未知）
///
/// **补充数据（聚合边界外）**：
///
/// - 日期信息通过 Repository 独立管理
/// - 一个文献可有多个不同类型的日期
/// - 使用 `PublicationRepository.replaceDatesBatch()` 批量替换
///
/// **验证规则**：
///
/// - 年份必填，范围 1800-2100
/// - 月份可选，范围 1-12
/// - 日期可选，范围 1-31
/// - 精度自动推断（如有日则为 DAY，仅有月则为 MONTH）
///
/// 使用示例：
///
/// ```java
/// // 创建完整日期
/// PublicationDate date1 = PublicationDate.of(PublicationDateType.RECEIVED, 2024, 3, 15);
///
/// // 创建仅年月的日期
/// PublicationDate date2 = PublicationDate.ofYearMonth(PublicationDateType.PUBLISHED, 2024, 6);
///
/// // 创建仅年份的日期
/// PublicationDate date3 = PublicationDate.ofYear(PublicationDateType.ACCEPTED, 2024);
///
/// // 创建带季节的日期
/// PublicationDate date4 = PublicationDate.builder()
///     .dateType(PublicationDateType.PUBLISHED)
///     .year(2024)
///     .season("Spring")
///     .isPrimary(true)
///     .build();
/// ```
///
/// @param dateType 日期类型
/// @param year 年份（必填，1800-2100）
/// @param month 月份（可选，1-12）
/// @param day 日（可选，1-31）
/// @param datePrecision 日期精度
/// @param season 季节（如 "Spring 2024"）
/// @param dateString 原始日期字符串
/// @param isPrimary 是否主要日期
/// @param orderNum 顺序号
/// @author linqibin
/// @since 0.1.0
public record PublicationDate(
    PublicationDateType dateType,
    int year,
    Integer month,
    Integer day,
    DatePrecision datePrecision,
    String season,
    String dateString,
    boolean isPrimary,
    Integer orderNum)
    implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /// 紧凑构造器：验证日期的有效性并自动推断精度。
  ///
  /// @throws IllegalArgumentException 如果日期类型为空或年份超出范围
  public PublicationDate {
    Assert.notNull(dateType, "日期类型不能为空");
    Assert.isTrue(year >= 1800 && year <= 2100, "年份必须在 1800-2100 范围内：%d", year);

    if (month != null) {
      Assert.isTrue(month >= 1 && month <= 12, "月份必须在 1-12 范围内：%d", month);
    }

    if (day != null) {
      Assert.isTrue(day >= 1 && day <= 31, "日期必须在 1-31 范围内：%d", day);
      Assert.notNull(month, "指定日期时必须同时指定月份");
    }

    // 自动推断精度
    if (datePrecision == null) {
      if (day != null) {
        datePrecision = DatePrecision.DAY;
      } else if (month != null) {
        datePrecision = DatePrecision.MONTH;
      } else {
        datePrecision = DatePrecision.YEAR;
      }
    }
  }

  /// 创建完整日期（精确到日）。
  ///
  /// @param dateType 日期类型
  /// @param year 年份
  /// @param month 月份
  /// @param day 日期
  /// @return 日期值对象
  public static PublicationDate of(PublicationDateType dateType, int year, int month, int day) {
    return new PublicationDate(
        dateType, year, month, day, DatePrecision.DAY, null, null, false, null);
  }

  /// 创建年月日期（精确到月）。
  ///
  /// @param dateType 日期类型
  /// @param year 年份
  /// @param month 月份
  /// @return 日期值对象
  public static PublicationDate ofYearMonth(PublicationDateType dateType, int year, int month) {
    return new PublicationDate(
        dateType, year, month, null, DatePrecision.MONTH, null, null, false, null);
  }

  /// 创建仅年份日期。
  ///
  /// @param dateType 日期类型
  /// @param year 年份
  /// @return 日期值对象
  public static PublicationDate ofYear(PublicationDateType dateType, int year) {
    return new PublicationDate(
        dateType, year, null, null, DatePrecision.YEAR, null, null, false, null);
  }

  /// 创建主要日期（标记为 isPrimary=true）。
  ///
  /// @param dateType 日期类型
  /// @param year 年份
  /// @param month 月份（可为 null）
  /// @param day 日期（可为 null）
  /// @return 主要日期值对象
  public static PublicationDate primary(
      PublicationDateType dateType, int year, Integer month, Integer day) {
    return new PublicationDate(dateType, year, month, day, null, null, null, true, null);
  }

  /// 判断是否为完整日期（精确到日）。
  ///
  /// @return true 如果精度为 DAY
  public boolean isComplete() {
    return datePrecision == DatePrecision.DAY;
  }

  /// 判断是否有季节信息。
  ///
  /// @return true 如果 season 不为空
  public boolean hasSeason() {
    return StrUtil.isNotBlank(season);
  }

  /// 判断是否有原始日期字符串。
  ///
  /// @return true 如果 dateString 不为空
  public boolean hasDateString() {
    return StrUtil.isNotBlank(dateString);
  }

  /// 判断是否为发表相关日期。
  ///
  /// @return true 如果为 PUBLISHED、EPUBLISH 或 PPUBLISH
  public boolean isPublicationDate() {
    return dateType.isPublicationDate();
  }

  /// 转换为 LocalDate（仅完整日期可用）。
  ///
  /// @return LocalDate 对象
  /// @throws IllegalStateException 如果不是完整日期
  public LocalDate toLocalDate() {
    if (!isComplete()) {
      throw new IllegalStateException("只有精确到日的日期才能转换为 LocalDate");
    }
    return LocalDate.of(year, month, day);
  }

  /// 尝试转换为 LocalDate（不完整日期返回 null）。
  ///
  /// @return LocalDate 或 null
  public LocalDate toLocalDateOrNull() {
    if (!isComplete()) {
      return null;
    }
    return LocalDate.of(year, month, day);
  }

  /// 获取显示文本。
  ///
  /// @return 格式化的日期文本
  public String toDisplayString() {
    if (StrUtil.isNotBlank(dateString)) {
      return dateString;
    }
    return switch (datePrecision) {
      case DAY -> String.format("%04d-%02d-%02d", year, month, day);
      case MONTH -> String.format("%04d-%02d", year, month);
      case YEAR -> String.valueOf(year);
    };
  }

  /// 构建器创建方法（用于复杂场景）。
  ///
  /// @return PublicationDateBuilder
  public static PublicationDateBuilder builder() {
    return new PublicationDateBuilder();
  }

  /// 日期构建器（用于需要设置多个可选字段的场景）。
  public static class PublicationDateBuilder {
    private PublicationDateType dateType;
    private int year;
    private Integer month;
    private Integer day;
    private DatePrecision datePrecision;
    private String season;
    private String dateString;
    private boolean isPrimary;
    private Integer orderNum;

    /// 设置日期类型。
    public PublicationDateBuilder dateType(PublicationDateType dateType) {
      this.dateType = dateType;
      return this;
    }

    /// 设置年份。
    public PublicationDateBuilder year(int year) {
      this.year = year;
      return this;
    }

    /// 设置月份。
    public PublicationDateBuilder month(Integer month) {
      this.month = month;
      return this;
    }

    /// 设置日期。
    public PublicationDateBuilder day(Integer day) {
      this.day = day;
      return this;
    }

    /// 设置日期精度。
    public PublicationDateBuilder datePrecision(DatePrecision datePrecision) {
      this.datePrecision = datePrecision;
      return this;
    }

    /// 设置季节。
    public PublicationDateBuilder season(String season) {
      this.season = season;
      return this;
    }

    /// 设置原始日期字符串。
    public PublicationDateBuilder dateString(String dateString) {
      this.dateString = dateString;
      return this;
    }

    /// 设置是否主要日期。
    public PublicationDateBuilder isPrimary(boolean isPrimary) {
      this.isPrimary = isPrimary;
      return this;
    }

    /// 设置顺序号。
    public PublicationDateBuilder orderNum(Integer orderNum) {
      this.orderNum = orderNum;
      return this;
    }

    /// 构建日期值对象。
    public PublicationDate build() {
      return new PublicationDate(
          dateType, year, month, day, datePrecision, season, dateString, isPrimary, orderNum);
    }
  }
}
