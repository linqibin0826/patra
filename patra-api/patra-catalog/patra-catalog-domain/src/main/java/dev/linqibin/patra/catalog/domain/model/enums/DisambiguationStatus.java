package dev.linqibin.patra.catalog.domain.model.enums;

import cn.hutool.core.lang.Assert;
import lombok.Getter;

/// 机构消歧状态枚举。
///
/// 字段映射：cat_publication_author_affiliation.disambiguation_status
///
/// 支持的状态：
///
/// - **PENDING**：待消歧，新导入的机构归属记录
/// - **MATCHED**：已匹配，成功关联到 OrganizationAggregate
/// - **UNMATCHED**：无法匹配，机构不在 ROR 数据库中
/// - **AMBIGUOUS**：有歧义，存在多个可能的匹配结果
///
/// 状态转换规则：
///
/// ```
/// PENDING ──────────────────┬──────────────────> MATCHED
///    │                      │
///    ├──────────────────────┼──────────────────> UNMATCHED
///    │                      │
///    └──────────────────────┴──────────────────> AMBIGUOUS
/// ```
///
/// 使用示例：
///
/// ```java
/// DisambiguationStatus status = DisambiguationStatus.fromCode("PENDING");
/// if (status.isPending()) {
///     // 处理待消歧记录
/// }
/// ```
///
/// @author linqibin
/// @since 0.1.0
@Getter
public enum DisambiguationStatus {

  /// 待消歧（新导入记录）
  PENDING("PENDING", "待消歧"),

  /// 已匹配（成功关联到机构）
  MATCHED("MATCHED", "已匹配"),

  /// 无法匹配（机构不在数据库中）
  UNMATCHED("UNMATCHED", "无法匹配"),

  /// 有歧义（多个可能匹配）
  AMBIGUOUS("AMBIGUOUS", "有歧义");

  /// 数据库存储的代码值
  private final String code;

  /// 状态描述
  private final String description;

  DisambiguationStatus(String code, String description) {
    this.code = code;
    this.description = description;
  }

  /// 从代码值解析枚举（不区分大小写）。
  ///
  /// @param value 代码值（如 "PENDING", "matched"）
  /// @return 对应的枚举值
  /// @throws IllegalArgumentException 如果代码值无效
  public static DisambiguationStatus fromCode(String value) {
    Assert.notBlank(value, "消歧状态代码不能为空");
    String normalized = value.trim().toUpperCase();
    for (DisambiguationStatus status : values()) {
      if (status.code.equals(normalized)) {
        return status;
      }
    }
    throw new IllegalArgumentException("未知的消歧状态：" + value);
  }

  /// 尝试从代码值解析枚举，如果无法识别则返回 null。
  ///
  /// @param value 代码值
  /// @return 对应的枚举值，无法识别则返回 null
  public static DisambiguationStatus fromCodeOrNull(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    String normalized = value.trim().toUpperCase();
    for (DisambiguationStatus status : values()) {
      if (status.code.equals(normalized)) {
        return status;
      }
    }
    return null;
  }

  /// 判断是否为待消歧状态。
  ///
  /// @return true 如果为待消歧状态
  public boolean isPending() {
    return this == PENDING;
  }

  /// 判断是否为已匹配状态。
  ///
  /// @return true 如果为已匹配状态
  public boolean isMatched() {
    return this == MATCHED;
  }

  /// 判断是否为无法匹配状态。
  ///
  /// @return true 如果为无法匹配状态
  public boolean isUnmatched() {
    return this == UNMATCHED;
  }

  /// 判断是否为有歧义状态。
  ///
  /// @return true 如果为有歧义状态
  public boolean isAmbiguous() {
    return this == AMBIGUOUS;
  }

  /// 判断是否需要人工处理（无法匹配或有歧义）。
  ///
  /// @return true 如果需要人工处理
  public boolean needsManualReview() {
    return this == UNMATCHED || this == AMBIGUOUS;
  }

  /// 判断是否为终态（不需要再次消歧）。
  ///
  /// @return true 如果为终态
  public boolean isTerminal() {
    return this == MATCHED || this == UNMATCHED;
  }
}
