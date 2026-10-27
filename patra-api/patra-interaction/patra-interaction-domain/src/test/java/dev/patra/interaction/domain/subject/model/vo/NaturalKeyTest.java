package dev.patra.interaction.domain.subject.model.vo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.patra.interaction.domain.subject.model.enums.NaturalKeyScheme;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/// 身份锚 `pmid:…` 的解析与输出。
class NaturalKeyTest {

  /// 落库格式往返不变。
  @Test
  void should_round_trip_pmid() {
    NaturalKey key = NaturalKey.parse("pmid:123");

    assertThat(key.scheme()).isEqualTo(NaturalKeyScheme.PMID);
    assertThat(key.value()).isEqualTo("123");
    assertThat(key).hasToString("pmid:123");
    assertThat(NaturalKey.of(NaturalKeyScheme.PMID, "123")).isEqualTo(key);
  }

  /// 前缀未知、缺失或 PMID 不合法时拒绝。
  ///
  /// @param raw 原文
  @ParameterizedTest
  @ValueSource(
      strings = {"pmid:0", "pmid:01", "pmid:1234567890123456", "123", "doi:10.1/x", "pmid:"})
  void should_reject_malformed(String raw) {
    assertThatThrownBy(() -> NaturalKey.parse(raw)).isInstanceOf(IllegalArgumentException.class);
  }

  /// NLM ID 按字符串原样往返：前导零、`R` / `A` 后缀、16 位的新式 ID 都保留。
  ///
  /// @param nlm NLM ID
  @ParameterizedTest
  @ValueSource(strings = {"0404401", "2985213R", "9300367A", "9918523075306676"})
  void should_round_trip_nlm_ids(String nlm) {
    NaturalKey key = NaturalKey.parse("nlm:" + nlm);

    assertThat(key).isEqualTo(NaturalKey.of(NaturalKeyScheme.NLM, nlm));
    assertThat(key).hasToString("nlm:" + nlm);
  }

  /// 小写后缀、只有后缀、空、超过 19 位数字、两个后缀都拒绝（区分大小写，不归一）。
  ///
  /// @param nlm NLM ID
  @ParameterizedTest
  @ValueSource(strings = {"2985213r", "R", "", "12345678901234567890", "0404401RR"})
  void should_reject_malformed_nlm_ids(String nlm) {
    assertThatThrownBy(() -> NaturalKey.of(NaturalKeyScheme.NLM, nlm))
        .isInstanceOf(IllegalArgumentException.class);
  }

  /// 帖子 ID 是规范的十进制正整数。
  @Test
  void should_round_trip_post_ids() {
    assertThat(NaturalKey.parse("post:1")).isEqualTo(NaturalKey.of(NaturalKeyScheme.POST, "1"));
    assertThat(NaturalKey.of(NaturalKeyScheme.POST, "9223372036854775807"))
        .hasToString("post:9223372036854775807");
  }

  /// 零、前导零、负数都拒绝。
  ///
  /// @param id 帖子 ID
  @ParameterizedTest
  @ValueSource(strings = {"0", "01", "-1"})
  void should_reject_malformed_post_ids(String id) {
    assertThatThrownBy(() -> NaturalKey.of(NaturalKeyScheme.POST, id))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
