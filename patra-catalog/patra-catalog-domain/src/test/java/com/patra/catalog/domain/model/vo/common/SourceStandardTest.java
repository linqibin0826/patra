package com.patra.catalog.domain.model.vo.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/// 来源标准值对象单元测试。
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("SourceStandard 来源标准值对象")
@Timeout(2)
class SourceStandardTest {

  @Nested
  @DisplayName("构造函数验证")
  class ConstructorValidation {

    @Test
    @DisplayName("应成功创建来源标准")
    void shouldCreateSourceStandard() {
      SourceStandard standard = SourceStandard.of("NAME_EN");

      assertThat(standard.code()).isEqualTo("NAME_EN");
    }

    @Test
    @DisplayName("应自动去除空白字符")
    void shouldTrimWhitespace() {
      SourceStandard standard = SourceStandard.of("  NAME_EN  ");

      assertThat(standard.code()).isEqualTo("NAME_EN");
    }

    @ParameterizedTest
    @DisplayName("空值或空白字符串应抛出异常")
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t", "\n"})
    void shouldRejectBlankCode(String code) {
      assertThatThrownBy(() -> SourceStandard.of(code))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("来源标准代码不能为空");
    }
  }

  @Nested
  @DisplayName("预定义常量")
  class PredefinedConstants {

    @Test
    @DisplayName("NAME_EN 常量应正确定义")
    void shouldDefineNameEnConstant() {
      assertThat(SourceStandard.NAME_EN.code()).isEqualTo("NAME_EN");
    }

    @Test
    @DisplayName("ISO_3166_1_ALPHA2 常量应正确定义")
    void shouldDefineIso31661Alpha2Constant() {
      assertThat(SourceStandard.ISO_3166_1_ALPHA2.code()).isEqualTo("ISO_3166_1_ALPHA2");
    }

    @Test
    @DisplayName("ISO_3166_1_ALPHA3 常量应正确定义")
    void shouldDefineIso31661Alpha3Constant() {
      assertThat(SourceStandard.ISO_3166_1_ALPHA3.code()).isEqualTo("ISO_3166_1_ALPHA3");
    }

    @Test
    @DisplayName("ISO_639_3 常量应正确定义")
    void shouldDefineIso6393Constant() {
      assertThat(SourceStandard.ISO_639_3.code()).isEqualTo("ISO_639_3");
    }

    @Test
    @DisplayName("BCP_47 常量应正确定义")
    void shouldDefineBcp47Constant() {
      assertThat(SourceStandard.BCP_47.code()).isEqualTo("BCP_47");
    }
  }

  @Nested
  @DisplayName("equals 和 hashCode")
  class EqualsAndHashCode {

    @Test
    @DisplayName("相同代码的两个实例应相等")
    void shouldBeEqualForSameCode() {
      SourceStandard standard1 = SourceStandard.of("NAME_EN");
      SourceStandard standard2 = SourceStandard.of("NAME_EN");

      assertThat(standard1).isEqualTo(standard2);
      assertThat(standard1.hashCode()).isEqualTo(standard2.hashCode());
    }

    @Test
    @DisplayName("与预定义常量应相等")
    void shouldBeEqualToPredefinedConstant() {
      SourceStandard custom = SourceStandard.of("NAME_EN");

      assertThat(custom).isEqualTo(SourceStandard.NAME_EN);
    }

    @Test
    @DisplayName("不同代码应不相等")
    void shouldNotBeEqualForDifferentCode() {
      SourceStandard standard1 = SourceStandard.of("NAME_EN");
      SourceStandard standard2 = SourceStandard.of("ISO_639_3");

      assertThat(standard1).isNotEqualTo(standard2);
    }
  }

  @Nested
  @DisplayName("toString()")
  class ToStringMethod {

    @Test
    @DisplayName("toString 应返回代码值")
    void shouldReturnCodeValue() {
      SourceStandard standard = SourceStandard.of("NAME_EN");

      assertThat(standard.toString()).isEqualTo("NAME_EN");
    }
  }
}
