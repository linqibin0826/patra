package com.patra.catalog.domain.model.enums;

import cn.hutool.core.lang.Assert;
import lombok.Getter;

/// 机构名称类型枚举。
///
/// 字段映射：cat_organization_name.types (JSON 数组)
///
/// 基于 ROR (Research Organization Registry) Schema v2.0 定义的名称类型。
/// 每个名称可以同时具有多个类型（如 ror_display + label）。
///
/// **名称类型说明**：
///
/// | 类型 | 说明 | 示例 |
/// |------|------|------|
/// | ROR_DISPLAY | ROR 显示名 | 在 ROR 搜索结果中显示的名称（每条记录有且仅有一个） |
/// | LABEL | 官方标签 | 机构的官方名称，可能有多语言版本 |
/// | ALIAS | 别名 | 机构的非官方名称、历史名称或翻译名称 |
/// | ACRONYM | 缩写 | 机构名称的首字母缩写或简称 |
///
/// **使用示例**：
///
/// ```java
/// // 一个名称可能同时是 ror_display 和 label
/// Set<OrganizationNameType> types = Set.of(
///     OrganizationNameType.ROR_DISPLAY,
///     OrganizationNameType.LABEL
/// );
///
/// // 检查是否为官方名称
/// boolean isOfficial = types.stream().anyMatch(OrganizationNameType::isOfficialName);
/// ```
///
/// @author linqibin
/// @since 0.1.0
/// @see <a href="https://ror.readme.io/docs/fields#names">ROR Names Field</a>
@Getter
public enum OrganizationNameType {

  /// ROR 显示名（每条记录有且仅有一个）
  ROR_DISPLAY("ror_display", "ROR 显示名"),

  /// 官方标签（机构的官方名称）
  LABEL("label", "官方标签"),

  /// 别名（非官方名称、历史名称、翻译名称等）
  ALIAS("alias", "别名"),

  /// 缩写（首字母缩写或简称）
  ACRONYM("acronym", "缩写");

  /// 数据库存储的代码值（与 ROR 一致，小写）
  private final String code;

  /// 中文描述
  private final String description;

  OrganizationNameType(String code, String description) {
    this.code = code;
    this.description = description;
  }

  /// 从代码值解析枚举（不区分大小写）。
  ///
  /// @param value 代码值（如 "ror_display", "LABEL"）
  /// @return 对应的枚举值
  /// @throws IllegalArgumentException 如果代码值为空或无效
  public static OrganizationNameType fromCode(String value) {
    Assert.notBlank(value, "名称类型代码不能为空");
    String normalized = value.trim().toLowerCase();
    for (OrganizationNameType type : values()) {
      if (type.code.equals(normalized)) {
        return type;
      }
    }
    throw new IllegalArgumentException("未知的名称类型：" + value);
  }

  /// 尝试从代码值解析枚举，如果无法识别则返回 null。
  ///
  /// @param value 代码值
  /// @return 对应的枚举值，无法识别则返回 null
  public static OrganizationNameType fromCodeOrNull(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    String normalized = value.trim().toLowerCase();
    for (OrganizationNameType type : values()) {
      if (type.code.equals(normalized)) {
        return type;
      }
    }
    return null;
  }

  /// 判断是否为 ROR 显示名。
  ///
  /// @return true 如果为 ROR_DISPLAY
  public boolean isDisplayName() {
    return this == ROR_DISPLAY;
  }

  /// 判断是否为官方名称（ROR 显示名或官方标签）。
  ///
  /// @return true 如果为 ROR_DISPLAY 或 LABEL
  public boolean isOfficialName() {
    return this == ROR_DISPLAY || this == LABEL;
  }

  /// 判断是否为缩写类型。
  ///
  /// @return true 如果为 ACRONYM
  public boolean isAbbreviation() {
    return this == ACRONYM;
  }
}
