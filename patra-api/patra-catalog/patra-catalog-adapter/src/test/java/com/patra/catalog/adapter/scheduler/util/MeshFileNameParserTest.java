package com.patra.catalog.adapter.scheduler.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.patra.catalog.adapter.scheduler.exception.MeshConfigurationException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/// MeshFileNameParser 单元测试。
///
/// 验证从 MeSH 数据文件 URL 中提取版本号的功能。
@DisplayName("MeshFileNameParser 版本号解析")
@Timeout(value = 2, unit = TimeUnit.SECONDS)
class MeshFileNameParserTest {

  // ========== 正常解析测试 ==========

  @Nested
  @DisplayName("正常解析测试")
  class NormalParsingTest {

    @Test
    @DisplayName("应从 Descriptor URL 提取版本号")
    void shouldExtractVersionFromDescriptorUrl() {
      var url = "https://nlmpubs.nlm.nih.gov/projects/mesh/MESH_FILES/xmlmesh/desc2025.xml";

      var result = MeshFileNameParser.extractVersion(url);

      assertThat(result).isEqualTo("2025");
    }

    @Test
    @DisplayName("应从 Qualifier URL 提取版本号")
    void shouldExtractVersionFromQualifierUrl() {
      var url = "https://nlmpubs.nlm.nih.gov/projects/mesh/MESH_FILES/xmlmesh/qual2024.xml";

      var result = MeshFileNameParser.extractVersion(url);

      assertThat(result).isEqualTo("2024");
    }

    @ParameterizedTest
    @ValueSource(
        strings = {
          "http://example.com/desc2020.xml",
          "https://example.com/path/to/desc2021.xml",
          "http://localhost/qual2022.xml",
          "https://test.org/mesh/qual2030.xml"
        })
    @DisplayName("应处理不同域名和路径的 URL")
    void shouldHandleVariousUrls(String url) {
      var result = MeshFileNameParser.extractVersion(url);

      assertThat(result).matches("\\d{4}");
    }

    @Test
    @DisplayName("应从简单文件路径提取版本号")
    void shouldExtractVersionFromSimplePath() {
      var url = "file:///tmp/desc2025.xml";

      var result = MeshFileNameParser.extractVersion(url);

      assertThat(result).isEqualTo("2025");
    }
  }

  // ========== 异常情况测试 ==========

  @Nested
  @DisplayName("异常情况测试")
  class ExceptionTest {

    @ParameterizedTest
    @ValueSource(
        strings = {
          "http://example.com/data.xml",
          "http://example.com/mesh2025.xml",
          "http://example.com/descriptor2025.xml",
          "http://example.com/qualifier2025.xml",
          "http://example.com/desc.xml",
          "http://example.com/qual.xml",
          "http://example.com/desc25.xml",
          "http://example.com/descABCD.xml"
        })
    @DisplayName("不符合格式的文件名应抛出异常")
    void shouldThrowExceptionForInvalidFileName(String url) {
      assertThatThrownBy(() -> MeshFileNameParser.extractVersion(url))
          .isInstanceOf(MeshConfigurationException.class)
          .hasMessageContaining("无法从文件名解析 MeSH 版本号");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "\t", "\n"})
    @DisplayName("空白 URL 应抛出异常")
    void shouldThrowExceptionForBlankUrl(String url) {
      assertThatThrownBy(() -> MeshFileNameParser.extractVersion(url))
          .isInstanceOf(MeshConfigurationException.class)
          .hasMessageContaining("URL 不能为空");
    }

    @Test
    @DisplayName("无效 URL 格式应抛出异常")
    void shouldThrowExceptionForMalformedUrl() {
      var url = "not a valid url";

      assertThatThrownBy(() -> MeshFileNameParser.extractVersion(url))
          .isInstanceOf(MeshConfigurationException.class)
          .hasMessageContaining("URL 格式无效");
    }
  }

  // ========== 边界情况测试 ==========

  @Nested
  @DisplayName("边界情况测试")
  class EdgeCaseTest {

    @Test
    @DisplayName("应处理带查询参数的 URL")
    void shouldHandleUrlWithQueryParams() {
      var url = "https://example.com/desc2025.xml?token=abc";

      var result = MeshFileNameParser.extractVersion(url);

      assertThat(result).isEqualTo("2025");
    }

    @Test
    @DisplayName("应处理带片段标识符的 URL")
    void shouldHandleUrlWithFragment() {
      var url = "https://example.com/qual2024.xml#section";

      var result = MeshFileNameParser.extractVersion(url);

      assertThat(result).isEqualTo("2024");
    }
  }
}
