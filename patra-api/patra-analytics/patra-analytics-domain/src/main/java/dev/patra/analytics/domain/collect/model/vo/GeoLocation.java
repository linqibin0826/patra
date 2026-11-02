package dev.patra.analytics.domain.collect.model.vo;

/// IP 查出的地区。省份只在国家是 `CN` 时有值。
///
/// @param countryCode ISO 3166-1 两位国家码；查不到为 `null`
/// @param province 省级行政区名原文；境外或查不到为 `null`
public record GeoLocation(String countryCode, String province) {

  private static final GeoLocation UNKNOWN = new GeoLocation(null, null);

  /// 创建地区。
  ///
  /// @param countryCode 国家码
  /// @param province 省份
  /// @return 地区
  public static GeoLocation of(String countryCode, String province) {
    return new GeoLocation(countryCode, province);
  }

  /// 查不到地区。
  ///
  /// @return 两个字段都是 `null`
  public static GeoLocation unknown() {
    return UNKNOWN;
  }
}
