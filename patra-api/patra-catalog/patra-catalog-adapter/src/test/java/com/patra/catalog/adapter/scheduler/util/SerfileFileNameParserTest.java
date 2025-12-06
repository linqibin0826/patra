package com.patra.catalog.adapter.scheduler.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.patra.catalog.adapter.scheduler.exception.SerfileConfigurationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/// SerfileFileNameParser 单元测试。
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("SerfileFileNameParser 单元测试")
class SerfileFileNameParserTest {

  @Nested
  @DisplayName("SerfileBase 格式解析")
  class SerfileBaseParsingTest {

    @ParameterizedTest
    @CsvSource({
      "https://ftp.ncbi.nlm.nih.gov/pubmed/Serfile/serfilebase2025.xml, 2025",
      "https://nlmpubs.nlm.nih.gov/projects/serials/Serfiles/serfilebase2024.xml, 2024",
      "http://example.com/data/serfilebase2023.xml, 2023",
      "https://example.com/serfilebase2000.xml, 2000"
    })
    @DisplayName("应从 SerfileBase URL 正确提取年份版本号")
    void shouldExtractYearFromSerfileBaseUrl(String url, String expectedVersion) {
      String version = SerfileFileNameParser.extractVersion(url);
      assertThat(version).isEqualTo(expectedVersion);
    }
  }

  @Nested
  @DisplayName("Serfile 更新格式解析")
  class SerfileUpdateParsingTest {

    @ParameterizedTest
    @CsvSource({
      "https://ftp.ncbi.nlm.nih.gov/pubmed/Serfile/serfile250101.xml, 250101",
      "https://example.com/serfile240615.xml, 240615",
      "http://example.com/data/serfile231231.xml, 231231"
    })
    @DisplayName("应从 Serfile 更新文件 URL 正确提取日期版本号")
    void shouldExtractDateFromSerfileUpdateUrl(String url, String expectedVersion) {
      String version = SerfileFileNameParser.extractVersion(url);
      assertThat(version).isEqualTo(expectedVersion);
    }
  }

  @Nested
  @DisplayName("错误处理")
  class ErrorHandlingTest {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t", "\n"})
    @DisplayName("空或空白 URL 应抛出 SerfileConfigurationException")
    void shouldThrowExceptionForBlankUrl(String url) {
      assertThatThrownBy(() -> SerfileFileNameParser.extractVersion(url))
          .isInstanceOf(SerfileConfigurationException.class)
          .hasMessage("URL 不能为空");
    }

    @ParameterizedTest
    @ValueSource(
        strings = {
          "https://example.com/data.xml",
          "https://example.com/serfile.xml",
          "https://example.com/serfilebase.xml",
          "https://example.com/serfilebase25.xml",
          "https://example.com/serfilebase12345.xml",
          "https://example.com/serfile12345.xml",
          "https://example.com/serfile25.xml"
        })
    @DisplayName("不符合规范的文件名应抛出 SerfileConfigurationException")
    void shouldThrowExceptionForInvalidFileName(String url) {
      assertThatThrownBy(() -> SerfileFileNameParser.extractVersion(url))
          .isInstanceOf(SerfileConfigurationException.class)
          .hasMessageContaining("无法从文件名解析 Serfile 版本号");
    }

    @Test
    @DisplayName("无效 URL 格式应抛出 SerfileConfigurationException")
    void shouldThrowExceptionForInvalidUrlFormat() {
      assertThatThrownBy(() -> SerfileFileNameParser.extractVersion("not-a-valid-url"))
          .isInstanceOf(SerfileConfigurationException.class)
          .hasMessageContaining("无法从文件名解析 Serfile 版本号");
    }
  }
}
