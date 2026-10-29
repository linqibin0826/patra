package dev.patra.identity.infra.account.geolocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/// ip2region 的原始结果转成展示用的属地（PAP-99 设计第 8.3 节）。
///
/// 原始结果五段：`国家|省份|城市|ISP|ISO 国家码`，未知段写 `0`；中国境内是中文，境外是英文。
/// 中国（ISO 码 `CN`，含港澳台）到国家 / 省 / 市并去掉「省」「市」「特别行政区」后缀、直辖市去重；
/// 境外只到国家，国家名按 ISO 码用 JDK 的 CLDR 数据转中文。不要 ISP。
final class RegionText {

  /// 属地最多多少个字符（登录记录的列宽）。
  static final int MAX_LENGTH = 64;

  private static final String UNKNOWN = "0";
  private static final String RESERVED = "Reserved";
  private static final List<String> SUFFIXES = List.of("特别行政区", "省", "市");

  /// ISO 3166-1 的两位国家码：不在其中的（`0`、`ZZ` 之类）不转中文名，CLDR 会把它们译成「未知地区」。
  private static final Set<String> ISO_COUNTRIES =
      Locale.getISOCountries(Locale.IsoCountryCode.PART1_ALPHA2);

  /// 只有静态方法。
  private RegionText() {}

  /// 规范化。
  ///
  /// @param raw ip2region 的原始结果，可以为 `null`
  /// @return 属地，空格分隔；不可用时为 `null`
  static String normalize(String raw) {
    if (raw == null || raw.isEmpty()) {
      return null;
    }
    String[] parts = raw.split("\\|", -1);
    if (parts.length != 5 || UNKNOWN.equals(parts[0]) || RESERVED.equals(parts[0])) {
      return null;
    }
    String text = "CN".equals(parts[4]) ? china(parts[0], parts[1], parts[2]) : country(parts);
    return text.length() > MAX_LENGTH ? text.substring(0, MAX_LENGTH) : text;
  }

  /// 中国：国家 + 省 + 市，去后缀，省市相同只留一个。
  ///
  /// @param country 国家
  /// @param province 省份
  /// @param city 城市
  /// @return 属地
  private static String china(String country, String province, String city) {
    List<String> segments = new ArrayList<>(List.of(country));
    String shortProvince = UNKNOWN.equals(province) ? null : stripSuffix(province);
    String shortCity = UNKNOWN.equals(city) ? null : stripSuffix(city);
    if (shortProvince != null) {
      segments.add(shortProvince);
    }
    if (shortCity != null && !shortCity.equals(shortProvince)) {
      segments.add(shortCity);
    }
    return String.join(" ", segments);
  }

  /// 境外：只到国家，ISO 码是正式国家码就用中文名，否则用数据里的英文国家名。
  ///
  /// @param parts 五段原始结果
  /// @return 国家名
  private static String country(String[] parts) {
    String iso = parts[4];
    if (ISO_COUNTRIES.contains(iso)) {
      return Locale.of("", iso).getDisplayCountry(Locale.SIMPLIFIED_CHINESE);
    }
    return parts[0];
  }

  /// 去掉「特别行政区」「省」「市」后缀；去完为空时保留原值。
  ///
  /// @param name 省或市
  /// @return 短名
  private static String stripSuffix(String name) {
    for (String suffix : SUFFIXES) {
      if (name.endsWith(suffix) && name.length() > suffix.length()) {
        return name.substring(0, name.length() - suffix.length());
      }
    }
    return name;
  }
}
