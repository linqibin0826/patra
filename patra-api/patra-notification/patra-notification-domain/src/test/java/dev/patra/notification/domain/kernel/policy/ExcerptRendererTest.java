package dev.patra.notification.domain.kernel.policy;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.notification.domain.kernel.model.read.RenderedExcerpt;
import java.util.Map;
import org.junit.jupiter.api.Test;

/// 正文纯文本渲染成摘录（PAP-128 交接的截断规则）。
class ExcerptRendererTest {

  private static final Map<Long, String> NAMES = Map.of(1L, "zhou");

  /// `<@ID>` 换成 `@名字`。
  @Test
  void should_replace_mentions_with_names() {
    RenderedExcerpt excerpt = ExcerptRenderer.render("谢谢 <@1> 分享", NAMES, 120, true);

    assertThat(excerpt).isEqualTo(RenderedExcerpt.of("谢谢 @zhou 分享", false));
  }

  /// 名字表里没有的人按已注销显示。
  @Test
  void should_name_missing_user_as_deleted() {
    assertThat(ExcerptRenderer.render("<@2>", NAMES, 120, true).text()).isEqualTo("@已注销用户");
  }

  /// 单行渠道把换行压成空格，多行渠道保留。
  @Test
  void should_collapse_newlines_when_single_line() {
    assertThat(ExcerptRenderer.render("a\nb", NAMES, 120, true).text()).isEqualTo("a b");
    assertThat(ExcerptRenderer.render("a\nb", NAMES, 120, false).text()).isEqualTo("a\nb");
  }

  /// 按扩展字素簇数：超出上限截断并标记；带肤色的 emoji 算一个。
  @Test
  void should_cut_to_max_graphemes() {
    RenderedExcerpt cut = ExcerptRenderer.render("字".repeat(130), NAMES, 120, true);
    RenderedExcerpt emoji = ExcerptRenderer.render("👍🏽👍🏽", NAMES, 1, true);

    assertThat(cut).isEqualTo(RenderedExcerpt.of("字".repeat(120), true));
    assertThat(emoji).isEqualTo(RenderedExcerpt.of("👍🏽", true));
  }

  /// `@名字` 整个放得下才放，放不下就停在它前面。
  @Test
  void should_stop_before_mention_that_does_not_fit() {
    RenderedExcerpt excerpt = ExcerptRenderer.render("字".repeat(118) + "<@1>", NAMES, 120, true);

    assertThat(excerpt).isEqualTo(RenderedExcerpt.of("字".repeat(118), true));
  }

  /// 截掉了东西时去掉末尾空白（换行先压成空格再数）。
  @Test
  void should_collapse_newlines_and_strip_when_cut() {
    RenderedExcerpt excerpt = ExcerptRenderer.render("字".repeat(119) + " \n\n后面", NAMES, 120, true);

    assertThat(excerpt).isEqualTo(RenderedExcerpt.of("字".repeat(119), true));
  }

  /// 正好放满不算截断。
  @Test
  void should_not_mark_truncated_when_exactly_fits() {
    assertThat(ExcerptRenderer.render("字".repeat(120), NAMES, 120, true).truncated()).isFalse();
  }

  /// 超出 long 的 19 位数字不是合法用户 ID：当普通文字原样输出。
  @Test
  void should_keep_overflowing_mention_as_literal() {
    assertThat(ExcerptRenderer.render("<@9999999999999999999>", NAMES, 120, true).text())
        .isEqualTo("<@9999999999999999999>");
  }
}
