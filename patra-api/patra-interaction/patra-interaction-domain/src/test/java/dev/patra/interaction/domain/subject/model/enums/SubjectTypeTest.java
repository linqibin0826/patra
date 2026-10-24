package dev.patra.interaction.domain.subject.model.enums;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.patra.interaction.domain.subject.exception.InvalidSubjectKeyException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/// 能力矩阵：编码、slug、能力与对外 key 的解析。
class SubjectTypeTest {

  /// 编码与 slug 落库、进 URL，永不改。
  @Test
  void should_keep_stable_code_and_slug() {
    assertThat(SubjectType.PUBLICATION.code()).isEqualTo((short) 1);
    assertThat(SubjectType.PUBLICATION.slug()).isEqualTo("publication");
    assertThat(SubjectType.fromCode((short) 1)).isEqualTo(SubjectType.PUBLICATION);
    assertThat(SubjectType.fromSlug("publication")).contains(SubjectType.PUBLICATION);
    assertThat(SubjectType.PUBLICATION.naturalKeyScheme()).isEqualTo(NaturalKeyScheme.PMID);
  }

  /// 库里出现未知编码是程序错误。
  @Test
  void should_reject_unknown_code() {
    assertThatThrownBy(() -> SubjectType.fromCode((short) 99))
        .isInstanceOf(IllegalArgumentException.class);
  }

  /// 未知 slug 交给调用方翻译成 404。
  @Test
  void should_return_empty_for_unknown_slug() {
    assertThat(SubjectType.fromSlug("journal")).isEmpty();
  }

  /// 本版只开「收藏文献」。
  @Test
  void should_support_collect_for_publication() {
    assertThat(SubjectType.PUBLICATION.supports(SubjectCapability.COLLECT)).isTrue();
  }

  /// 文献的 key 是 catalog 内部 ID 的规范十进制。
  @Test
  void should_parse_canonical_positive_decimal() {
    assertThat(SubjectType.PUBLICATION.parseKey("1")).isEqualTo(1L);
    assertThat(SubjectType.PUBLICATION.parseKey("9223372036854775807")).isEqualTo(Long.MAX_VALUE);
  }

  /// 非规范写法一律拒绝。
  ///
  /// @param key 不合法的 key
  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"0", "-1", "01", "+1", " 1", "1 ", "abc", "9223372036854775808"})
  void should_reject_non_canonical_keys(String key) {
    assertThatThrownBy(() -> SubjectType.PUBLICATION.parseKey(key))
        .isInstanceOf(InvalidSubjectKeyException.class);
  }
}
