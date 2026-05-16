package dev.linqibin.patra.catalog.app.usecase.publication.query.dto;

/// 刊级 Top N 高被引查询参数。
///
/// 承担 `limit` 的**默认值与范围归一化**（null→5，钳位到 [1,20]）；
/// QueryService 无需再次校验入参边界。
///
/// @param venueId 期刊 ID（必填）
/// @param limit   返回条数，已归一化到 [1,20]
/// @param since   发表年下限（可为 null，不过滤）
public record TopPublicationsQuery(Long venueId, int limit, Integer since) {

  /// 静态工厂方法，承担归一化职责。
  ///
  /// @param venueId  期刊 ID
  /// @param rawLimit 原始 limit（可为 null）
  /// @param since    发表年下限（可为 null）
  /// @return 已归一化的查询参数
  public static TopPublicationsQuery of(Long venueId, Integer rawLimit, Integer since) {
    int normalized = Math.max(1, Math.min(rawLimit == null ? 5 : rawLimit, 20));
    return new TopPublicationsQuery(venueId, normalized, since);
  }
}
