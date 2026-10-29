package dev.patra.catalog.adapter.fulltext.rest.converter;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/// PMC 许可原值 → Creative Commons 官方链接（CC 4.0 / CC0 1.0）。
///
/// @author linqibin
/// @since 0.11.0
final class CcLicenseLinks {

  private static final Map<String, String> LINKS =
      Map.of(
          "CC BY", "https://creativecommons.org/licenses/by/4.0/",
          "CC BY-NC", "https://creativecommons.org/licenses/by-nc/4.0/",
          "CC BY-NC-ND", "https://creativecommons.org/licenses/by-nc-nd/4.0/",
          "CC BY-ND", "https://creativecommons.org/licenses/by-nd/4.0/",
          "CC BY-SA", "https://creativecommons.org/licenses/by-sa/4.0/",
          "CC BY-NC-SA", "https://creativecommons.org/licenses/by-nc-sa/4.0/",
          "CC0", "https://creativecommons.org/publicdomain/zero/1.0/");

  private CcLicenseLinks() {}

  /// 查许可链接，大小写与多余空格不敏感。
  ///
  /// @param licenseCode 许可原值，可空
  /// @return 链接；不认识或为空时为空
  static Optional<String> urlOf(String licenseCode) {
    if (licenseCode == null || licenseCode.isBlank()) {
      return Optional.empty();
    }
    String normalized = licenseCode.strip().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
    return Optional.ofNullable(LINKS.get(normalized));
  }
}
