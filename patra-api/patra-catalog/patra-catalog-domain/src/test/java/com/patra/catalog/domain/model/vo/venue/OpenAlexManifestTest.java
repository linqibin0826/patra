package com.patra.catalog.domain.model.vo.venue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/// OpenAlexManifest 单元测试。
///
/// **测试策略**：
///
/// - 验证构造函数和不变量
/// - 验证路径提取方法
/// - 验证 S3 URL 到 HTTP URL 的转换
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("OpenAlexManifest 单元测试")
@Timeout(value = 2, unit = TimeUnit.SECONDS)
class OpenAlexManifestTest {

  @Nested
  @DisplayName("构造函数测试")
  class ConstructorTest {

    @Test
    @DisplayName("有效参数 - 应该成功创建")
    void validParams_shouldCreateSuccessfully() {
      // Given
      var entries =
          List.of(
              new OpenAlexManifest.Entry(
                  "s3://openalex/data/sources/updated_date=2025-11-02/part_000.gz", 1000L, 100),
              new OpenAlexManifest.Entry(
                  "s3://openalex/data/sources/updated_date=2025-10-27/part_000.gz", 2000L, 200));

      // When
      var manifest = new OpenAlexManifest(entries, 3000L, 300);

      // Then
      assertThat(manifest.entries()).hasSize(2);
      assertThat(manifest.totalContentLength()).isEqualTo(3000L);
      assertThat(manifest.totalRecordCount()).isEqualTo(300);
    }

    @Test
    @DisplayName("空 entries 列表 - 应该创建空清单")
    void emptyEntries_shouldCreateEmptyManifest() {
      // When
      var manifest = new OpenAlexManifest(List.of(), 0L, 0);

      // Then
      assertThat(manifest.entries()).isEmpty();
      assertThat(manifest.totalRecordCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("null entries - 应该抛出 NullPointerException")
    void nullEntries_shouldThrowException() {
      // When & Then
      assertThatThrownBy(() -> new OpenAlexManifest(null, 0L, 0))
          .isInstanceOf(NullPointerException.class);
    }
  }

  @Nested
  @DisplayName("getAllS3Paths() 方法测试")
  class GetAllS3PathsTest {

    @Test
    @DisplayName("应该返回所有 S3 路径")
    void shouldReturnAllS3Paths() {
      // Given
      var entries =
          List.of(
              new OpenAlexManifest.Entry(
                  "s3://openalex/data/sources/updated_date=2025-11-02/part_000.gz", 1000L, 100),
              new OpenAlexManifest.Entry(
                  "s3://openalex/data/sources/updated_date=2025-10-27/part_000.gz", 2000L, 200));
      var manifest = new OpenAlexManifest(entries, 3000L, 300);

      // When
      List<String> paths = manifest.getAllS3Paths();

      // Then
      assertThat(paths)
          .containsExactly(
              "s3://openalex/data/sources/updated_date=2025-11-02/part_000.gz",
              "s3://openalex/data/sources/updated_date=2025-10-27/part_000.gz");
    }

    @Test
    @DisplayName("空清单应该返回空列表")
    void emptyManifest_shouldReturnEmptyList() {
      // Given
      var manifest = new OpenAlexManifest(List.of(), 0L, 0);

      // When
      List<String> paths = manifest.getAllS3Paths();

      // Then
      assertThat(paths).isEmpty();
    }
  }

  @Nested
  @DisplayName("getAllHttpPaths() 方法测试")
  class GetAllHttpPathsTest {

    @Test
    @DisplayName("应该将 S3 路径转换为 HTTP 路径")
    void shouldConvertS3PathsToHttpPaths() {
      // Given
      var entries =
          List.of(
              new OpenAlexManifest.Entry(
                  "s3://openalex/data/sources/updated_date=2025-11-02/part_000.gz", 1000L, 100),
              new OpenAlexManifest.Entry(
                  "s3://openalex/data/sources/updated_date=2025-10-27/part_000.gz", 2000L, 200));
      var manifest = new OpenAlexManifest(entries, 3000L, 300);

      // When
      List<String> httpPaths = manifest.getAllHttpPaths();

      // Then
      assertThat(httpPaths)
          .containsExactly(
              "https://openalex.s3.amazonaws.com/data/sources/updated_date=2025-11-02/part_000.gz",
              "https://openalex.s3.amazonaws.com/data/sources/updated_date=2025-10-27/part_000.gz");
    }
  }

  @Nested
  @DisplayName("getRelativePaths() 方法测试")
  class GetRelativePathsTest {

    @Test
    @DisplayName("应该提取相对路径（用于缓存键）")
    void shouldExtractRelativePaths() {
      // Given
      var entries =
          List.of(
              new OpenAlexManifest.Entry(
                  "s3://openalex/data/sources/updated_date=2025-11-02/part_000.gz", 1000L, 100),
              new OpenAlexManifest.Entry(
                  "s3://openalex/data/sources/updated_date=2025-10-27/part_000.gz", 2000L, 200));
      var manifest = new OpenAlexManifest(entries, 3000L, 300);

      // When
      List<String> relativePaths = manifest.getRelativePaths();

      // Then
      assertThat(relativePaths)
          .containsExactly(
              "updated_date=2025-11-02/part_000.gz", "updated_date=2025-10-27/part_000.gz");
    }
  }

  @Nested
  @DisplayName("Entry 记录测试")
  class EntryTest {

    @Test
    @DisplayName("应该正确获取 Entry 属性")
    void shouldGetEntryProperties() {
      // Given
      var entry =
          new OpenAlexManifest.Entry(
              "s3://openalex/data/sources/updated_date=2025-11-02/part_000.gz", 1234567L, 500);

      // Then
      assertThat(entry.url())
          .isEqualTo("s3://openalex/data/sources/updated_date=2025-11-02/part_000.gz");
      assertThat(entry.contentLength()).isEqualTo(1234567L);
      assertThat(entry.recordCount()).isEqualTo(500);
    }

    @Test
    @DisplayName("应该正确提取相对路径")
    void shouldExtractRelativePath() {
      // Given
      var entry =
          new OpenAlexManifest.Entry(
              "s3://openalex/data/sources/updated_date=2025-11-02/part_000.gz", 1000L, 100);

      // When
      String relativePath = entry.getRelativePath();

      // Then
      assertThat(relativePath).isEqualTo("updated_date=2025-11-02/part_000.gz");
    }

    @Test
    @DisplayName("应该正确转换为 HTTP URL")
    void shouldConvertToHttpUrl() {
      // Given
      var entry =
          new OpenAlexManifest.Entry(
              "s3://openalex/data/sources/updated_date=2025-11-02/part_000.gz", 1000L, 100);

      // When
      String httpUrl = entry.toHttpUrl();

      // Then
      assertThat(httpUrl)
          .isEqualTo(
              "https://openalex.s3.amazonaws.com/data/sources/updated_date=2025-11-02/part_000.gz");
    }
  }
}
