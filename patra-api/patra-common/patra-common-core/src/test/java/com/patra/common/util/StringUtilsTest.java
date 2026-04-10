package com.patra.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/// StringUtils 单元测试。
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("StringUtils")
@Timeout(value = 2, unit = TimeUnit.SECONDS)
class StringUtilsTest {

  @Nested
  @DisplayName("trimToNull")
  class TrimToNullTests {

    @Test
    @DisplayName("对 null 输入应返回 null")
    void should_return_null_for_null_input() {
      assertThat(StringUtils.trimToNull(null)).isNull();
    }

    @Test
    @DisplayName("对空字符串应返回 null")
    void should_return_null_for_empty_string() {
      assertThat(StringUtils.trimToNull("")).isNull();
    }

    @Test
    @DisplayName("对纯空白字符串应返回 null")
    void should_return_null_for_blank_string() {
      assertThat(StringUtils.trimToNull("   ")).isNull();
    }

    @Test
    @DisplayName("应去除首尾空白并返回")
    void should_trim_and_return_non_blank_string() {
      assertThat(StringUtils.trimToNull("  hello  ")).isEqualTo("hello");
    }

    @Test
    @DisplayName("对无空白的正常字符串应原样返回")
    void should_return_as_is_for_normal_string() {
      assertThat(StringUtils.trimToNull("hello")).isEqualTo("hello");
    }
  }

  @Nested
  @DisplayName("escapeLike")
  class EscapeLikeTests {

    @Test
    @DisplayName("对 null 输入应返回 null")
    void should_return_null_for_null_input() {
      assertThat(StringUtils.escapeLike(null)).isNull();
    }

    @Test
    @DisplayName("对空字符串应返回空字符串")
    void should_return_empty_for_empty_string() {
      assertThat(StringUtils.escapeLike("")).isEmpty();
    }

    @Test
    @DisplayName("不含通配符的字符串应原样返回")
    void should_return_as_is_when_no_wildcards() {
      assertThat(StringUtils.escapeLike("nature")).isEqualTo("nature");
    }

    @Test
    @DisplayName("应转义 % 为 !%")
    void should_escape_percent() {
      assertThat(StringUtils.escapeLike("nat%re")).isEqualTo("nat!%re");
    }

    @Test
    @DisplayName("应转义 _ 为 !_")
    void should_escape_underscore() {
      assertThat(StringUtils.escapeLike("nat_re")).isEqualTo("nat!_re");
    }

    @Test
    @DisplayName("应转义 ! 为 !!（转义字符本身必须先行转义）")
    void should_escape_the_escape_character_itself() {
      assertThat(StringUtils.escapeLike("nat!re")).isEqualTo("nat!!re");
    }

    @Test
    @DisplayName("应处理混合通配符（%、_ 和转义字符同时出现）")
    void should_escape_mixed_wildcards() {
      assertThat(StringUtils.escapeLike("a_b%c!d")).isEqualTo("a!_b!%c!!d");
    }

    @Test
    @DisplayName("只含通配符的字符串应被完整转义")
    void should_escape_all_wildcards() {
      assertThat(StringUtils.escapeLike("%_!")).isEqualTo("!%!_!!");
    }

    @Test
    @DisplayName("正常的 ASCII 标点不应被转义")
    void should_not_escape_other_punctuation() {
      assertThat(StringUtils.escapeLike("a-b.c d")).isEqualTo("a-b.c d");
    }
  }
}
