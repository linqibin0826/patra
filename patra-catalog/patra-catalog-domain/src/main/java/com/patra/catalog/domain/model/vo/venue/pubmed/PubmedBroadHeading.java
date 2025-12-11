package com.patra.catalog.domain.model.vo.venue.pubmed;

/// PubMed 广泛期刊分类。
///
/// 表示期刊所属的广泛学科类别，如 Science、Medicine、Chemistry 等。
/// 这些分类用于对期刊进行高层次的学科归类。
///
/// @param heading 分类名称
/// @author linqibin
/// @since 0.1.0
public record PubmedBroadHeading(String heading) {

  /// 创建广泛分类。
  ///
  /// @param heading 分类名称
  /// @return 广泛分类值对象
  public static PubmedBroadHeading of(String heading) {
    return new PubmedBroadHeading(heading);
  }
}
