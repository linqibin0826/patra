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
      assertThatThrownBy(() -> new MeshImportParams(null, VALID_MESH_VERSION, false, false))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("filePath 不能为空");
    }

    @Test
    @DisplayName("应该在 filePath 为空字符串时抛出 IllegalArgumentException")
    void shouldThrowWhenFilePathIsEmpty() {
      assertThatThrownBy(() -> new MeshImportParams("", VALID_MESH_VERSION, false, false))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("filePath 不能为空");
    }

    @Test
    @DisplayName("应该在 filePath 为空白字符串时抛出 IllegalArgumentException")
    void shouldThrowWhenFilePathIsBlank() {
      assertThatThrownBy(() -> new MeshImportParams("   ", VALID_MESH_VERSION, false, false))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("filePath 不能为空");
    }

    @Test
    @DisplayName("应该在 meshVersion 为 null 时抛出 IllegalArgumentException")
    void shouldThrowWhenMeshVersionIsNull() {
      assertThatThrownBy(() -> new MeshImportParams(VALID_FILE_PATH, null, false, false))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("meshVersion 不能为空");
    }

    @Test
    @DisplayName("应该在 meshVersion 为空字符串时抛出 IllegalArgumentException")
    void shouldThrowWhenMeshVersionIsEmpty() {
      assertThatThrownBy(() -> new MeshImportParams(VALID_FILE_PATH, "", false, false))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("meshVersion 不能为空");
    }

    @Test
    @DisplayName("应该在 meshVersion 为空白字符串时抛出 IllegalArgumentException")
    void shouldThrowWhenMeshVersionIsBlank() {
      assertThatThrownBy(() -> new MeshImportParams(VALID_FILE_PATH, "   ", false, false))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("meshVersion 不能为空");
    }
  }

  @Nested
  @DisplayName("构造函数测试")
  class ConstructorTests {

    @Test
    @DisplayName("四参数构造函数应该正确设置所有字段")
    void fourArgConstructorShouldSetAllFields() {
      // When
      MeshImportParams params =
          new MeshImportParams(VALID_FILE_PATH, VALID_MESH_VERSION, true, true);

      // Then
      assertThat(params.filePath()).isEqualTo(VALID_FILE_PATH);
      assertThat(params.meshVersion()).isEqualTo(VALID_MESH_VERSION);
      assertThat(params.forceNewInstance()).isTrue();
      assertThat(params.tempFile()).isTrue();
    }

    @Test
    @DisplayName("三参数构造函数应该将 tempFile 默认为 false")
    void threeArgConstructorShouldDefaultTempFileToFalse() {
      // When
      MeshImportParams params = new MeshImportParams(VALID_FILE_PATH, VALID_MESH_VERSION, true);

      // Then
      assertThat(params.filePath()).isEqualTo(VALID_FILE_PATH);
      assertThat(params.meshVersion()).isEqualTo(VALID_MESH_VERSION);
      assertThat(params.forceNewInstance()).isTrue();
      assertThat(params.tempFile()).isFalse();
    }
  }

  @Nested
  @DisplayName("工厂方法测试")
  class FactoryMethodTests {

    @Test
    @DisplayName("incremental() 应该创建幂等执行参数")
    void incrementalShouldCreateIdempotentParams() {
      // When
      MeshImportParams params = MeshImportParams.incremental(VALID_FILE_PATH, VALID_MESH_VERSION);

      // Then
      assertThat(params.filePath()).isEqualTo(VALID_FILE_PATH);
      assertThat(params.meshVersion()).isEqualTo(VALID_MESH_VERSION);
      assertThat(params.forceNewInstance()).isFalse();
      assertThat(params.tempFile()).isFalse();
    }

    @Test
    @DisplayName("forceNew() 应该创建强制新实例参数")
    void forceNewShouldCreateForceNewInstanceParams() {
      // When
      MeshImportParams params = MeshImportParams.forceNew(VALID_FILE_PATH, VALID_MESH_VERSION);

      // Then
      assertThat(params.filePath()).isEqualTo(VALID_FILE_PATH);
      assertThat(params.meshVersion()).isEqualTo(VALID_MESH_VERSION);
      assertThat(params.forceNewInstance()).isTrue();
      assertThat(params.tempFile()).isFalse();
    }

    @Test
    @DisplayName("incremental() 应该验证参数")
    void incrementalShouldValidateParams() {
      assertThatThrownBy(() -> MeshImportParams.incremental(null, VALID_MESH_VERSION))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("filePath 不能为空");
    }

    @Test
    @DisplayName("forceNew() 应该验证参数")
    void forceNewShouldValidateParams() {
      assertThatThrownBy(() -> MeshImportParams.forceNew(VALID_FILE_PATH, null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("meshVersion 不能为空");
    }
  }
}
