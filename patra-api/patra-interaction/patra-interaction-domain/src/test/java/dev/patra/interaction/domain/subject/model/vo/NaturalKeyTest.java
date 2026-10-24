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
}
