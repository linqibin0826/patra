package com.patra.catalog.domain.model.vo.mesh;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// MeshImportParams 值对象单元测试。
///
/// **测试策略**：
///
/// - 纯单元测试，无 Mock
/// - 测试参数验证逻辑和工厂方法
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("MeshImportParams 单元测试")
class MeshImportParamsTest {

  private static final String VALID_DOWNLOAD_URL =
      "https://nlmpubs.nlm.nih.gov/projects/mesh/MESH_FILES/xmlmesh/desc2025.xml";
  private static final String VALID_MESH_VERSION = "2025";

  @Nested
  @DisplayName("参数验证测试")
  class ValidationTests {

    @Test
    @DisplayName("应该在 downloadUrl 为 null 时抛出 IllegalArgumentException")
    void shouldThrowWhenDownloadUrlIsNull() {
      assertThatThrownBy(() -> new MeshImportParams(null, VALID_MESH_VERSION))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("downloadUrl 不能为空");
    }

    @Test
    @DisplayName("应该在 downloadUrl 为空字符串时抛出 IllegalArgumentException")
    void shouldThrowWhenDownloadUrlIsEmpty() {
      assertThatThrownBy(() -> new MeshImportParams("", VALID_MESH_VERSION))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("downloadUrl 不能为空");
    }

    @Test
    @DisplayName("应该在 downloadUrl 为空白字符串时抛出 IllegalArgumentException")
    void shouldThrowWhenDownloadUrlIsBlank() {
      assertThatThrownBy(() -> new MeshImportParams("   ", VALID_MESH_VERSION))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("downloadUrl 不能为空");
    }

    @Test
    @DisplayName("应该在 meshVersion 为 null 时抛出 IllegalArgumentException")
    void shouldThrowWhenMeshVersionIsNull() {
      assertThatThrownBy(() -> new MeshImportParams(VALID_DOWNLOAD_URL, null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("meshVersion 不能为空");
    }

    @Test
    @DisplayName("应该在 meshVersion 为空字符串时抛出 IllegalArgumentException")
    void shouldThrowWhenMeshVersionIsEmpty() {
      assertThatThrownBy(() -> new MeshImportParams(VALID_DOWNLOAD_URL, ""))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("meshVersion 不能为空");
    }

    @Test
    @DisplayName("应该在 meshVersion 为空白字符串时抛出 IllegalArgumentException")
    void shouldThrowWhenMeshVersionIsBlank() {
      assertThatThrownBy(() -> new MeshImportParams(VALID_DOWNLOAD_URL, "   "))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("meshVersion 不能为空");
    }
  }

  @Nested
  @DisplayName("构造函数测试")
  class ConstructorTests {

    @Test
    @DisplayName("构造函数应该正确设置所有字段")
    void constructorShouldSetAllFields() {
      // When
      MeshImportParams params = new MeshImportParams(VALID_DOWNLOAD_URL, VALID_MESH_VERSION);

      // Then
      assertThat(params.downloadUrl()).isEqualTo(VALID_DOWNLOAD_URL);
      assertThat(params.meshVersion()).isEqualTo(VALID_MESH_VERSION);
    }
  }

  @Nested
  @DisplayName("工厂方法测试")
  class FactoryMethodTests {

    @Test
    @DisplayName("withDownloadUrl() 应该创建导入参数")
    void withDownloadUrlShouldCreateParams() {
      // When
      MeshImportParams params =
          MeshImportParams.withDownloadUrl(VALID_DOWNLOAD_URL, VALID_MESH_VERSION);

      // Then
      assertThat(params.downloadUrl()).isEqualTo(VALID_DOWNLOAD_URL);
      assertThat(params.meshVersion()).isEqualTo(VALID_MESH_VERSION);
    }

    @Test
    @DisplayName("withDownloadUrl() 应该验证 downloadUrl")
    void withDownloadUrlShouldValidateDownloadUrl() {
      assertThatThrownBy(() -> MeshImportParams.withDownloadUrl(null, VALID_MESH_VERSION))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("downloadUrl 不能为空");
    }

    @Test
    @DisplayName("withDownloadUrl() 应该验证 meshVersion")
    void withDownloadUrlShouldValidateMeshVersion() {
      assertThatThrownBy(() -> MeshImportParams.withDownloadUrl(VALID_DOWNLOAD_URL, null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("meshVersion 不能为空");
    }
  }
}
