package com.patra.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/// StringUtils 单元测试。
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("StringUtils")
@Timeout(value = 2, unit = TimeUnit.SECONDS)
class StringUtilsTest {

  @Test
  @DisplayName("trimToNull 对 null 输入应返回 null")
  void should_return_null_for_null_input() {
    assertThat(StringUtils.trimToNull(null)).isNull();
  }

  @Test
  @DisplayName("trimToNull 对空字符串应返回 null")
  void should_return_null_for_empty_string() {
    assertThat(StringUtils.trimToNull("")).isNull();
  }

  @Test
  @DisplayName("trimToNull 对纯空白字符串应返回 null")
  void should_return_null_for_blank_string() {
    assertThat(StringUtils.trimToNull("   ")).isNull();
  }

  @Test
  @DisplayName("trimToNull 应去除首尾空白并返回")
  void should_trim_and_return_non_blank_string() {
    assertThat(StringUtils.trimToNull("  hello  ")).isEqualTo("hello");
  }

  @Test
  @DisplayName("trimToNull 对无空白的正常字符串应原样返回")
  void should_return_as_is_for_normal_string() {
    assertThat(StringUtils.trimToNull("hello")).isEqualTo("hello");
  }
}
