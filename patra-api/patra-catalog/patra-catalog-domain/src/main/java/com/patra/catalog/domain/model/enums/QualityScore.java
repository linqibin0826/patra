package com.patra.catalog.domain.model.enums;

import cn.hutool.core.lang.Assert;
import lombok.Getter;

/// 质量评分枚举（等级制）。
///
/// 字段映射：cat_publication_metadata.quality_score, cat_publication_metadata.completeness_score
///
/// 采用学术评级制（A-F），用于评估数据质量和完整性：
///
/// - **A** - 优秀（90-100%）- 数据完整、准确、无错误
/// - **B** - 良好（80-89%）- 轻微缺陷，不影响使用
/// - **C** - 合格（70-79%）- 存在缺陷，但可接受
/// - **D** - 较差（60-69%）- 明显缺陷，需要改进
/// - **F** - 不合格（<60%）- 严重问题，不可使用
///
/// @author linqibin
/// @since 0.1.0
@Getter
public enum QualityScore {

  /// 优秀
  A("A", "Excellent", 5),

  /// 良好
  B("B", "Good", 4),

  /// 合格
  C("C", "Acceptable", 3),

  /// 较差
  D("D", "Poor", 2),

  /// 不合格
  F("F", "Failed", 1);

  /// 数据库存储的代码值（大写字母）
  private final String code;

  /// 描述文本
  private final String description;

  /// 评分等级（数值越大越好）
  private final int level;

  QualityScore(String code, String description, int level) {
    this.code = code;
    this.description = description;
    this.level = level;
  }

  /// 从代码值解析枚举（不区分大小写）。
  ///
  /// @param value 代码值（如 "A", "b", "C"）
  /// @return 对应的枚举值
  /// @throws IllegalArgumentException 如果代码值无效
  public static QualityScore fromCode(String value) {
    Assert.notBlank(value, "质量评分代码不能为空");
    String normalized = value.trim().toUpperCase();
    for (QualityScore score : values()) {
      if (score.code.equals(normalized)) {
        return score;
      }
    }
    throw new IllegalArgumentException("未知的质量评分：" + value);
  }

  /// 判断是否合格（C 及以上）。
  ///
  /// @return true 如果为 A、B 或 C
  public boolean isPassing() {
    return this.level >= C.level;
  }

  /// 判断是否优于指定评分。
  ///
  /// @param other 比较的评分
  /// @return true 如果当前评分优于指定评分
  public boolean isBetterThan(QualityScore other) {
    return this.level > other.level;
  }
}
