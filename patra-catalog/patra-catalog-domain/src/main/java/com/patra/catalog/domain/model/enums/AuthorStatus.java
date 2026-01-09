package com.patra.catalog.domain.model.enums;

import cn.hutool.core.lang.Assert;
import lombok.Getter;

/// 作者状态枚举。
///
/// 字段映射：cat_author.status
///
/// 支持的状态：
///
/// - **ACTIVE**：活跃状态，正常的作者记录
/// - **MERGED**：已合并状态，作者已被合并到另一个作者记录
/// - **INACTIVE**：已停用状态，作者记录已被标记为无效
///
/// 状态转换规则：
///
/// ```
/// ACTIVE ──────────────────┬──────────────────> MERGED
///    │                     │
///    │                     │
///    └─────────────────────┴──────────────────> INACTIVE
/// ```
///
/// 使用示例：
///
/// ```java
/// AuthorStatus status = AuthorStatus.fromCode("ACTIVE");
/// if (status.isActive()) {
///     // 处理活跃作者
/// }
///
/// // 检查是否可编辑
/// if (status.isEditable()) {
///     // 允许编辑
/// }
/// ```
///
/// @author linqibin
/// @since 0.1.0
@Getter
public enum AuthorStatus {

  /// 活跃状态（正常使用）
  ACTIVE("ACTIVE", "活跃"),

  /// 已合并状态（合并到其他作者）
  MERGED("MERGED", "已合并"),

  /// 已停用状态（标记为无效）
  INACTIVE("INACTIVE", "已停用");

  /// 数据库存储的代码值
  private final String code;

  /// 状态描述
  private final String description;

  AuthorStatus(String code, String description) {
    this.code = code;
    this.description = description;
  }

  /// 从代码值解析枚举（不区分大小写）。
  ///
  /// @param value 代码值（如 "ACTIVE", "merged"）
  /// @return 对应的枚举值
  /// @throws IllegalArgumentException 如果代码值无效
  public static AuthorStatus fromCode(String value) {
    Assert.notBlank(value, "作者状态代码不能为空");
    String normalized = value.trim().toUpperCase();
    for (AuthorStatus status : values()) {
      if (status.code.equals(normalized)) {
        return status;
      }
    }
    throw new IllegalArgumentException("未知的作者状态：" + value);
  }

  /// 尝试从代码值解析枚举，如果无法识别则返回 null。
  ///
  /// @param value 代码值
  /// @return 对应的枚举值，无法识别则返回 null
  public static AuthorStatus fromCodeOrNull(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    String normalized = value.trim().toUpperCase();
    for (AuthorStatus status : values()) {
      if (status.code.equals(normalized)) {
        return status;
      }
    }
    return null;
  }

  /// 判断是否为活跃状态。
  ///
  /// @return true 如果为活跃状态
  public boolean isActive() {
    return this == ACTIVE;
  }

  /// 判断是否为已合并状态。
  ///
  /// @return true 如果为已合并状态
  public boolean isMerged() {
    return this == MERGED;
  }

  /// 判断是否为已停用状态。
  ///
  /// @return true 如果为已停用状态
  public boolean isInactive() {
    return this == INACTIVE;
  }

  /// 判断作者是否可编辑（仅活跃状态可编辑）。
  ///
  /// @return true 如果作者可编辑
  public boolean isEditable() {
    return this == ACTIVE;
  }

  /// 判断作者是否可见（活跃和已合并状态可见）。
  ///
  /// @return true 如果作者可见
  public boolean isVisible() {
    return this == ACTIVE || this == MERGED;
  }

  /// 判断是否为终态（不可再变更的状态）。
  ///
  /// @return true 如果为终态
  public boolean isTerminal() {
    return this == MERGED || this == INACTIVE;
  }
}
