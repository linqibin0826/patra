package com.patra.catalog.domain.model.vo.venue;

/// Wikidata 富化数据值对象。
///
/// 封装从 Wikidata SPARQL 查询获取的期刊富化字段，
/// 作为单次查询的聚合返回结果，避免多次网络调用。
///
/// **包含的数据**：
///
/// | 字段 | Wikidata 属性 | 说明 |
/// |------|--------------|------|
/// | titleZh | rdfs:label (zh/zh-hans/zh-hant) | 中文标题 |
/// | imageUrl | P18 | 封面图片（Wikimedia Commons URL） |
/// | homepageUrl | P856 | 官方网站 URL |
///
/// **不变性**：Record 自动保证不可变。
///
/// **null 语义**：字段为 null 表示 Wikidata 中无对应数据，
/// 不代表查询失败。调用方应用 null 守护逻辑（"null 不清除已有值"）。
///
/// @param titleZh 中文标题（可为 null）
/// @param imageUrl 封面图片 URL（Wikimedia Commons，可为 null）
/// @param homepageUrl 官方网站 URL（可为 null）
/// @author linqibin
/// @since 0.1.0
public record VenueWikidataEnrichment(String titleZh, String imageUrl, String homepageUrl) {

  /// 创建包含所有字段的富化数据。
  ///
  /// @param titleZh 中文标题（可为 null）
  /// @param imageUrl 封面图片 URL（可为 null）
  /// @param homepageUrl 官方网站 URL（可为 null）
  /// @return 富化数据值对象
  public static VenueWikidataEnrichment of(String titleZh, String imageUrl, String homepageUrl) {
    return new VenueWikidataEnrichment(titleZh, imageUrl, homepageUrl);
  }

  /// 判断是否有中文标题。
  ///
  /// @return true 如果有中文标题
  public boolean hasTitleZh() {
    return titleZh != null && !titleZh.isBlank();
  }

  /// 判断是否有封面图片 URL。
  ///
  /// @return true 如果有封面图片 URL
  public boolean hasImageUrl() {
    return imageUrl != null && !imageUrl.isBlank();
  }

  /// 判断是否有官方网站 URL。
  ///
  /// @return true 如果有官方网站 URL
  public boolean hasHomepageUrl() {
    return homepageUrl != null && !homepageUrl.isBlank();
  }
}
