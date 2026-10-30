package dev.patra.interaction.domain.subject.model.vo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/// 正文转成的纯文本：提及的写法与构造校验（PAP-128 spec 第 4 节）。
class PlainTextTest {

  /// 提及正则只认规范的十进制正整数，最多 19 位；断开过的不认。
  @Test
  void should_match_canonical_mention_token() {
    assertThat(PlainText.MENTION.matcher("<@42>").matches()).isTrue();
    assertThat(PlainText.MENTION.matcher("<@9999999999999999999>").matches()).isTrue();
    assertThat(PlainText.MENTION.matcher("<@0>").matches()).isFalse();
    assertThat(PlainText.MENTION.matcher("<@042>").matches()).isFalse();
    assertThat(PlainText.MENTION.matcher("<@>").matches()).isFalse();
    assertThat(PlainText.MENTION.matcher("<" + PlainText.WORD_JOINER + "@42>").find()).isFalse();
  }

  /// 文字不能为空引用。
  @Test
  void should_reject_null_value() {
    assertThatThrownBy(() -> PlainText.of(null)).isInstanceOf(NullPointerException.class);
  }
}
