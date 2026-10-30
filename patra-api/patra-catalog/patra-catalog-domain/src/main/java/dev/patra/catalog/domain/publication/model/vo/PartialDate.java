package dev.patra.catalog.domain.publication.model.vo;

import java.time.LocalDate;
import java.time.YearMonth;

/// 按实际精度记录的日期：只到年、到年月或到年月日，缺的部件为 null，不补默认值。
///
/// PubMed 的出版日期常常只有年或年月（还可能是季节、`MedlineDate` 区间原文），补成 1 月 1 日会编造信息。
/// 精度由「哪几个部件为 null」决定。
///
/// @param year 年
/// @param month 月（1 ~ 12），没有时为 null
/// @param day 日，没有时为 null；有日时必须有月，且对该月合法
/// @param season 季节原文（如 `Spring`），可空
/// @param text 无法结构化的原文（如 `MedlineDate`、月份区间 `Jun-Jul`），可空
/// @author linqibin
/// @since 0.1.0
public record PartialDate(int year, Integer month, Integer day, String season, String text) {

  /// 紧凑构造器：校验部件组合。
  ///
  /// @throws IllegalArgumentException 月不在 1 ~ 12、有日无月或日对该月不合法时
  public PartialDate {
    if (month != null && (month < 1 || month > 12)) {
      throw new IllegalArgumentException("月份必须在 1 ~ 12：" + month);
    }
    if (day != null) {
      if (month == null) {
        throw new IllegalArgumentException("有日必须有月：day=" + day);
      }
      if (!YearMonth.of(year, month).isValidDay(day)) {
        throw new IllegalArgumentException("日期不合法：%d-%d-%d".formatted(year, month, day));
      }
    }
  }

  /// 只到年。
  ///
  /// @param year 年
  /// @return 年精度日期
  public static PartialDate ofYear(int year) {
    return new PartialDate(year, null, null, null, null);
  }

  /// 到年月。
  ///
  /// @param year 年
  /// @param month 月
  /// @return 月精度日期
  public static PartialDate ofYearMonth(int year, int month) {
    return new PartialDate(year, month, null, null, null);
  }

  /// 到日。
  ///
  /// @param date 完整日期
  /// @return 日精度日期
  public static PartialDate ofDay(LocalDate date) {
    return new PartialDate(date.getYear(), date.getMonthValue(), date.getDayOfMonth(), null, null);
  }

  /// 精度代码，与 `cat_publication_date.date_precision` 一致。
  ///
  /// @return `year`、`month` 或 `day`
  public String precision() {
    if (day != null) {
      return "day";
    }
    return month != null ? "month" : "year";
  }
}
