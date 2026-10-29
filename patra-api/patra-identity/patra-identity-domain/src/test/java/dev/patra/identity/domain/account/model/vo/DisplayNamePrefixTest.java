package dev.patra.identity.domain.account.model.vo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/// DisplayNamePrefix 单元测试：可能是某个昵称开头的才算前缀，比较时不分大小写（PAP-118 spec 4.4）。
@DisplayName("DisplayNamePrefix 单元测试")
class DisplayNamePrefixTest {

  @ParameterizedTest
  @CsvSource({"张, 张", "Chen, chen", "' Chen  Yu ', chen yu", "-a, -a"})
  @DisplayName("与存库的昵称同一套规范化，再转小写")
  void should_normalize_and_lowercase(String raw, String value) {
    assertThat(DisplayNamePrefix.parse(raw)).map(DisplayNamePrefix::value).contains(value);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"   ", "a\u0007", "", "\uD800", "a​b"})
  @DisplayName("不可能是任何昵称开头的：空、控制字符、私用区、孤立代理项、零宽格式字符")
  void should_reject_impossible_beginnings(String raw) {
    assertThat(DisplayNamePrefix.parse(raw)).isEmpty();
  }

  @Test
  @DisplayName("最长 120 字节，存库的昵称不会更长")
  void should_accept_up_to_120_bytes() {
    assertThat(DisplayNamePrefix.parse("张".repeat(40))).isPresent();
    assertThat(DisplayNamePrefix.parse("张".repeat(41))).isEmpty();
  }

  @Test
  @DisplayName("增补平面的字符（emoji）照收")
  void should_keep_supplementary_characters() {
    assertThat(DisplayNamePrefix.parse("🍎")).map(DisplayNamePrefix::value).contains("🍎");
  }

  @Test
  @DisplayName("构造器只接受非空值")
  void should_only_construct_from_non_empty_value() {
    assertThatThrownBy(() -> new DisplayNamePrefix(null)).isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new DisplayNamePrefix(""))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
