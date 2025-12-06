package com.patra.catalog.domain.model.enums;

import cn.hutool.core.lang.Assert;
import lombok.Getter;

/// 期刊关联类型枚举。
///
/// 字段映射：cat_venue_relation.relation_type
///
/// 定义期刊之间的演变关系，主要来源于 NLM Serfile 的 TitleRelated 字段。
///
/// 关联类型说明：
///
/// | 类型 | 说明 | 示例 |
/// |------|------|------|
/// | PRECEDING | 前刊 | A 是 B 的前刊（B 继承自 A） |
/// | SUCCEEDING | 后刊 | A 是 B 的后刊（A 继承自 B） |
/// | ABSORBED | 被吸收 | A 被 B 吸收 |
/// | ABSORBED_BY | 吸收了 | A 吸收了 B |
/// | MERGED | 合并 | A 与其他期刊合并 |
/// | SPLIT_FROM | 分拆自 | A 从 B 分拆而来 |
/// | CONTINUED_BY | 被延续为 | A 被 B 延续 |
/// | CONTINUES | 延续自 | A 延续自 B |
///
/// 使用示例：
///
/// ```java
/// VenueRelationType type = VenueRelationType.fromCode("PRECEDING");
/// if (type.isPredecessor()) {
///     // 处理前刊关系
/// }
/// ```
///
/// @author linqibin
/// @since 0.1.0
@Getter
public enum VenueRelationType {

  /// 前刊（本刊继承自该刊）
  PRECEDING("PRECEDING", "前刊"),

  /// 后刊（本刊被该刊继承）
  SUCCEEDING("SUCCEEDING", "后刊"),

  /// 被吸收（本刊被该刊吸收）
  ABSORBED("ABSORBED", "被吸收"),

  /// 吸收了（本刊吸收了该刊）
  ABSORBED_BY("ABSORBED_BY", "吸收了"),

  /// 合并（本刊与该刊合并）
  MERGED("MERGED", "合并"),

  /// 分拆自（本刊从该刊分拆而来）
  SPLIT_FROM("SPLIT_FROM", "分拆自"),

  /// 被延续为（本刊被该刊延续）
  CONTINUED_BY("CONTINUED_BY", "被延续为"),

  /// 延续自（本刊延续自该刊）
  CONTINUES("CONTINUES", "延续自");

  /// 数据库存储的代码值
  private final String code;

  /// 中文描述
  private final String description;

  VenueRelationType(String code, String description) {
    this.code = code;
    this.description = description;
  }

  /// 从代码值解析枚举（不区分大小写）。
  ///
  /// @param value 代码值
  /// @return 对应的枚举值
  /// @throws IllegalArgumentException 如果代码值无效
  public static VenueRelationType fromCode(String value) {
    Assert.notBlank(value, "关联类型代码不能为空");
    String normalized = value.trim().toUpperCase();
    for (VenueRelationType type : values()) {
      if (type.code.equals(normalized)) {
        return type;
      }
    }
    throw new IllegalArgumentException("未知的关联类型：" + value);
  }

  /// 尝试从代码值解析枚举，如果无法识别则返回 null。
  ///
  /// @param value 代码值
  /// @return 对应的枚举值，无法识别则返回 null
  public static VenueRelationType fromCodeOrNull(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    String normalized = value.trim().toUpperCase();
    for (VenueRelationType type : values()) {
      if (type.code.equals(normalized)) {
        return type;
      }
    }
    return null;
  }

  /// 从 Serfile TitleType 属性值转换。
  ///
  /// Serfile 中的 TitleType 属性值与本枚举的映射关系：
  /// - Preceding → PRECEDING
  /// - Succeeding → SUCCEEDING
  /// - Absorbed → ABSORBED
  /// - AbsorbedBy → ABSORBED_BY
  /// - Merged → MERGED
  /// - SplitFrom → SPLIT_FROM
  /// - ContinuedBy → CONTINUED_BY
  /// - Continues → CONTINUES
  ///
  /// @param titleType Serfile 中的 TitleType 值
  /// @return 对应的枚举值，无法识别则返回 null
  public static VenueRelationType fromSerfileTitleType(String titleType) {
    if (titleType == null || titleType.isBlank()) {
      return null;
    }
    return switch (titleType.trim()) {
      case "Preceding" -> PRECEDING;
      case "Succeeding" -> SUCCEEDING;
      case "Absorbed" -> ABSORBED;
      case "AbsorbedBy" -> ABSORBED_BY;
      case "Merged" -> MERGED;
      case "SplitFrom" -> SPLIT_FROM;
      case "ContinuedBy" -> CONTINUED_BY;
      case "Continues" -> CONTINUES;
      default -> null;
    };
  }

  /// 判断是否为前驱关系（本刊来源于其他刊）。
  ///
  /// @return true 如果为 PRECEDING、CONTINUES 或 SPLIT_FROM
  public boolean isPredecessor() {
    return this == PRECEDING || this == CONTINUES || this == SPLIT_FROM;
  }

  /// 判断是否为后继关系（本刊演变为其他刊）。
  ///
  /// @return true 如果为 SUCCEEDING、CONTINUED_BY 或 ABSORBED
  public boolean isSuccessor() {
    return this == SUCCEEDING || this == CONTINUED_BY || this == ABSORBED;
  }

  /// 判断是否为合并相关关系。
  ///
  /// @return true 如果为 MERGED、ABSORBED 或 ABSORBED_BY
  public boolean isMergeRelated() {
    return this == MERGED || this == ABSORBED || this == ABSORBED_BY;
  }

  /// 获取逆向关系类型。
  ///
  /// @return 逆向关系类型，如果没有明确的逆向关系则返回 null
  public VenueRelationType getInverse() {
    return switch (this) {
      case PRECEDING -> SUCCEEDING;
      case SUCCEEDING -> PRECEDING;
      case ABSORBED -> ABSORBED_BY;
      case ABSORBED_BY -> ABSORBED;
      case CONTINUES -> CONTINUED_BY;
      case CONTINUED_BY -> CONTINUES;
      case MERGED, SPLIT_FROM -> null;
    };
  }
}
