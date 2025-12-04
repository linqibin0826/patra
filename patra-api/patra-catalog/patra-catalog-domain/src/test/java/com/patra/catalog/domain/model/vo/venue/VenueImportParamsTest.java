package com.patra.catalog.domain.model.vo.venue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/// VenueImportParams 单元测试。
///
/// **测试策略**：
///
/// - 验证构造函数参数校验
/// - 验证工厂方法
/// - 验证辅助方法
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("VenueImportParams 单元测试")
@Timeout(value = 2, unit = TimeUnit.SECONDS)
class VenueImportParamsTest {

  @Nested
  @DisplayName("构造函数测试")
  class ConstructorTest {

    @Test
    @DisplayName("有效参数（临时文件）- 应该成功创建")
    void validParamsWithTempFiles_shouldCreateSuccessfully() {
      // Given
      List<String> filePaths =
          List.of(
              "/tmp/openalex/updated_date=2025-11-02/part_000.gz",
              "/tmp/openalex/updated_date=2025-10-27/part_000.gz");

      // When
      VenueImportParams params = new VenueImportParams(filePaths, true);

      // Then
      assertThat(params.filePaths()).hasSize(2);
      assertThat(params.tempFiles()).isTrue();
    }

    @Test
    @DisplayName("有效参数（非临时文件）- 应该成功创建")
    void validParamsWithNonTempFiles_shouldCreateSuccessfully() {
      // Given
      List<String> filePaths = List.of("/tmp/openalex/part_000.gz");

      // When
      VenueImportParams params = new VenueImportParams(filePaths, false);

      // Then
      assertThat(params.filePaths()).hasSize(1);
      assertThat(params.tempFiles()).isFalse();
    }

    @Test
    @DisplayName("null filePaths - 应该抛出 IllegalArgumentException")
    void nullFilePaths_shouldThrowException() {
      // When & Then
      assertThatThrownBy(() -> new VenueImportParams(null, false))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("filePaths");
    }

    @Test
    @DisplayName("空 filePaths 列表 - 应该抛出 IllegalArgumentException")
    void emptyFilePaths_shouldThrowException() {
      // When & Then
      assertThatThrownBy(() -> new VenueImportParams(List.of(), false))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("filePaths");
    }
  }

  @Nested
  @DisplayName("工厂方法测试")
  class FactoryMethodTest {

    @Test
    @DisplayName("of() - 应该创建非临时文件参数")
    void of_shouldCreateNonTempFilesParams() {
      // Given
      List<String> filePaths = List.of("/tmp/openalex/part_000.gz");

      // When
      VenueImportParams params = VenueImportParams.of(filePaths);

      // Then
      assertThat(params.filePaths()).isEqualTo(filePaths);
      assertThat(params.tempFiles()).isFalse();
    }

    @Test
    @DisplayName("withTempFiles() - 应该创建临时文件参数")
    void withTempFiles_shouldCreateTempFilesParams() {
      // Given
      List<String> filePaths = List.of("/tmp/openalex/part_000.gz");

      // When
      VenueImportParams params = VenueImportParams.withTempFiles(filePaths);

      // Then
      assertThat(params.filePaths()).isEqualTo(filePaths);
      assertThat(params.tempFiles()).isTrue();
    }

    @Test
    @DisplayName("of() - 应该验证参数")
    void of_shouldValidateParams() {
      // When & Then
      assertThatThrownBy(() -> VenueImportParams.of(null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("filePaths");
    }

    @Test
    @DisplayName("withTempFiles() - 应该验证参数")
    void withTempFiles_shouldValidateParams() {
      // When & Then
      assertThatThrownBy(() -> VenueImportParams.withTempFiles(List.of()))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("filePaths");
    }
  }

  @Nested
  @DisplayName("辅助方法测试")
  class HelperMethodTest {

    @Test
    @DisplayName("getFilePathsAsString() - 应该返回逗号分隔的路径")
    void getFilePathsAsString_shouldReturnCommaSeparatedPaths() {
      // Given
      List<String> filePaths = List.of("/tmp/a.gz", "/tmp/b.gz", "/tmp/c.gz");
      VenueImportParams params = new VenueImportParams(filePaths, false);

      // When
      String pathsStr = params.getFilePathsAsString();

      // Then
      assertThat(pathsStr).isEqualTo("/tmp/a.gz,/tmp/b.gz,/tmp/c.gz");
    }

    @Test
    @DisplayName("getFileCount() - 应该返回文件数量")
    void getFileCount_shouldReturnFileCount() {
      // Given
      List<String> filePaths = List.of("/tmp/a.gz", "/tmp/b.gz");
      VenueImportParams params = new VenueImportParams(filePaths, false);

      // When
      int count = params.getFileCount();

      // Then
      assertThat(count).isEqualTo(2);
    }
  }
}
