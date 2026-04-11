package com.patra.catalog.domain.model.enums;

import cn.hutool.core.lang.Assert;
import lombok.Getter;

/// CAS 中科院期刊预警级别枚举。
///
/// 字段映射：`cat_venue_cas_warning.warning_level`。
///
/// **三级预警体系**（对应 LetPub 页面展示的中文标签）：
///
/// - **HIGH** - 高风险（中文："高"）
/// - **MEDIUM** - 中风险（中文："中"）
/// - **LOW** - 低风险（中文："低"）
///
/// 仅当 `inWarningList = true` 时此字段可能有值；非预警期刊应为 null。
///
/// **持久化策略**：通过 `AttributeConverter` 以小写英文代码持久化（`high`/`medium`/`low`），
/// 与 `PublicationIdentifierType` 等既有枚举一致，避免数据库存中文引发编码/排序问题。
///
/// @author linqibin
/// @since 0.1.0
@Getter
public enum CasWarningLevel {

  /// 高风险预警
  HIGH("high", "高"),

  /// 中风险预警
  MEDIUM("medium", "中"),

  /// 低风险预警
  LOW("low", "低");

  /// 数据库存储的代码值（小写英文）
  private final String code;

  /// LetPub 页面原始中文标签（用于 Parser 识别 + 页面还原展示）
  private final String chineseLabel;

  CasWarningLevel(String code, String chineseLabel) {
    this.code = code;
    this.chineseLabel = chineseLabel;
  }

  /// 从数据库代码值解析枚举（不区分大小写）。
  ///
  /// @param value 代码值（如 "high"、"HIGH"）
  /// @return 对应的枚举值
  /// @throws IllegalArgumentException 如果代码值无效
  public static CasWarningLevel fromCode(String value) {
    Assert.notBlank(value, "CAS 预警级别代码不能为空");
    String normalized = value.trim().toLowerCase();
    for (CasWarningLevel level : values()) {
      if (level.code.equals(normalized)) {
        return level;
      }
    }
    throw new IllegalArgumentException("未知的 CAS 预警级别代码：" + value);
  }

  /// 从 LetPub 页面的中文标签解析枚举（如从 `"高风险"` 或 `"高级"` 中提取 `"高"`）。
  ///
  /// @param label 中文标签（应已从 "X风险"/"X级" 中剥离为单字 "高"/"中"/"低"）
  /// @return 对应的枚举值
  /// @throws IllegalArgumentException 如果标签无效
  public static CasWarningLevel fromChineseLabel(String label) {
    Assert.notBlank(label, "CAS 预警级别中文标签不能为空");
    String normalized = label.trim();
    for (CasWarningLevel level : values()) {
      if (level.chineseLabel.equals(normalized)) {
        return level;
      }
    }
    throw new IllegalArgumentException("未知的 CAS 预警级别中文标签：" + label);
  }
}
