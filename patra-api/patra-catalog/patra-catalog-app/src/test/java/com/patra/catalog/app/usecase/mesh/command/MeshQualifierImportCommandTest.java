package com.patra.catalog.app.usecase.mesh.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.patra.catalog.domain.exception.CatalogScheduleParameterException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/// MeSH 限定词导入命令单元测试。
///
/// **测试策略**：
///
/// - 验证 URL 格式（必须是 HTTP/HTTPS 协议）
/// - 验证必填字段约束（url、meshVersion）
/// - 验证工厂方法
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("MeshQualifierImportCommand 单元测试")
class MeshQualifierImportCommandTest {

  @Nested
  @DisplayName("URL 验证测试")
  class UrlValidationTest {

    @Test
    @DisplayName("有效的 HTTP URL - 应该创建成功")
    void validHttpUrl_shouldCreateSuccessfully() {
      // Given
      String url = "http://example.com/mesh/qual2025.xml";

      // When
      MeshQualifierImportCommand command = new MeshQualifierImportCommand(url, "2025");

      // Then
      assertThat(command.url()).isEqualTo(url);
      assertThat(command.meshVersion()).isEqualTo("2025");
    }

    @Test
    @DisplayName("有效的 HTTPS URL - 应该创建成功")
    void validHttpsUrl_shouldCreateSuccessfully() {
      // Given
      String url = "https://nlmpubs.nlm.nih.gov/projects/mesh/2025/xmlmesh/qual2025.xml";

      // When
      MeshQualifierImportCommand command = new MeshQualifierImportCommand(url, "2025");

      // Then
      assertThat(command.url()).isEqualTo(url);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "\t", "\n"})
    @DisplayName("空白 URL - 应该抛出 CatalogScheduleParameterException")
    void blankUrl_shouldThrowException(String url) {
      // When & Then
      assertThatThrownBy(() -> new MeshQualifierImportCommand(url, "2025"))
          .isInstanceOf(CatalogScheduleParameterException.class)
          .hasMessageContaining("url");
    }

    @ParameterizedTest
    @ValueSource(
        strings = {
          "ftp://example.com/file.xml",
          "file:///local/path.xml",
          "/local/path.xml",
          "not-a-url",
          "example.com/file.xml"
        })
    @DisplayName("非 HTTP/HTTPS 协议 - 应该抛出 CatalogScheduleParameterException")
    void nonHttpProtocol_shouldThrowException(String url) {
      // When & Then
      assertThatThrownBy(() -> new MeshQualifierImportCommand(url, "2025"))
          .isInstanceOf(CatalogScheduleParameterException.class)
          .hasMessageContaining("HTTP");
    }

    @Test
    @DisplayName("无效的 URL 格式 - 应该抛出 CatalogScheduleParameterException")
    void invalidUrlFormat_shouldThrowException() {
      // Given
      String invalidUrl = "http://[invalid";

      // When & Then
      assertThatThrownBy(() -> new MeshQualifierImportCommand(invalidUrl, "2025"))
          .isInstanceOf(CatalogScheduleParameterException.class)
          .hasMessageContaining("url");
    }
  }

  @Nested
  @DisplayName("meshVersion 验证测试")
  class MeshVersionValidationTest {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "\t"})
    @DisplayName("空白 meshVersion - 应该抛出 CatalogScheduleParameterException")
    void blankMeshVersion_shouldThrowException(String meshVersion) {
      // When & Then
      assertThatThrownBy(
              () -> new MeshQualifierImportCommand("https://example.com/qual.xml", meshVersion))
          .isInstanceOf(CatalogScheduleParameterException.class)
          .hasMessageContaining("meshVersion");
    }
  }

  @Nested
  @DisplayName("of() 工厂方法测试")
  class FactoryMethodTest {

    @Test
    @DisplayName("有效参数 - 应该正确创建命令")
    void validParameters_shouldCreateCommand() {
      // Given
      String url = "https://example.com/qual.xml";
      String meshVersion = "2025";

      // When
      MeshQualifierImportCommand command = MeshQualifierImportCommand.of(url, meshVersion);

      // Then
      assertThat(command.url()).isEqualTo(url);
      assertThat(command.meshVersion()).isEqualTo(meshVersion);
    }
  }
}
