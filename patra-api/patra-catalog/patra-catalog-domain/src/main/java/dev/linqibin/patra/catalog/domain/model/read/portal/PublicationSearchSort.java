package dev.linqibin.patra.catalog.domain.model.read.portal;

import java.util.Locale;

/// 文献检索排序方式。
///
/// [#CITED] 只服务首页 feed 的 cited tab，检索端点的请求校验不放行该值（release-spec 边界 C）。
///
/// @author linqibin
/// @since 0.1.0
public enum PublicationSearchSort {

  /// 按最后采集时间降序（默认）。
  LATEST,

  /// 按出版年份降序，同年按最后采集时间降序。
  YEAR,

  /// 按被引次数降序（feed 专用）。
  CITED;

  /// 将外部字符串码解析为枚举值，大小写不敏感；null 或未识别值返回 [#LATEST]。
  ///
  /// @param code 外部传入的排序码，可为 null
  /// @return 对应的枚举值
  public static PublicationSearchSort fromCode(String code) {
    if (code == null) {
      return LATEST;
    }
    return switch (code.toLowerCase(Locale.ROOT)) {
      case "year" -> YEAR;
      case "cited" -> CITED;
      default -> LATEST;
    };
  }
}
