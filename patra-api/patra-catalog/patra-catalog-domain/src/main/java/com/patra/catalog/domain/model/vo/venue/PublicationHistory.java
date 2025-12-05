package com.patra.catalog.domain.model.vo.venue;

import java.io.Serial;
import java.io.Serializable;

/// 出版历史值对象。封装载体的出版时间范围和停刊状态。
///
/// 设计原则：
///
/// - 不可变性：Record 自动提供
/// - 可选性：年份字段可为空，支持仅知道创刊或停刊年份的场景
/// - 业务规则：停刊年份必须大于等于创刊年份（如果两者都存在）
///
/// 数据来源：
///
/// 主要来自 PubMed Catalog 的期刊历史数据。
///
/// 使用示例：
///
/// ```java
/// // 正常出版中的期刊
/// PublicationHistory active = PublicationHistory.of(1990, null, false);
///
/// // 已停刊期刊
/// PublicationHistory ceased = PublicationHistory.ceased(1950, 2010);
///
/// // 创刊年份未知但已停刊
/// PublicationHistory unknownStart = PublicationHistory.of(null, 2020, true);
/// ```
///
/// @param startYear 创刊年份（可为空）
/// @param endYear 停刊年份（可为空，仅已停刊期刊有值）
/// @param ceased 是否已停刊
/// @author linqibin
/// @since 0.1.0
public record PublicationHistory(Integer startYear, Integer endYear, boolean ceased)
    implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /// 紧凑构造器：验证出版年份的业务规则。
  ///
  /// @throws IllegalArgumentException 如果停刊年份早于创刊年份
  public PublicationHistory {
    if (startYear != null && endYear != null && endYear < startYear) {
      throw new IllegalArgumentException("停刊年份(%d)不能早于创刊年份(%d)".formatted(endYear, startYear));
    }
    // 如果有停刊年份，ceased 应该为 true
    if (endYear != null && !ceased) {
      ceased = true;
    }
  }

  /// 创建出版历史。
  ///
  /// @param startYear 创刊年份
  /// @param endYear 停刊年份
  /// @param ceased 是否已停刊
  /// @return 出版历史值对象
  public static PublicationHistory of(Integer startYear, Integer endYear, boolean ceased) {
    return new PublicationHistory(startYear, endYear, ceased);
  }

  /// 创建活跃出版的期刊历史。
  ///
  /// @param startYear 创刊年份
  /// @return 出版历史值对象
  public static PublicationHistory active(Integer startYear) {
    return new PublicationHistory(startYear, null, false);
  }

  /// 创建已停刊期刊的出版历史。
  ///
  /// @param startYear 创刊年份
  /// @param endYear 停刊年份
  /// @return 出版历史值对象
  public static PublicationHistory ceased(Integer startYear, Integer endYear) {
    return new PublicationHistory(startYear, endYear, true);
  }

  /// 判断期刊是否仍在出版。
  ///
  /// @return true 如果期刊仍在出版
  public boolean isActive() {
    return !ceased;
  }

  /// 判断是否有创刊年份信息。
  ///
  /// @return true 如果有创刊年份
  public boolean hasStartYear() {
    return startYear != null;
  }

  /// 判断是否有停刊年份信息。
  ///
  /// @return true 如果有停刊年份
  public boolean hasEndYear() {
    return endYear != null;
  }

  /// 计算出版年限。
  ///
  /// @param currentYear 当前年份（用于计算活跃期刊的出版年限）
  /// @return 出版年限，如果无法计算则返回 null
  public Integer calculateYearsPublished(int currentYear) {
    if (startYear == null) {
      return null;
    }
    int end = ceased && endYear != null ? endYear : currentYear;
    return end - startYear + 1;
  }
}
