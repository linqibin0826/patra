package com.patra.catalog.domain.model.enums;

import cn.hutool.core.lang.Assert;
import java.math.BigDecimal;
import lombok.Getter;

/// 机构消歧方法枚举。
///
/// 字段映射：cat_publication_author_affiliation.disambiguation_method
///
/// 支持的方法（按置信度排序）：
///
/// - **ROR_ID**：基于 ROR ID 精确匹配，置信度 1.0
/// - **RINGGOLD**：基于 Ringgold ID 匹配，置信度 0.95
/// - **GRID**：基于 GRID ID 匹配（历史数据），置信度 0.85
/// - **NAME_MATCH**：基于名称模糊匹配，置信度 0.6-0.8
/// - **MANUAL**：人工消歧，置信度 1.0
///
/// 消歧优先级：
///
/// ```
/// ROR_ID (1.0) > RINGGOLD (0.95) > GRID (0.85) > NAME_MATCH (0.6-0.8)
/// ```
///
/// 使用示例：
///
/// ```java
/// DisambiguationMethod method = DisambiguationMethod.ROR_ID;
/// BigDecimal score = method.getDefaultScore();
/// ```
///
/// @author linqibin
/// @since 0.1.0
@Getter
public enum DisambiguationMethod {

  /// 基于 ROR ID 精确匹配
  ROR_ID("ROR_ID", "ROR ID 匹配", new BigDecimal("1.0000")),

  /// 基于 Ringgold ID 匹配
  RINGGOLD("RINGGOLD", "Ringgold ID 匹配", new BigDecimal("0.9500")),

  /// 基于 GRID ID 匹配（历史数据，GRID 已废弃）
  GRID("GRID", "GRID ID 匹配", new BigDecimal("0.8500")),

  /// 基于名称模糊匹配
  NAME_MATCH("NAME_MATCH", "名称匹配", new BigDecimal("0.7000")),

  /// 人工消歧
  MANUAL("MANUAL", "人工消歧", new BigDecimal("1.0000"));

  /// 数据库存储的代码值
  private final String code;

  /// 方法描述
  private final String description;

  /// 默认置信度评分
  private final BigDecimal defaultScore;

  DisambiguationMethod(String code, String description, BigDecimal defaultScore) {
    this.code = code;
    this.description = description;
    this.defaultScore = defaultScore;
  }

  /// 从代码值解析枚举（不区分大小写）。
  ///
  /// @param value 代码值（如 "ROR_ID", "ringgold"）
  /// @return 对应的枚举值
  /// @throws IllegalArgumentException 如果代码值无效
  public static DisambiguationMethod fromCode(String value) {
    Assert.notBlank(value, "消歧方法代码不能为空");
    String normalized = value.trim().toUpperCase();
    for (DisambiguationMethod method : values()) {
      if (method.code.equals(normalized)) {
        return method;
      }
    }
    throw new IllegalArgumentException("未知的消歧方法：" + value);
  }

  /// 尝试从代码值解析枚举，如果无法识别则返回 null。
  ///
  /// @param value 代码值
  /// @return 对应的枚举值，无法识别则返回 null
  public static DisambiguationMethod fromCodeOrNull(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    String normalized = value.trim().toUpperCase();
    for (DisambiguationMethod method : values()) {
      if (method.code.equals(normalized)) {
        return method;
      }
    }
    return null;
  }

  /// 判断是否基于标识符匹配（高置信度）。
  ///
  /// @return true 如果基于标识符匹配
  public boolean isIdentifierBased() {
    return this == ROR_ID || this == RINGGOLD || this == GRID;
  }

  /// 判断是否为自动匹配方法。
  ///
  /// @return true 如果为自动匹配
  public boolean isAutomatic() {
    return this != MANUAL;
  }

  /// 判断是否需要人工确认（低置信度方法）。
  ///
  /// @return true 如果需要人工确认
  public boolean needsConfirmation() {
    return this == NAME_MATCH;
  }
}
