package com.patra.catalog.domain.model.enums;

import cn.hutool.core.lang.Assert;
import lombok.Getter;

/// 机构关系类型枚举。
///
/// 字段映射：cat_organization_relation.relation_type
///
/// 基于 ROR (Research Organization Registry) Schema v2.0 定义的机构关系类型。
///
/// **关系类型说明**：
///
/// | 类型 | 说明 | 示例 |
/// |------|------|------|
/// | PARENT | 上级机构 | 学院 → 大学 |
/// | CHILD | 下级机构 | 大学 → 学院 |
/// | RELATED | 相关机构 | 合作机构、联盟成员 |
/// | SUCCESSOR | 后继机构 | 机构重组后的新机构 |
/// | PREDECESSOR | 前身机构 | 机构重组前的旧机构 |
///
/// **关系特点**：
///
/// - **层级关系**（PARENT/CHILD）：表示组织架构的上下级关系
/// - **时序关系**（SUCCESSOR/PREDECESSOR）：表示机构历史演变
/// - **平行关系**（RELATED）：表示没有明确层级或时序的关联
///
/// **使用示例**：
///
/// ```java
/// OrganizationRelationType type = OrganizationRelationType.fromCode("parent");
///
/// // 获取逆向关系（用于双向建立关联）
/// OrganizationRelationType inverse = type.getInverse(); // CHILD
/// ```
///
/// @author linqibin
/// @since 0.1.0
/// @see <a href="https://ror.readme.io/docs/relationships">ROR Relationships</a>
@Getter
public enum OrganizationRelationType {

  /// 上级机构
  PARENT("parent", "上级机构"),

  /// 下级机构
  CHILD("child", "下级机构"),

  /// 相关机构
  RELATED("related", "相关机构"),

  /// 后继机构
  SUCCESSOR("successor", "后继机构"),

  /// 前身机构
  PREDECESSOR("predecessor", "前身机构");

  /// 数据库存储的代码值（与 ROR 一致，小写）
  private final String code;

  /// 中文描述
  private final String description;

  OrganizationRelationType(String code, String description) {
    this.code = code;
    this.description = description;
  }

  /// 从代码值解析枚举（不区分大小写）。
  ///
  /// @param value 代码值（如 "parent", "CHILD"）
  /// @return 对应的枚举值
  /// @throws IllegalArgumentException 如果代码值为空或无效
  public static OrganizationRelationType fromCode(String value) {
    Assert.notBlank(value, "机构关系类型代码不能为空");
    String normalized = value.trim().toLowerCase();
    for (OrganizationRelationType type : values()) {
      if (type.code.equals(normalized)) {
        return type;
      }
    }
    throw new IllegalArgumentException("未知的机构关系类型：" + value);
  }

  /// 尝试从代码值解析枚举，如果无法识别则返回 null。
  ///
  /// @param value 代码值
  /// @return 对应的枚举值，无法识别则返回 null
  public static OrganizationRelationType fromCodeOrNull(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    String normalized = value.trim().toLowerCase();
    for (OrganizationRelationType type : values()) {
      if (type.code.equals(normalized)) {
        return type;
      }
    }
    return null;
  }

  /// 获取逆向关系类型。
  ///
  /// 用于双向建立关联，例如：A 的 PARENT 是 B → B 的 CHILD 是 A
  ///
  /// @return 逆向关系类型
  public OrganizationRelationType getInverse() {
    return switch (this) {
      case PARENT -> CHILD;
      case CHILD -> PARENT;
      case SUCCESSOR -> PREDECESSOR;
      case PREDECESSOR -> SUCCESSOR;
      case RELATED -> RELATED; // 对称关系
    };
  }

  /// 判断是否为层级关系（上下级）。
  ///
  /// @return true 如果为 PARENT 或 CHILD
  public boolean isHierarchical() {
    return this == PARENT || this == CHILD;
  }

  /// 判断是否为时序关系（前身/后继）。
  ///
  /// @return true 如果为 SUCCESSOR 或 PREDECESSOR
  public boolean isTemporal() {
    return this == SUCCESSOR || this == PREDECESSOR;
  }
}
