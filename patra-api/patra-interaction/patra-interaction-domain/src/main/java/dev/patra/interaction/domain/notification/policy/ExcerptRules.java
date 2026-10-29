package dev.patra.interaction.domain.notification.policy;

import dev.patra.interaction.domain.notification.model.vo.ExcerptText;
import dev.patra.interaction.domain.notification.model.vo.ExcerptText.Literal;
import dev.patra.interaction.domain.notification.model.vo.ExcerptText.Mention;
import dev.patra.interaction.domain.notification.model.vo.ExcerptText.Segment;
import dev.patra.interaction.domain.notification.model.vo.RenderedExcerpt;
import dev.patra.interaction.domain.subject.model.read.UserCard;
import java.text.BreakIterator;
import java.util.Map;

/// 摘录的长度与读取时的渲染（spec 6.2）。
///
/// 写入时存评论纯文本的前 {@link #MAX_STORED_UNITS} 个单位；读取时把提及换成名字，再截到
/// {@link #MAX_RENDERED_GRAPHEMES} 个扩展字素簇。存得比显示的长，是给换名字后的长度变化留余量。
public final class ExcerptRules {

  /// 存储的单位上限（字面字素各 1、提及各 1）。
  public static final int MAX_STORED_UNITS = 300;

  /// 渲染后的字素上限。
  public static final int MAX_RENDERED_GRAPHEMES = 120;

  /// identity 不认识的人（已注销）在摘录里的写法。
  public static final String DELETED_USER = "已注销用户";

  /// 只有静态方法，不实例化。
  private ExcerptRules() {}

  /// 渲染：提及换成 `@用户名`（主页对收件人可见）/ `@昵称`（不可见）/ `@已注销用户`（不认识），再截断。
  ///
  /// @param excerpt 存储的摘录
  /// @param cards 提及到的用户的名片（访客是收件人）；缺了的按已注销
  /// @return 纯文本摘录
  public static RenderedExcerpt render(ExcerptText excerpt, Map<Long, UserCard> cards) {
    StringBuilder text = new StringBuilder();
    for (Segment segment : excerpt.segments()) {
      if (segment instanceof Mention mention) {
        text.append('@').append(nameOf(cards.get(mention.userId())));
      } else if (segment instanceof Literal literal) {
        text.append(literal.text());
      }
    }
    String full = text.toString();
    BreakIterator graphemes = BreakIterator.getCharacterInstance();
    graphemes.setText(full);
    int end = 0;
    int count = 0;
    while (count < MAX_RENDERED_GRAPHEMES && graphemes.next() != BreakIterator.DONE) {
      end = graphemes.current();
      count++;
    }
    boolean cut = end < full.length();
    return RenderedExcerpt.of(full.substring(0, end), excerpt.truncated() || cut);
  }

  /// 提及在摘录里显示的名字。
  ///
  /// @param card 名片，可空
  /// @return 名字（不含 `@`）
  private static String nameOf(UserCard card) {
    if (card == null) {
      return DELETED_USER;
    }
    if (card.profileVisible() && card.username() != null) {
      return card.username();
    }
    return card.displayName() != null ? card.displayName() : DELETED_USER;
  }
}
