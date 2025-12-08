package com.patra.catalog.domain.model.vo.venue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/// VenueInitializeParams 单元测试。
///
/// **测试策略**：
///
/// - 验证构造函数参数校验
/// - 验证工厂方法
/// - 验证辅助方法
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("VenueInitializeParams 单元测试")
@Timeout(value = 2, unit = TimeUnit.SECONDS)
class VenueInitializeParamsTest {

  @Nested
  @DisplayName("构造函数测试")
  class ConstructorTest {

    @Test
    @DisplayName("有效参数 - 应该成功创建")
    void validParams_shouldCreateSuccessfully() {
      // Given
      List<String> partitionUrls =
          List.of(
              "https://openalex.s3.amazonaws.com/data/sources/updated_date=2025-11-02/part_000.gz",
              "https://openalex.s3.amazonaws.com/data/sources/updated_date=2025-10-27/part_000.gz");

      // When
      VenueInitializeParams params = new VenueInitializeParams(partitionUrls);

      // Then
      assertThat(params.partitionUrls()).hasSize(2);
    }

    @Test
    @DisplayName("单个 URL - 应该成功创建")
    void singleUrl_shouldCreateSuccessfully() {
      // Given
      List<String> partitionUrls =
          List.of("https://openalex.s3.amazonaws.com/data/sources/part_000.gz");

      // When
      VenueInitializeParams params = new VenueInitializeParams(partitionUrls);

      // Then
      assertThat(params.partitionUrls()).hasSize(1);
    }

    @Test
    @DisplayName("null partitionUrls - 应该抛出 IllegalArgumentException")
    void nullPartitionUrls_shouldThrowException() {
      // When & Then
      assertThatThrownBy(() -> new VenueInitializeParams(null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("partitionUrls");
    }

    @Test
    @DisplayName("空 partitionUrls 列表 - 应该抛出 IllegalArgumentException")
    void emptyPartitionUrls_shouldThrowException() {
      // When & Then
      assertThatThrownBy(() -> new VenueInitializeParams(List.of()))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("partitionUrls");
    }
  }

  @Nested
  @DisplayName("工厂方法测试")
  class FactoryMethodTest {

    @Test
    @DisplayName("of() - 应该创建导入参数")
    void of_shouldCreateParams() {
      // Given
      List<String> partitionUrls =
          List.of("https://openalex.s3.amazonaws.com/data/sources/part_000.gz");

      // When
      VenueInitializeParams params = VenueInitializeParams.of(partitionUrls);

      // Then
      assertThat(params.partitionUrls()).isEqualTo(partitionUrls);
    }

    @Test
    @DisplayName("of() - 应该验证参数")
    void of_shouldValidateParams() {
      // When & Then
      assertThatThrownBy(() -> VenueInitializeParams.of(null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("partitionUrls");
    }

    @Test
    @DisplayName("of() - 空列表应该抛出异常")
    void of_emptyList_shouldThrowException() {
      // When & Then
      assertThatThrownBy(() -> VenueInitializeParams.of(List.of()))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("partitionUrls");
    }
  }

  @Nested
  @DisplayName("辅助方法测试")
  class HelperMethodTest {

    @Test
    @DisplayName("getPartitionUrlsAsString() - 应该返回逗号分隔的 URL")
    void getPartitionUrlsAsString_shouldReturnCommaSeparatedUrls() {
      // Given
      List<String> partitionUrls =
          List.of(
              "https://example.com/a.gz", "https://example.com/b.gz", "https://example.com/c.gz");
      VenueInitializeParams params = new VenueInitializeParams(partitionUrls);

      // When
      String urlsStr = params.getPartitionUrlsAsString();

      // Then
      assertThat(urlsStr)
          .isEqualTo("https://example.com/a.gz,https://example.com/b.gz,https://example.com/c.gz");
    }

    @Test
    @DisplayName("getPartitionCount() - 应该返回分区数量")
    void getPartitionCount_shouldReturnPartitionCount() {
      // Given
      List<String> partitionUrls = List.of("https://example.com/a.gz", "https://example.com/b.gz");
      VenueInitializeParams params = new VenueInitializeParams(partitionUrls);

      // When
      int count = params.getPartitionCount();

      // Then
      assertThat(count).isEqualTo(2);
    }
  }
}
