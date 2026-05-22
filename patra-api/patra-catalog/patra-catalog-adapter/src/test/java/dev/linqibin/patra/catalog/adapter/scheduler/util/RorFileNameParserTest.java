package dev.linqibin.patra.catalog.adapter.scheduler.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.linqibin.patra.catalog.adapter.scheduler.exception.RorConfigurationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/// RorFileNameParser 单元测试。
///
/// 验证从 ROR Data Dump 文件 URL 中提取版本号的功能。
///
/// **文件名格式**：`v{version}-{date}-ror-data.zip`
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("RorFileNameParser 版本号解析")
class RorFileNameParserTest {

  // ========== 正常解析测试 ==========

  @Nested
  @DisplayName("正常解析测试")
  class NormalParsingTest {

    @Test
    @DisplayName("应从标准 Zenodo URL 提取版本号")
    void shouldExtractVersionFromZenodoUrl() {
      var url = "https://zenodo.org/records/17468391/files/v2.0-2025-12-16-ror-data.zip";

      var result = RorFileNameParser.extractVersion(url);

      assertThat(result).isEqualTo("v2.0");
    }

    @Test
    @DisplayName("应从带下载参数的 URL 提取版本号")
    void shouldExtractVersionFromUrlWithDownloadParam() {
      var url = "https://zenodo.org/records/17953395/files/v2.0-2025-12-16-ror-data.zip?download=1";

      var result = RorFileNameParser.extractVersion(url);

      assertThat(result).isEqualTo("v2.0");
    }

    @Test
    @DisplayName("应处理不同版本号格式")
    void shouldHandleDifferentVersionFormats() {
      var url = "https://example.com/v1.63-2024-01-09-ror-data.zip";

      var result = RorFileNameParser.extractVersion(url);

      assertThat(result).isEqualTo("v1.63");
    }

    @ParameterizedTest
    @ValueSource(
        strings = {
          "https://zenodo.org/records/123/files/v1.0-2024-01-01-ror-data.zip",
          "https://example.com/path/to/v2.5-2025-06-15-ror-data.zip",
          "http://localhost/v3.0-2023-12-31-ror-data.zip",
          "https://test.org/ror/v10.99-2030-01-01-ror-data.zip"
        })
    @DisplayName("应处理不同域名和路径的 URL")
    void shouldHandleVariousUrls(String url) {
      var result = RorFileNameParser.extractVersion(url);

      assertThat(result).matches("v\\d+\\.\\d+");
    }

    @Test
    @DisplayName("应从本地文件路径提取版本号")
    void shouldExtractVersionFromLocalFilePath() {
      var url = "file:///tmp/v2.0-2025-12-16-ror-data.zip";

      var result = RorFileNameParser.extractVersion(url);

      assertThat(result).isEqualTo("v2.0");
    }
  }

  // ========== 异常情况测试 ==========

  @Nested
  @DisplayName("异常情况测试")
  class ExceptionTest {

    @ParameterizedTest
    @ValueSource(
        strings = {
          "https://example.com/data.zip",
          "https://example.com/ror-data.zip",
          "https://example.com/2025-12-16-ror-data.zip",
          "https://example.com/v2.0-ror-data.zip",
          "https://example.com/v2.0-2025-12-16.zip",
          "https://example.com/v2-2025-12-16-ror-data.zip"
        })
    @DisplayName("不符合格式的文件名应抛出异常")
    void shouldThrowExceptionForInvalidFileName(String url) {
      assertThatThrownBy(() -> RorFileNameParser.extractVersion(url))
          .isInstanceOf(RorConfigurationException.class)
          .hasMessageContaining("无法从文件名解析 ROR 版本号");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "\t", "\n"})
    @DisplayName("空白 URL 应抛出异常")
    void shouldThrowExceptionForBlankUrl(String url) {
      assertThatThrownBy(() -> RorFileNameParser.extractVersion(url))
          .isInstanceOf(RorConfigurationException.class)
          .hasMessageContaining("URL 不能为空");
    }

    @Test
    @DisplayName("无效 URL 格式应抛出异常")
    void shouldThrowExceptionForMalformedUrl() {
      var url = "not a valid url";

      assertThatThrownBy(() -> RorFileNameParser.extractVersion(url))
          .isInstanceOf(RorConfigurationException.class)
          .hasMessageContaining("URL 格式无效");
    }

    @Test
    @DisplayName("URL 路径为空应抛出异常")
    void shouldThrowExceptionForEmptyPath() {
      var url = "https://example.com";

      assertThatThrownBy(() -> RorFileNameParser.extractVersion(url))
          .isInstanceOf(RorConfigurationException.class);
    }
  }

  // ========== 边界情况测试 ==========

  @Nested
  @DisplayName("边界情况测试")
  class EdgeCaseTest {

    @Test
    @DisplayName("应处理带查询参数的 URL")
    void shouldHandleUrlWithQueryParams() {
      var url =
          "https://zenodo.org/records/123/files/v2.0-2025-12-16-ror-data.zip?download=1&token=abc";

      var result = RorFileNameParser.extractVersion(url);

      assertThat(result).isEqualTo("v2.0");
    }

    @Test
    @DisplayName("应处理带片段标识符的 URL")
    void shouldHandleUrlWithFragment() {
      var url = "https://example.com/v1.5-2024-06-01-ror-data.zip#section";

      var result = RorFileNameParser.extractVersion(url);

      assertThat(result).isEqualTo("v1.5");
    }

    @Test
    @DisplayName("应处理深层路径的 URL")
    void shouldHandleDeepPathUrl() {
      var url = "https://example.com/a/b/c/d/e/v2.0-2025-12-16-ror-data.zip";

      var result = RorFileNameParser.extractVersion(url);

      assertThat(result).isEqualTo("v2.0");
    }
  }
}
