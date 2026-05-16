package dev.linqibin.patra.catalog.adapter.scheduler.config;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/// {@link RorDataSourceProperties} 单元测试。
///
/// **测试覆盖**：
///
/// - `@NotBlank` 约束验证
/// - 属性 setter/getter 功能
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("RorDataSourceProperties 单元测试")
@Timeout(value = 2, unit = TimeUnit.SECONDS)
class RorDataSourcePropertiesTest {

  private static Validator validator;

  @BeforeAll
  static void setUpValidator() {
    try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
      validator = factory.getValidator();
    }
  }

  @Nested
  @DisplayName("验证约束测试")
  class ValidationTests {

    @Test
    @DisplayName("downloadUrl 为 null 时 - 应产生验证错误")
    void shouldFailValidation_whenDownloadUrlIsNull() {
      // Given
      RorDataSourceProperties properties = new RorDataSourceProperties();
      properties.setDownloadUrl(null);

      // When
      Set<ConstraintViolation<RorDataSourceProperties>> violations = validator.validate(properties);

      // Then
      assertThat(violations).hasSize(1);
      assertThat(violations.iterator().next().getMessage())
          .isEqualTo("patra.catalog.ror.download-url 不能为空");
    }

    @Test
    @DisplayName("downloadUrl 为空字符串时 - 应产生验证错误")
    void shouldFailValidation_whenDownloadUrlIsEmpty() {
      // Given
      RorDataSourceProperties properties = new RorDataSourceProperties();
      properties.setDownloadUrl("");

      // When
      Set<ConstraintViolation<RorDataSourceProperties>> violations = validator.validate(properties);

      // Then
      assertThat(violations).hasSize(1);
      assertThat(violations.iterator().next().getMessage())
          .isEqualTo("patra.catalog.ror.download-url 不能为空");
    }

    @Test
    @DisplayName("downloadUrl 为空白字符串时 - 应产生验证错误")
    void shouldFailValidation_whenDownloadUrlIsBlank() {
      // Given
      RorDataSourceProperties properties = new RorDataSourceProperties();
      properties.setDownloadUrl("   ");

      // When
      Set<ConstraintViolation<RorDataSourceProperties>> violations = validator.validate(properties);

      // Then
      assertThat(violations).hasSize(1);
      assertThat(violations.iterator().next().getMessage())
          .isEqualTo("patra.catalog.ror.download-url 不能为空");
    }

    @Test
    @DisplayName("downloadUrl 有效时 - 应通过验证")
    void shouldPassValidation_whenDownloadUrlIsValid() {
      // Given
      RorDataSourceProperties properties = new RorDataSourceProperties();
      properties.setDownloadUrl(
          "https://zenodo.org/records/17953395/files/v2.0-2025-12-16-ror-data.zip?download=1");

      // When
      Set<ConstraintViolation<RorDataSourceProperties>> violations = validator.validate(properties);

      // Then
      assertThat(violations).isEmpty();
    }
  }

  @Nested
  @DisplayName("Getter/Setter 测试")
  class GetterSetterTests {

    @Test
    @DisplayName("setDownloadUrl - 应正确设置值")
    void setDownloadUrl_shouldSetValue() {
      // Given
      RorDataSourceProperties properties = new RorDataSourceProperties();
      String expectedUrl =
          "https://zenodo.org/records/17953395/files/v2.0-2025-12-16-ror-data.zip?download=1";

      // When
      properties.setDownloadUrl(expectedUrl);

      // Then
      assertThat(properties.getDownloadUrl()).isEqualTo(expectedUrl);
    }

    @Test
    @DisplayName("默认值 - downloadUrl 应为 null")
    void defaultValue_downloadUrlShouldBeNull() {
      // When
      RorDataSourceProperties properties = new RorDataSourceProperties();

      // Then
      assertThat(properties.getDownloadUrl()).isNull();
    }
  }
}
