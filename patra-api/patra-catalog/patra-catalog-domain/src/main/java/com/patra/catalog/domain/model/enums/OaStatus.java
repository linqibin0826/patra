package com.patra.catalog.domain.model.enums;

import cn.hutool.core.lang.Assert;
import lombok.Getter;

/// 开放获取状态枚举（字典：cat_oa_status）。
///
/// 字段映射：cat_publication.oa_status → gold/green/hybrid/bronze/closed
///
/// 优先级排序（从高到低）：
///
/// - **GOLD** - 黄金 OA（出版商开放，最佳）
///   - **GREEN** - 绿色 OA（机构仓储，次佳）
///   - **HYBRID** - 混合 OA（部分开放）
///   - **BRONZE** - 青铜 OA（免费但无许可证）
///   - **CLOSED** - 封闭获取
///
/// 业务规则：
///
/// - 一个文献可能有多个 OA 位置，选择优先级最高的状态
///   - GOLD > GREEN > HYBRID > BRONZE > CLOSED
///   - 用于快速筛选 OA 文献（WHERE oa_status != 'closed'）
///
/// 使用示例：
///
/// ```java
/// OaStatus status1 = OaStatus.fromCode("gold");
/// if (status1.isOpenAccess()) {
///     System.out.println("这是开放获取文献");
///
/// OaStatus status2 = OaStatus.GREEN;
/// if (status2.isBetterThan(OaStatus.BRONZE)) {
///     System.out.println("绿色 OA 优于青铜 OA");
/// ```
///
/// @author linqibin
/// @since 0.1.0
@Getter
public enum OaStatus {

  /// 黄金 OA - 出版商完全开放，优先级最高（100）
  GOLD("gold", "Gold Open Access", 100),

  /// 绿色 OA - 机构仓储/作者存档，优先级次高（80）
  GREEN("green", "Green Open Access", 80),

  /// 混合 OA - 订阅期刊部分文章开放，优先级中等（70）
  HYBRID("hybrid", "Hybrid Open Access", 70),

  /// 青铜 OA - 免费但无明确开放许可证，优先级较低（50）
  BRONZE("bronze", "Bronze Open Access", 50),

  /// 封闭获取 - 需付费或订阅，优先级最低（0）
  CLOSED("closed", "Closed Access", 0);

  /// 数据库存储的代码值（小写）
  private final String code;

  /// 描述文本
  private final String description;

  /// 优先级分数（数字越大优先级越高）
  private final int priority;

  OaStatus(String code, String description, int priority) {
    this.code = code;
    this.description = description;
    this.priority = priority;
  }

  /// 从代码值解析枚举（不区分大小写）。
  ///
  /// @param value 代码值（如 "gold", "GREEN", "Hybrid"）
  /// @return 对应的枚举值
  /// @throws IllegalArgumentException 如果代码值无效
  public static OaStatus fromCode(String value) {
    Assert.notBlank(value, "OA 状态代码不能为空");
    String normalized = value.trim().toLowerCase();
    for (OaStatus status : values()) {
      if (status.code.equals(normalized)) {
        return status;
      }
    }
    throw new IllegalArgumentException("未知的 OA 状态：" + value);
  }

  /// 判断是否为开放获取（非封闭）。
  ///
  /// @return true 如果为任何形式的 OA
  public boolean isOpenAccess() {
    return this != CLOSED;
  }

  /// 判断是否优于另一 OA 状态。
  ///
  /// 用于选择最佳 OA 位置。
  ///
  /// @param other 另一 OA 状态
  /// @return true 如果当前状态优先级更高
  public boolean isBetterThan(OaStatus other) {
    return this.priority > other.priority;
  }

  /// 从两个状态中选择优先级更高的。
  ///
  /// @param status1 状态1
  /// @param status2 状态2
  /// @return 优先级更高的状态
  public static OaStatus selectBetter(OaStatus status1, OaStatus status2) {
    if (status1 == null) return status2;
    if (status2 == null) return status1;
    return status1.isBetterThan(status2) ? status1 : status2;
  }

  /// 判断是否为黄金 OA。
  ///
  /// @return true 如果为黄金 OA
  public boolean isGold() {
    return this == GOLD;
  }

  /// 判断是否为绿色 OA。
  ///
  /// @return true 如果为绿色 OA
  public boolean isGreen() {
    return this == GREEN;
  }
}
