package dev.linqibin.patra.catalog.domain.model.enums;

import cn.hutool.core.lang.Assert;
import lombok.Getter;

/// 质量级别枚举（描述性）。
///
/// 字段映射：cat_publication_alternative_abstract.quality_level
///
/// 用于描述翻译摘要等内容的质量级别：
///
/// - **EXCELLENT** - 优秀（专业级，可直接使用）
/// - **GOOD** - 良好（质量较高，偶有小问题）
/// - **FAIR** - 一般（可用，但需要校对）
/// - **POOR** - 较差（质量较低，仅供参考）
///
/// @author linqibin
/// @since 0.1.0
@Getter
public enum QualityLevel {

  /// 优秀
  EXCELLENT("excellent", "Excellent Quality", 4),

  /// 良好
  GOOD("good", "Good Quality", 3),

  /// 一般
  FAIR("fair", "Fair Quality", 2),

  /// 较差
  POOR("poor", "Poor Quality", 1);

  /// 数据库存储的代码值（小写）
  private final String code;

  /// 描述文本
  private final String description;

  /// 质量等级（数值越大越好）
  private final int level;

  QualityLevel(String code, String description, int level) {
    this.code = code;
    this.description = description;
    this.level = level;
  }

  /// 从代码值解析枚举（不区分大小写）。
  ///
  /// @param value 代码值（如 "excellent", "GOOD"）
  /// @return 对应的枚举值
  /// @throws IllegalArgumentException 如果代码值无效
  public static QualityLevel fromCode(String value) {
    Assert.notBlank(value, "质量级别代码不能为空");
    String normalized = value.trim().toLowerCase();
    for (QualityLevel level : values()) {
      if (level.code.equals(normalized)) {
        return level;
      }
    }
    throw new IllegalArgumentException("未知的质量级别：" + value);
  }

  /// 判断是否达到可接受质量（FAIR 及以上）。
  ///
  /// @return true 如果为 EXCELLENT、GOOD 或 FAIR
  public boolean isAcceptable() {
    return this.level >= FAIR.level;
  }

  /// 判断是否优于指定级别。
  ///
  /// @param other 比较的级别
  /// @return true 如果当前级别优于指定级别
  public boolean isBetterThan(QualityLevel other) {
    return this.level > other.level;
  }
}
