package com.patra.ingest.domain.model.vo.plan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("PlanMetadata 值对象测试")
class PlanMetadataTest {

  @Nested
  @DisplayName("构造器验证")
  class ConstructorValidation {

    @Test
    @DisplayName("应该成功创建包含有效数据的元数据")
    void shouldCreateValidMetadata() {
      // Given
      int totalCount = 100;
      String webEnv = "NCID_1_12345678_130.14.22.33_9001_1234567890";
      String queryKey = "1";

      // When
      PlanMetadata metadata = new PlanMetadata(totalCount, webEnv, queryKey);

      // Then
      assertThat(metadata.totalCount()).isEqualTo(totalCount);
      assertThat(metadata.webEnv()).isEqualTo(webEnv);
      assertThat(metadata.queryKey()).isEqualTo(queryKey);
    }

    @Test
    @DisplayName("应该成功创建不包含 WebEnv 的元数据")
    void shouldCreateMetadataWithoutWebEnv() {
      // Given
      int totalCount = 50;

      // When & Then
      assertThatCode(() -> new PlanMetadata(totalCount, null, null))
          .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("应该成功创建 totalCount 为 0 的元数据")
    void shouldCreateMetadataWithZeroCount() {
      // When & Then
      assertThatCode(() -> new PlanMetadata(0, null, null))
          .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("应该拒绝负数的 totalCount")
    void shouldRejectNegativeTotalCount() {
      // When & Then
      assertThatThrownBy(() -> new PlanMetadata(-1, null, null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("totalCount必须 >= 0");
    }

    @Test
    @DisplayName("应该拒绝仅提供 webEnv 而无 queryKey")
    void shouldRejectWebEnvWithoutQueryKey() {
      // Given
      String webEnv = "NCID_1_12345678_130.14.22.33_9001_1234567890";

      // When & Then
      assertThatThrownBy(() -> new PlanMetadata(100, webEnv, null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("webEnv和queryKey必须同时存在或同时为空");
    }

    @Test
    @DisplayName("应该拒绝仅提供 queryKey 而无 webEnv")
    void shouldRejectQueryKeyWithoutWebEnv() {
      // Given
      String queryKey = "1";

      // When & Then
      assertThatThrownBy(() -> new PlanMetadata(100, null, queryKey))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("webEnv和queryKey必须同时存在或同时为空");
    }

    @Test
    @DisplayName("应该拒绝空白的 webEnv")
    void shouldRejectBlankWebEnv() {
      // When & Then
      assertThatThrownBy(() -> new PlanMetadata(100, "  ", "1"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("webEnv和queryKey必须同时存在或同时为空");
    }

    @Test
    @DisplayName("应该拒绝空白的 queryKey")
    void shouldRejectBlankQueryKey() {
      // Given
      String webEnv = "NCID_1_12345678_130.14.22.33_9001_1234567890";

      // When & Then
      assertThatThrownBy(() -> new PlanMetadata(100, webEnv, "  "))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("webEnv和queryKey必须同时存在或同时为空");
    }

    @Test
    @DisplayName("应该拒绝空字符串的 webEnv")
    void shouldRejectEmptyWebEnv() {
      // When & Then
      assertThatThrownBy(() -> new PlanMetadata(100, "", "1"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("webEnv和queryKey必须同时存在或同时为空");
    }

    @Test
    @DisplayName("应该拒绝空字符串的 queryKey")
    void shouldRejectEmptyQueryKey() {
      // Given
      String webEnv = "NCID_1_12345678_130.14.22.33_9001_1234567890";

      // When & Then
      assertThatThrownBy(() -> new PlanMetadata(100, webEnv, ""))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("webEnv和queryKey必须同时存在或同时为空");
    }
  }

  @Nested
  @DisplayName("静态工厂方法")
  class FactoryMethods {

    @Test
    @DisplayName("empty() 应该返回 totalCount 为 0 且无 WebEnv 的元数据")
    void emptyShouldReturnZeroCountMetadata() {
      // When
      PlanMetadata metadata = PlanMetadata.empty();

      // Then
      assertThat(metadata.totalCount()).isZero();
      assertThat(metadata.webEnv()).isNull();
      assertThat(metadata.queryKey()).isNull();
    }

    @Test
    @DisplayName("empty() 应该返回没有 WebEnv 句柄的元数据")
    void emptyShouldReturnMetadataWithoutWebEnv() {
      // When
      PlanMetadata metadata = PlanMetadata.empty();

      // Then
      assertThat(metadata.hasWebEnv()).isFalse();
    }
  }

  @Nested
  @DisplayName("hasWebEnv 方法")
  class HasWebEnvMethod {

    @Test
    @DisplayName("当 webEnv 和 queryKey 都存在时应该返回 true")
    void shouldReturnTrueWhenBothPresent() {
      // Given
      PlanMetadata metadata = new PlanMetadata(
          100,
          "NCID_1_12345678_130.14.22.33_9001_1234567890",
          "1"
      );

      // When
      boolean result = metadata.hasWebEnv();

      // Then
      assertThat(result).isTrue();
    }

    @Test
    @DisplayName("当 webEnv 和 queryKey 都为 null 时应该返回 false")
    void shouldReturnFalseWhenBothNull() {
      // Given
      PlanMetadata metadata = new PlanMetadata(100, null, null);

      // When
      boolean result = metadata.hasWebEnv();

      // Then
      assertThat(result).isFalse();
    }
  }

  @Nested
  @DisplayName("Record 语义")
  class RecordSemantics {

    @Test
    @DisplayName("相同值的实例应该相等")
    void shouldBeEqualForSameValues() {
      // Given
      PlanMetadata metadata1 = new PlanMetadata(
          100,
          "NCID_1_12345678_130.14.22.33_9001_1234567890",
          "1"
      );
      PlanMetadata metadata2 = new PlanMetadata(
          100,
          "NCID_1_12345678_130.14.22.33_9001_1234567890",
          "1"
      );

      // Then
      assertThat(metadata1).isEqualTo(metadata2);
      assertThat(metadata1.hashCode()).isEqualTo(metadata2.hashCode());
    }

    @Test
    @DisplayName("不同 totalCount 的实例应该不相等")
    void shouldNotBeEqualForDifferentTotalCount() {
      // Given
      PlanMetadata metadata1 = new PlanMetadata(100, null, null);
      PlanMetadata metadata2 = new PlanMetadata(200, null, null);

      // Then
      assertThat(metadata1).isNotEqualTo(metadata2);
    }

    @Test
    @DisplayName("不同 webEnv 的实例应该不相等")
    void shouldNotBeEqualForDifferentWebEnv() {
      // Given
      PlanMetadata metadata1 = new PlanMetadata(
          100,
          "NCID_1_12345678_130.14.22.33_9001_1234567890",
          "1"
      );
      PlanMetadata metadata2 = new PlanMetadata(
          100,
          "NCID_1_98765432_130.14.22.33_9001_9876543210",
          "1"
      );

      // Then
      assertThat(metadata1).isNotEqualTo(metadata2);
    }

    @Test
    @DisplayName("不同 queryKey 的实例应该不相等")
    void shouldNotBeEqualForDifferentQueryKey() {
      // Given
      String webEnv = "NCID_1_12345678_130.14.22.33_9001_1234567890";
      PlanMetadata metadata1 = new PlanMetadata(100, webEnv, "1");
      PlanMetadata metadata2 = new PlanMetadata(100, webEnv, "2");

      // Then
      assertThat(metadata1).isNotEqualTo(metadata2);
    }

    @Test
    @DisplayName("toString 应该包含所有字段")
    void toStringShouldContainAllFields() {
      // Given
      PlanMetadata metadata = new PlanMetadata(
          100,
          "NCID_1_12345678_130.14.22.33_9001_1234567890",
          "1"
      );

      // When
      String result = metadata.toString();

      // Then
      assertThat(result)
          .contains("totalCount")
          .contains("100")
          .contains("webEnv")
          .contains("NCID_1_12345678_130.14.22.33_9001_1234567890")
          .contains("queryKey")
          .contains("1");
    }

    @Test
    @DisplayName("与 null 比较应该返回 false")
    void shouldNotEqualNull() {
      // Given
      PlanMetadata metadata = new PlanMetadata(100, null, null);

      // Then
      assertThat(metadata).isNotEqualTo(null);
    }

    @Test
    @DisplayName("与不同类型比较应该返回 false")
    void shouldNotEqualDifferentType() {
      // Given
      PlanMetadata metadata = new PlanMetadata(100, null, null);

      // Then
      assertThat(metadata).isNotEqualTo("not a PlanMetadata");
    }

    @Test
    @DisplayName("与自身比较应该返回 true")
    void shouldEqualSelf() {
      // Given
      PlanMetadata metadata = new PlanMetadata(100, null, null);

      // Then
      assertThat(metadata).isEqualTo(metadata);
    }
  }

  @Nested
  @DisplayName("边界条件")
  class BoundaryConditions {

    @Test
    @DisplayName("应该处理极大的 totalCount")
    void shouldHandleLargeTotalCount() {
      // Given
      int maxInt = Integer.MAX_VALUE;

      // When & Then
      assertThatCode(() -> new PlanMetadata(maxInt, null, null))
          .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("应该处理极长的 webEnv 字符串")
    void shouldHandleLongWebEnv() {
      // Given
      String longWebEnv = "A".repeat(1000);
      String queryKey = "1";

      // When & Then
      assertThatCode(() -> new PlanMetadata(100, longWebEnv, queryKey))
          .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("应该处理极长的 queryKey 字符串")
    void shouldHandleLongQueryKey() {
      // Given
      String webEnv = "NCID_1_12345678_130.14.22.33_9001_1234567890";
      String longQueryKey = "1".repeat(1000);

      // When & Then
      assertThatCode(() -> new PlanMetadata(100, webEnv, longQueryKey))
          .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("应该处理包含特殊字符的 webEnv")
    void shouldHandleSpecialCharactersInWebEnv() {
      // Given
      String webEnv = "WebEnv_with-special.chars_123";
      String queryKey = "1";

      // When
      PlanMetadata metadata = new PlanMetadata(100, webEnv, queryKey);

      // Then
      assertThat(metadata.webEnv()).isEqualTo(webEnv);
      assertThat(metadata.hasWebEnv()).isTrue();
    }

    @Test
    @DisplayName("应该处理包含特殊字符的 queryKey")
    void shouldHandleSpecialCharactersInQueryKey() {
      // Given
      String webEnv = "NCID_1_12345678_130.14.22.33_9001_1234567890";
      String queryKey = "queryKey-with.special_chars";

      // When
      PlanMetadata metadata = new PlanMetadata(100, webEnv, queryKey);

      // Then
      assertThat(metadata.queryKey()).isEqualTo(queryKey);
      assertThat(metadata.hasWebEnv()).isTrue();
    }
  }
}
