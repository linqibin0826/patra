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
    assertThat(SubjectType.PUBLICATION.displayName()).isEqualTo("文献");
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
    assertThat(SubjectType.fromSlug("unknown")).isEmpty();
  }

  /// 文献可以收藏、讨论。
  @Test
  void should_support_collect_and_discuss_for_publication() {
    assertThat(SubjectType.PUBLICATION.supports(SubjectCapability.COLLECT)).isTrue();
    assertThat(SubjectType.PUBLICATION.supports(SubjectCapability.DISCUSS)).isTrue();
    assertThat(SubjectType.PUBLICATION.supports(SubjectCapability.SUBMISSION_EXPERIENCE)).isFalse();
  }

  /// 期刊：key 是 catalog 期刊 ID，身份锚是 NLM ID，只能挂投稿经验。
  @Test
  void should_define_journal() {
    assertThat(SubjectType.JOURNAL.code()).isEqualTo((short) 2);
    assertThat(SubjectType.JOURNAL.slug()).isEqualTo("journal");
    assertThat(SubjectType.JOURNAL.displayName()).isEqualTo("期刊");
    assertThat(SubjectType.JOURNAL.naturalKeyScheme()).isEqualTo(NaturalKeyScheme.NLM);
    assertThat(SubjectType.fromCode((short) 2)).isEqualTo(SubjectType.JOURNAL);
    assertThat(SubjectType.fromSlug("journal")).contains(SubjectType.JOURNAL);
    assertThat(SubjectType.JOURNAL.supports(SubjectCapability.SUBMISSION_EXPERIENCE)).isTrue();
    assertThat(SubjectType.JOURNAL.supports(SubjectCapability.COLLECT)).isFalse();
    assertThat(SubjectType.JOURNAL.supports(SubjectCapability.DISCUSS)).isFalse();
    assertThat(SubjectType.JOURNAL.parseKey("319059608675758187")).isEqualTo(319059608675758187L);
  }

  /// 投稿经验（帖子）：本地对象，key 是帖子 ID，只能讨论。
  @Test
  void should_define_post_as_local() {
    assertThat(SubjectType.POST.code()).isEqualTo((short) 3);
    assertThat(SubjectType.POST.slug()).isEqualTo("post");
    assertThat(SubjectType.POST.displayName()).isEqualTo("投稿经验");
    assertThat(SubjectType.POST.origin()).isEqualTo(SubjectOrigin.LOCAL);
    assertThat(SubjectType.POST.isLocal()).isTrue();
    assertThat(SubjectType.POST.naturalKeyScheme()).isEqualTo(NaturalKeyScheme.POST);
    assertThat(SubjectType.POST.supports(SubjectCapability.DISCUSS)).isTrue();
    assertThat(SubjectType.POST.supports(SubjectCapability.COLLECT)).isFalse();
    assertThat(SubjectType.PUBLICATION.isLocal()).isFalse();
    assertThat(SubjectType.JOURNAL.origin()).isEqualTo(SubjectOrigin.EXTERNAL);
  }

  /// 启动校验与对账只管外部类型。
  @Test
  void should_list_only_external_types() {
    assertThat(SubjectType.externalTypes())
        .containsExactly(SubjectType.PUBLICATION, SubjectType.JOURNAL);
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
