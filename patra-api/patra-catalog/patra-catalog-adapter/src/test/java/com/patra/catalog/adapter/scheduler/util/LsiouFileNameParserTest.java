package com.patra.catalog.adapter.scheduler.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.patra.catalog.adapter.scheduler.exception.LsiouConfigurationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/// LsiouFileNameParser 单元测试。
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("LsiouFileNameParser 单元测试")
class LsiouFileNameParserTest {

  @Nested
  @DisplayName("LSIOU 格式解析")
  class LsiouParsingTest {

    @ParameterizedTest
    @CsvSource({
      "ftp://ftp.nlm.nih.gov/online/journals/lsi2022.xml, 2022",
      "ftp://ftp.nlm.nih.gov/online/journals/lsi2024.xml, 2024",
      "http://example.com/data/lsi2023.xml, 2023",
      "https://example.com/lsi2000.xml, 2000"
    })
    @DisplayName("应从 LSIOU URL 正确提取年份版本号")
    void shouldExtractYearFromLsiouUrl(String url, String expectedVersion) {
      String version = LsiouFileNameParser.extractVersion(url);
      assertThat(version).isEqualTo(expectedVersion);
    }
  }

  @Nested
  @DisplayName("错误处理")
  class ErrorHandlingTest {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t", "\n"})
    @DisplayName("空或空白 URL 应抛出 LsiouConfigurationException")
    void shouldThrowExceptionForBlankUrl(String url) {
      assertThatThrownBy(() -> LsiouFileNameParser.extractVersion(url))
          .isInstanceOf(LsiouConfigurationException.class)
          .hasMessage("URL 不能为空");
    }

    @ParameterizedTest
    @ValueSource(
        strings = {
          "https://example.com/data.xml",
          "https://example.com/lsi.xml",
          "https://example.com/lsi25.xml",
          "https://example.com/lsi12345.xml",
          "https://example.com/serfilebase.2025.xml",
          "https://example.com/serfile250101.xml"
        })
    @DisplayName("不符合规范的文件名应抛出 LsiouConfigurationException")
    void shouldThrowExceptionForInvalidFileName(String url) {
      assertThatThrownBy(() -> LsiouFileNameParser.extractVersion(url))
          .isInstanceOf(LsiouConfigurationException.class)
          .hasMessageContaining("无法从文件名解析 LSIOU 版本号");
    }

    @Test
    @DisplayName("无效 URL 格式应抛出 LsiouConfigurationException")
    void shouldThrowExceptionForInvalidUrlFormat() {
      assertThatThrownBy(() -> LsiouFileNameParser.extractVersion("not-a-valid-url"))
          .isInstanceOf(LsiouConfigurationException.class)
          .hasMessageContaining("无法从文件名解析 LSIOU 版本号");
    }
  }
}
