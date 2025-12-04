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

  private static final String VALID_FILE_PATH = "/tmp/mesh-import.xml";
  private static final String VALID_MESH_VERSION = "2025";

  @Nested
  @DisplayName("参数验证测试")
  class ValidationTests {

    @Test
    @DisplayName("应该在 filePath 为 null 时抛出 IllegalArgumentException")
    void shouldThrowWhenFilePathIsNull() {
      assertThatThrownBy(() -> new MeshImportParams(null, VALID_MESH_VERSION, false))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("filePath 不能为空");
    }

    @Test
    @DisplayName("应该在 filePath 为空字符串时抛出 IllegalArgumentException")
    void shouldThrowWhenFilePathIsEmpty() {
      assertThatThrownBy(() -> new MeshImportParams("", VALID_MESH_VERSION, false))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("filePath 不能为空");
    }

    @Test
    @DisplayName("应该在 filePath 为空白字符串时抛出 IllegalArgumentException")
    void shouldThrowWhenFilePathIsBlank() {
      assertThatThrownBy(() -> new MeshImportParams("   ", VALID_MESH_VERSION, false))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("filePath 不能为空");
    }

    @Test
    @DisplayName("应该在 meshVersion 为 null 时抛出 IllegalArgumentException")
    void shouldThrowWhenMeshVersionIsNull() {
      assertThatThrownBy(() -> new MeshImportParams(VALID_FILE_PATH, null, false))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("meshVersion 不能为空");
    }

    @Test
    @DisplayName("应该在 meshVersion 为空字符串时抛出 IllegalArgumentException")
    void shouldThrowWhenMeshVersionIsEmpty() {
      assertThatThrownBy(() -> new MeshImportParams(VALID_FILE_PATH, "", false))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("meshVersion 不能为空");
    }

    @Test
    @DisplayName("应该在 meshVersion 为空白字符串时抛出 IllegalArgumentException")
    void shouldThrowWhenMeshVersionIsBlank() {
      assertThatThrownBy(() -> new MeshImportParams(VALID_FILE_PATH, "   ", false))
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
      MeshImportParams params = new MeshImportParams(VALID_FILE_PATH, VALID_MESH_VERSION, true);

      // Then
      assertThat(params.filePath()).isEqualTo(VALID_FILE_PATH);
      assertThat(params.meshVersion()).isEqualTo(VALID_MESH_VERSION);
      assertThat(params.tempFile()).isTrue();
    }

    @Test
    @DisplayName("构造函数应该支持非临时文件")
    void constructorShouldSupportNonTempFile() {
      // When
      MeshImportParams params = new MeshImportParams(VALID_FILE_PATH, VALID_MESH_VERSION, false);

      // Then
      assertThat(params.filePath()).isEqualTo(VALID_FILE_PATH);
      assertThat(params.meshVersion()).isEqualTo(VALID_MESH_VERSION);
      assertThat(params.tempFile()).isFalse();
    }
  }

  @Nested
  @DisplayName("工厂方法测试")
  class FactoryMethodTests {

    @Test
    @DisplayName("of() 应该创建非临时文件参数")
    void ofShouldCreateNonTempFileParams() {
      // When
      MeshImportParams params = MeshImportParams.of(VALID_FILE_PATH, VALID_MESH_VERSION);

      // Then
      assertThat(params.filePath()).isEqualTo(VALID_FILE_PATH);
      assertThat(params.meshVersion()).isEqualTo(VALID_MESH_VERSION);
      assertThat(params.tempFile()).isFalse();
    }

    @Test
    @DisplayName("withTempFile() 应该创建临时文件参数")
    void withTempFileShouldCreateTempFileParams() {
      // When
      MeshImportParams params = MeshImportParams.withTempFile(VALID_FILE_PATH, VALID_MESH_VERSION);

      // Then
      assertThat(params.filePath()).isEqualTo(VALID_FILE_PATH);
      assertThat(params.meshVersion()).isEqualTo(VALID_MESH_VERSION);
      assertThat(params.tempFile()).isTrue();
    }

    @Test
    @DisplayName("of() 应该验证参数")
    void ofShouldValidateParams() {
      assertThatThrownBy(() -> MeshImportParams.of(null, VALID_MESH_VERSION))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("filePath 不能为空");
    }

    @Test
    @DisplayName("withTempFile() 应该验证参数")
    void withTempFileShouldValidateParams() {
      assertThatThrownBy(() -> MeshImportParams.withTempFile(VALID_FILE_PATH, null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("meshVersion 不能为空");
    }
  }
}
