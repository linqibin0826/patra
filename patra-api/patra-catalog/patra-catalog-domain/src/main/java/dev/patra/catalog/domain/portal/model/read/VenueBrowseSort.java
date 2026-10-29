package dev.patra.catalog.domain.portal.model.read;

import java.util.Locale;

/// 期刊浏览排序方式。
///
/// @author linqibin
/// @since 0.1.0
public enum VenueBrowseSort {

  /// 按影响因子降序（默认）。
  IMPACT_FACTOR,

  /// 按 CAS 分区升序（Q1 最优）。
  CAS_QUARTILE,

  /// 按期刊标题字母升序。
  TITLE,

  /// 按被引量降序。
  CITED_BY;

  /// 将外部字符串码解析为枚举值，大小写不敏感。
  ///
  /// 别名规则：
  /// - `casquartile` / `cas_quartile` → [CAS_QUARTILE]
  /// - `title` → [TITLE]
  /// - `citedby` / `cited_by` → [CITED_BY]
  /// - `impactfactor` / `impact_factor` → [IMPACT_FACTOR]（显式别名）
  /// - 其余未识别值（含 null）→ [IMPACT_FACTOR]（默认）
  ///
  /// @param code 外部传入的排序码，可为 null
  /// @return 对应的枚举值，未知或 null 时返回 [IMPACT_FACTOR]
  public static VenueBrowseSort fromCode(String code) {
    if (code == null) {
      return IMPACT_FACTOR;
    }
    return switch (code.toLowerCase(Locale.ROOT)) {
      case "casquartile", "cas_quartile" -> CAS_QUARTILE;
      case "title" -> TITLE;
      case "citedby", "cited_by" -> CITED_BY;
      default -> IMPACT_FACTOR;
    };
  }
}
