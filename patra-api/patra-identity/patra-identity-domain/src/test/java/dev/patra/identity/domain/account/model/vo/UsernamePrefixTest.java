package dev.patra.identity.domain.account.model.vo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/// UsernamePrefix 单元测试：可能是某个合法用户名开头的才算前缀（PAP-118 spec 4.4）。
@DisplayName("UsernamePrefix 单元测试")
class UsernamePrefixTest {

  @ParameterizedTest
  @ValueSource(strings = {"a", "ab", "chen-", "chen_y", "123", "a-b_c"})
  @DisplayName("比用户名宽：不够 3 个字符、末尾是连接符、全是数字都算前缀")
  void should_accept_possible_username_beginnings(String raw) {
    assertThat(UsernamePrefix.parse(raw)).map(UsernamePrefix::value).contains(raw);
  }

  @Test
  @DisplayName("最长 30 个字符，和用户名一样")
  void should_accept_up_to_30_characters() {
    assertThat(UsernamePrefix.parse("a".repeat(30))).isPresent();
    assertThat(UsernamePrefix.parse("a".repeat(31))).isEmpty();
  }

  @Test
  @DisplayName("先去首尾空白、转小写，再判")
  void should_normalize_before_checking() {
    assertThat(UsernamePrefix.parse(" Chen-Y ")).map(UsernamePrefix::value).contains("chen-y");
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"   ", "-a", "_a", "a--", "a-_", "a b", "a%", "陈", "ｃｈｅｎ", "@chen"})
  @DisplayName("不可能是任何合法用户名开头的：空、连接符开头、连接符连续、用户名之外的字符")
  void should_reject_impossible_beginnings(String raw) {
    assertThat(UsernamePrefix.parse(raw)).isEmpty();
  }

  @Test
  @DisplayName("构造器只接受已规范化且合法的值")
  void should_only_construct_from_normalized_valid_value() {
    assertThatThrownBy(() -> new UsernamePrefix("Chen"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new UsernamePrefix("a--"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
