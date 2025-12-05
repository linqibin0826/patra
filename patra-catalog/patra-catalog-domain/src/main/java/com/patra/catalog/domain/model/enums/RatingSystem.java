package com.patra.catalog.domain.model.enums;

import cn.hutool.core.lang.Assert;
import lombok.Getter;

/// 期刊评价体系枚举。
///
/// 字段映射：cat_venue_rating.rating_system
///
/// 支持的评价体系：
///
/// - **JCR**：Journal Citation Reports（科睿唯安期刊引证报告）
/// - **CAS**：中国科学院期刊分区表（中科院分区）
/// - **SCOPUS**：Scopus CiteScore（Elsevier 的引用指标）
///
/// 评价体系特点对比：
///
/// | 体系 | 核心指标 | 分区方式 | 数据来源 |
/// |------|----------|----------|----------|
/// | JCR | Impact Factor | Q1-Q4 | Web of Science |
/// | CAS | 复合影响因子 | 1-4区 + Top | 中科院文献情报中心 |
/// | SCOPUS | CiteScore | Q1-Q4 | Scopus |
///
/// 设计说明：
///
/// - 各评价体系的 rating_data JSON 结构不同，需根据 rating_system 进行解析
/// - 分区等级需要标准化处理（Q1 ≈ 1区）
///
/// 使用示例：
///
/// ```java
/// RatingSystem system = RatingSystem.fromCode("JCR");
/// if (system.isJcr()) {
///     // 解析 JCR 特有字段
/// }
///
/// // 获取优先级进行排序
/// int priority = system.getPriority();
/// ```
///
/// @author linqibin
/// @since 0.1.0
@Getter
public enum RatingSystem {

  /// Journal Citation Reports（科睿唯安期刊引证报告）
  JCR("JCR", "Journal Citation Reports", 1),

  /// 中国科学院期刊分区表（中科院分区）
  CAS("CAS", "中科院分区", 2),

  /// Scopus CiteScore（Elsevier 引用指标）
  SCOPUS("SCOPUS", "Scopus CiteScore", 3);

  /// 数据库存储的代码值
  private final String code;

  /// 描述文本
  private final String description;

  /// 优先级（数字越小优先级越高，用于选择最新评级快照）
  private final int priority;

  RatingSystem(String code, String description, int priority) {
    this.code = code;
    this.description = description;
    this.priority = priority;
  }

  /// 从代码值解析枚举（不区分大小写）。
  ///
  /// @param value 代码值（如 "JCR", "cas", "SCOPUS"）
  /// @return 对应的枚举值
  /// @throws IllegalArgumentException 如果代码值无效
  public static RatingSystem fromCode(String value) {
    Assert.notBlank(value, "评价体系代码不能为空");
    String normalized = value.trim().toUpperCase();
    for (RatingSystem system : values()) {
      if (system.code.equals(normalized)) {
        return system;
      }
    }
    throw new IllegalArgumentException("未知的评价体系：" + value);
  }

  /// 尝试从代码值解析枚举，如果无法识别则返回 null。
  ///
  /// @param value 代码值
  /// @return 对应的枚举值，无法识别则返回 null
  public static RatingSystem fromCodeOrNull(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    String normalized = value.trim().toUpperCase();
    for (RatingSystem system : values()) {
      if (system.code.equals(normalized)) {
        return system;
      }
    }
    return null;
  }

  /// 判断是否为 JCR 评价体系。
  ///
  /// @return true 如果为 JCR
  public boolean isJcr() {
    return this == JCR;
  }

  /// 判断是否为中科院分区。
  ///
  /// @return true 如果为中科院分区
  public boolean isCas() {
    return this == CAS;
  }

  /// 判断是否为 Scopus 评价体系。
  ///
  /// @return true 如果为 Scopus
  public boolean isScopus() {
    return this == SCOPUS;
  }

  /// 判断是否为国际评价体系（JCR 或 Scopus）。
  ///
  /// @return true 如果为国际评价体系
  public boolean isInternational() {
    return this == JCR || this == SCOPUS;
  }

  /// 判断是否为国内评价体系（中科院分区）。
  ///
  /// @return true 如果为国内评价体系
  public boolean isDomestic() {
    return this == CAS;
  }

  /// 获取该评价体系的顶级分区标识。
  ///
  /// @return 顶级分区标识（Q1 或 1区）
  public String getTopQuartileLabel() {
    return switch (this) {
      case JCR, SCOPUS -> "Q1";
      case CAS -> "1区";
    };
  }

  /// 比较两个评价体系的优先级。
  ///
  /// @param other 另一个评价体系
  /// @return 负数表示 this 优先级更高，正数表示 other 优先级更高，0 表示相同
  public int comparePriority(RatingSystem other) {
    return Integer.compare(this.priority, other.priority);
  }
}
