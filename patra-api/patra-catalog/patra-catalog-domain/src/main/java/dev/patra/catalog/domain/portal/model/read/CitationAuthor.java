package dev.patra.catalog.domain.portal.model.read;

/// 引用记录里的一位作者（`cat_publication_author` 行内快照）。
///
/// 个人作者有 `family` / `given` / `initials`，团体作者只有 `collective`。
///
/// @param family 姓（`last_name`），可空
/// @param given 名（`fore_name`），可空
/// @param initials 名的缩写，可空
/// @param collective 团体作者名，可空
/// @author linqibin
/// @since 0.1.0
public record CitationAuthor(String family, String given, String initials, String collective) {

  /// 创建作者。
  ///
  /// @param family 姓，可空
  /// @param given 名，可空
  /// @param initials 名的缩写，可空
  /// @param collective 团体作者名，可空
  /// @return 作者
  public static CitationAuthor of(String family, String given, String initials, String collective) {
    return new CitationAuthor(family, given, initials, collective);
  }
}
