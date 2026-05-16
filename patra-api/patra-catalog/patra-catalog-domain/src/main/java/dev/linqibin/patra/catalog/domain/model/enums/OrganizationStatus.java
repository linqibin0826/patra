package dev.linqibin.patra.catalog.domain.model.enums;

import cn.hutool.core.lang.Assert;
import lombok.Getter;

/// 机构状态枚举。
///
/// 字段映射：cat_organization.status
///
/// 基于 ROR (Research Organization Registry) Schema v2.0 定义的机构状态。
///
/// **状态说明**：
///
/// | 状态 | 说明 | 业务含义 |
/// |------|------|---------|
/// | ACTIVE | 活跃 | 机构正常运营，数据有效 |
/// | INACTIVE | 不活跃 | 机构已停止运营，但记录保留 |
/// | WITHDRAWN | 已撤回 | 记录已从 ROR 撤回（如重复、错误） |
///
/// **使用示例**：
///
/// ```java
/// OrganizationStatus status = OrganizationStatus.fromCode("active");
/// if (status.isAvailable()) {
///     // 机构可用（未被撤回）
/// }
/// ```
///
/// @author linqibin
/// @since 0.1.0
/// @see <a href="https://ror.readme.io/docs/data-structure">ROR Data Structure</a>
@Getter
public enum OrganizationStatus {

  /// 活跃状态（机构正常运营）
  ACTIVE("active", "活跃"),

  /// 不活跃状态（机构已停止运营）
  INACTIVE("inactive", "不活跃"),

  /// 已撤回状态（记录已从 ROR 撤回）
  WITHDRAWN("withdrawn", "已撤回");

  /// 数据库存储的代码值（与 ROR 一致，小写）
  private final String code;

  /// 中文描述
  private final String description;

  OrganizationStatus(String code, String description) {
    this.code = code;
    this.description = description;
  }

  /// 从代码值解析枚举（不区分大小写）。
  ///
  /// @param value 代码值（如 "active", "INACTIVE"）
  /// @return 对应的枚举值
  /// @throws IllegalArgumentException 如果代码值为空或无效
  public static OrganizationStatus fromCode(String value) {
    Assert.notBlank(value, "机构状态代码不能为空");
    String normalized = value.trim().toLowerCase();
    for (OrganizationStatus status : values()) {
      if (status.code.equals(normalized)) {
        return status;
      }
    }
    throw new IllegalArgumentException("未知的机构状态：" + value);
  }

  /// 尝试从代码值解析枚举，如果无法识别则返回 null。
  ///
  /// @param value 代码值
  /// @return 对应的枚举值，无法识别则返回 null
  public static OrganizationStatus fromCodeOrNull(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    String normalized = value.trim().toLowerCase();
    for (OrganizationStatus status : values()) {
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

  /// 判断是否为不活跃状态。
  ///
  /// @return true 如果为不活跃状态
  public boolean isInactive() {
    return this == INACTIVE;
  }

  /// 判断是否为已撤回状态。
  ///
  /// @return true 如果为已撤回状态
  public boolean isWithdrawn() {
    return this == WITHDRAWN;
  }

  /// 判断机构是否可用（未被撤回）。
  ///
  /// 活跃或不活跃的机构都算可用，只有撤回的机构不可用。
  ///
  /// @return true 如果机构可用
  public boolean isAvailable() {
    return this != WITHDRAWN;
  }
}
