package dev.patra.catalog.adapter.fulltext.rest.converter;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/// `CcLicenseLinks` 单元测试。
@DisplayName("CcLicenseLinks")
class CcLicenseLinksTest {

  @ParameterizedTest(name = "[{index}] {0}")
  @CsvSource({
    "CC BY, https://creativecommons.org/licenses/by/4.0/",
    "CC BY-NC, https://creativecommons.org/licenses/by-nc/4.0/",
    "CC BY-NC-ND, https://creativecommons.org/licenses/by-nc-nd/4.0/",
    "CC BY-ND, https://creativecommons.org/licenses/by-nd/4.0/",
    "CC BY-SA, https://creativecommons.org/licenses/by-sa/4.0/",
    "CC BY-NC-SA, https://creativecommons.org/licenses/by-nc-sa/4.0/",
    "CC0, https://creativecommons.org/publicdomain/zero/1.0/",
    "'  cc by-nc-nd ', https://creativecommons.org/licenses/by-nc-nd/4.0/"
  })
  @DisplayName("常见 CC 许可映射到官方链接，大小写与多余空格不敏感")
  void should_map_known_cc_licenses(String code, String url) {
    assertThat(CcLicenseLinks.urlOf(code)).contains(url);
  }

  @Test
  @DisplayName("未知许可或空值返回空")
  void should_return_empty_for_unknown() {
    assertThat(CcLicenseLinks.urlOf("TDM")).isEmpty();
    assertThat(CcLicenseLinks.urlOf(null)).isEmpty();
    assertThat(CcLicenseLinks.urlOf(" ")).isEmpty();
  }
}
