package dev.patra.interaction.domain.notification.model.vo;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.interaction.domain.notification.model.vo.ExcerptText.Literal;
import dev.patra.interaction.domain.notification.model.vo.ExcerptText.Mention;
import dev.patra.interaction.domain.subject.model.vo.PlainText;
import org.junit.jupiter.api.Test;

/// 摘录的存储写法：字面的 `\` 与 `<` 转义，提及存 `<@ID>`；按单位（字面字素各 1、提及各 1）截断。
class ExcerptTextTest {

  /// 字面里的反斜杠与 `<@7>` 原样还原，不会被当成提及。
  @Test
  void should_round_trip_literal_with_backslash_and_angle_bracket() {
    ExcerptText text = ExcerptText.builder().literal("a\\b<@7>c").build();

    assertThat(text.segments()).containsExactly(new Literal("a\\b<@7>c"));
    assertThat(text.mentionIds()).isEmpty();
    assertThat(text.truncated()).isFalse();
  }

  /// 提及存成 `<@ID>` 标记。
  @Test
  void should_keep_mention_as_token() {
    ExcerptText text = ExcerptText.builder().literal("hi ").mention(42).literal("!").build();

    assertThat(text.stored()).isEqualTo("hi <@42>!");
    assertThat(text.segments())
        .containsExactly(new Literal("hi "), new Mention(42), new Literal("!"));
    assertThat(text.mentionIds()).containsExactly(42L);
  }

  /// 超出时只留前 N 个单位：提及整个去掉不切开；多码点的 emoji 按一个字素算。
  @Test
  void should_cut_by_graphemes_without_splitting_tokens() {
    ExcerptText long300 =
        ExcerptText.builder().literal("中".repeat(300)).mention(1).build().cut(300);
    assertThat(long300.truncated()).isTrue();
    assertThat(long300.segments()).containsExactly(new Literal("中".repeat(300)));

    String family = "👨‍👩‍👧";
    ExcerptText emoji = ExcerptText.builder().literal(family.repeat(3)).build().cut(2);
    assertThat(emoji.truncated()).isTrue();
    assertThat(emoji.segments()).containsExactly(new Literal(family.repeat(2)));
  }

  /// 没超出时原样返回、不标截断。
  @Test
  void should_not_mark_truncated_when_within_limit() {
    ExcerptText text = ExcerptText.builder().literal("abc").build();

    ExcerptText cut = text.cut(300);

    assertThat(cut.truncated()).isFalse();
    assertThat(cut.stored()).isEqualTo(text.stored());
  }

  /// 从库里还原：截断标记照存的给。
  @Test
  void should_restore_from_stored_form() {
    ExcerptText text = ExcerptText.of("x \\<@1> <@2>", true);

    assertThat(text.segments()).containsExactly(new Literal("x <@1> "), new Mention(2));
    assertThat(text.truncated()).isTrue();
  }

  /// 从纯文本转来：`<@ID>` 成提及段，换行变空格（站内摘录是单行）。
  @Test
  void should_build_single_line_segments_from_plain_text() {
    ExcerptText text = ExcerptText.fromPlainText(PlainText.of("问 <@42>\n好"));

    assertThat(text.segments())
        .containsExactly(new Literal("问 "), new Mention(42), new Literal(" 好"));
    assertThat(text.truncated()).isFalse();
  }

  /// 断开过的同形文字仍是字面。
  @Test
  void should_keep_broken_token_literal() {
    String broken = "<" + PlainText.WORD_JOINER + "@7>";

    ExcerptText text = ExcerptText.fromPlainText(PlainText.of(broken));

    assertThat(text.segments()).containsExactly(new Literal(broken));
    assertThat(text.mentionIds()).isEmpty();
  }
}
