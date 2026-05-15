package dev.linqibin.patra.catalog.domain.model.enums;

import cn.hutool.core.lang.Assert;
import lombok.Getter;

/// 索引处理方式枚举。
///
/// 字段映射：cat_venue_indexing_history.indexing_treatment
///
/// 定义 MEDLINE 对期刊文章的索引处理方式，来源于 NLM LSIOU 的 IndexingTreatment 属性。
///
/// 处理方式说明：
///
/// | 方式 | 说明 |
/// |------|------|
/// | FULL | 全文索引 - 期刊的所有文章都被索引 |
/// | SELECTIVE | 选择性索引 - 只有部分文章被索引 |
/// | UNKNOWN | 未知 - 索引方式未指定 |
/// | REFERENCED_IN | 被引用 - 在其他已索引期刊的引用中出现 |
/// | REFERENCED_IN_NO_DETAILS | 被引用（无详情） - 引用信息不完整 |
///
/// 对于科研人员的意义：
///
/// - **FULL**：该期刊的所有文章都可在 PubMed 中检索到
/// - **SELECTIVE**：只有经过筛选的高质量文章才会被索引，可能遗漏部分内容
///
/// 使用示例：
///
/// ```java
/// IndexingTreatment treatment = IndexingTreatment.fromCode("FULL");
/// if (treatment.isFullIndexing()) {
///     // 全文索引处理
/// }
/// ```
///
/// @author linqibin
/// @since 0.1.0
@Getter
public enum IndexingTreatment {

  /// 全文索引（期刊的所有文章都被索引）
  FULL("FULL", "全文索引"),

  /// 选择性索引（只有部分文章被索引）
  SELECTIVE("SELECTIVE", "选择性索引"),

  /// 未知或未指定
  UNKNOWN("UNKNOWN", "未知"),

  /// 被引用（在其他已索引期刊的引用中出现）
  REFERENCED_IN("REFERENCED_IN", "被引用"),

  /// 被引用但无详情（引用信息不完整）
  REFERENCED_IN_NO_DETAILS("REFERENCED_IN_NO_DETAILS", "被引用（无详情）");

  /// 数据库存储的代码值
  private final String code;

  /// 描述文本
  private final String description;

  IndexingTreatment(String code, String description) {
    this.code = code;
    this.description = description;
  }

  /// 从代码值解析枚举（不区分大小写）。
  ///
  /// @param value 代码值
  /// @return 对应的枚举值
  /// @throws IllegalArgumentException 如果代码值无效
  public static IndexingTreatment fromCode(String value) {
    Assert.notBlank(value, "索引处理方式代码不能为空");
    String normalized = value.trim().toUpperCase();
    for (IndexingTreatment treatment : values()) {
      if (treatment.code.equals(normalized)) {
        return treatment;
      }
    }
    throw new IllegalArgumentException("未知的索引处理方式：" + value);
  }

  /// 尝试从代码值解析枚举，如果无法识别则返回 null。
  ///
  /// @param value 代码值
  /// @return 对应的枚举值，无法识别则返回 null
  public static IndexingTreatment fromCodeOrNull(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    String normalized = value.trim().toUpperCase();
    for (IndexingTreatment treatment : values()) {
      if (treatment.code.equals(normalized)) {
        return treatment;
      }
    }
    return null;
  }

  /// 从 LSIOU IndexingTreatment 属性值转换。
  ///
  /// @param lsiouValue LSIOU 中的值（如 "Full"、"Selective"、"Unknown" 等）
  /// @return 对应的枚举值，无法识别则返回 null
  public static IndexingTreatment fromLsiouValue(String lsiouValue) {
    if (lsiouValue == null || lsiouValue.isBlank()) {
      return null;
    }
    return switch (lsiouValue.trim().toLowerCase()) {
      case "full" -> FULL;
      case "selective" -> SELECTIVE;
      case "unknown" -> UNKNOWN;
      case "referencedin" -> REFERENCED_IN;
      case "referencedinnodetails" -> REFERENCED_IN_NO_DETAILS;
      default -> null;
    };
  }

  /// 判断是否为全文索引。
  ///
  /// @return true 如果为 FULL
  public boolean isFullIndexing() {
    return this == FULL;
  }

  /// 判断是否为选择性索引。
  ///
  /// @return true 如果为 SELECTIVE
  public boolean isSelectiveIndexing() {
    return this == SELECTIVE;
  }
}
