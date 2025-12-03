package com.patra.catalog.infra.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// OpenAlexCacheProperties 单元测试。
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("OpenAlexCacheProperties 单元测试")
class OpenAlexCachePropertiesTest {

  @Nested
  @DisplayName("默认值测试")
  class DefaultValuesTest {

    @Test
    @DisplayName("空参数 - 应该使用默认值")
    void nullParams_shouldUseDefaults() {
      // When
      OpenAlexCacheProperties props = new OpenAlexCacheProperties(false, null, null, null, null);

      // Then
      assertThat(props.enabled()).isFalse();
      assertThat(props.bucket()).isEqualTo("patra-catalog-cache");
      assertThat(props.keyPrefix()).isEqualTo("openalex/sources");
      assertThat(props.s3BaseUrl()).isEqualTo("https://openalex.s3.amazonaws.com");
      assertThat(props.s3SourcesPath()).isEqualTo("data/sources");
    }

    @Test
    @DisplayName("自定义参数 - 应该使用提供的值")
    void customParams_shouldUseProvidedValues() {
      // When
      OpenAlexCacheProperties props =
          new OpenAlexCacheProperties(
              true,
              "custom-bucket",
              "custom/prefix",
              "https://custom.s3.amazonaws.com",
              "custom/path");

      // Then
      assertThat(props.enabled()).isTrue();
      assertThat(props.bucket()).isEqualTo("custom-bucket");
      assertThat(props.keyPrefix()).isEqualTo("custom/prefix");
      assertThat(props.s3BaseUrl()).isEqualTo("https://custom.s3.amazonaws.com");
      assertThat(props.s3SourcesPath()).isEqualTo("custom/path");
    }
  }

  @Nested
  @DisplayName("URL 生成测试")
  class UrlGenerationTest {

    @Test
    @DisplayName("getManifestUrl() - 应该返回正确的 manifest URL")
    void getManifestUrl_shouldReturnCorrectUrl() {
      // Given
      OpenAlexCacheProperties props = new OpenAlexCacheProperties(true, null, null, null, null);

      // When
      String url = props.getManifestUrl();

      // Then
      assertThat(url).isEqualTo("https://openalex.s3.amazonaws.com/data/sources/manifest");
    }

    @Test
    @DisplayName("getPartitionUrl() - 应该返回正确的分区 URL")
    void getPartitionUrl_shouldReturnCorrectUrl() {
      // Given
      OpenAlexCacheProperties props = new OpenAlexCacheProperties(true, null, null, null, null);

      // When
      String url = props.getPartitionUrl("updated_date=2025-11-02/part_000.gz");

      // Then
      assertThat(url)
          .isEqualTo(
              "https://openalex.s3.amazonaws.com/data/sources/updated_date=2025-11-02/part_000.gz");
    }
  }

  @Nested
  @DisplayName("缓存键测试")
  class CacheKeyTest {

    @Test
    @DisplayName("getCacheKey() - 应该返回正确的缓存键")
    void getCacheKey_shouldReturnCorrectKey() {
      // Given
      OpenAlexCacheProperties props = new OpenAlexCacheProperties(true, null, null, null, null);

      // When
      String key = props.getCacheKey("updated_date=2025-11-02/part_000.gz");

      // Then
      assertThat(key).isEqualTo("openalex/sources/updated_date=2025-11-02/part_000.gz");
    }

    @Test
    @DisplayName("getManifestCacheKey() - 应该返回正确的 manifest 缓存键")
    void getManifestCacheKey_shouldReturnCorrectKey() {
      // Given
      OpenAlexCacheProperties props = new OpenAlexCacheProperties(true, null, null, null, null);

      // When
      String key = props.getManifestCacheKey();

      // Then
      assertThat(key).isEqualTo("openalex/sources/manifest");
    }
  }
}
