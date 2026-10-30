package dev.patra.interaction.domain.notification.model.vo;

import dev.patra.interaction.domain.subject.model.vo.PlainText;
import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;

/// 提醒摘录的存储写法：评论转成的纯文本，提及存 `<@ID>` 标记。
///
/// 字面文字里的 `\` 存成 `\\`、`<` 存成 `\<`，所以读取时不会把字面的 `<@123>`（行内代码里的、转义的）当成提及。
/// 用户名只存 identity：存储里只有用户 ID，名字在读取时换（见 `ExcerptRules`）。
///
/// @param stored 存储写法
/// @param truncated 存的时候是否已截断
public record ExcerptText(String stored, boolean truncated) {

  /// 校验。
  public ExcerptText {
    Objects.requireNonNull(stored, "stored");
  }

  /// 从库里还原。
  ///
  /// @param stored 存储写法
  /// @param truncated 是否已截断
  /// @return 摘录
  public static ExcerptText of(String stored, boolean truncated) {
    return new ExcerptText(stored, truncated);
  }

  /// 逐段拼一份摘录。
  ///
  /// @return 构建器
  public static Builder builder() {
    return new Builder();
  }

  /// 从正文转成的纯文本拼一份摘录（PAP-128）：`<@ID>` 成提及段，其余是字面段；站内提醒是单行，换行变空格。
  ///
  /// @param text 纯文本
  /// @return 未截断的摘录
  public static ExcerptText fromPlainText(PlainText text) {
    String singleLine = text.value().replace('\n', ' ');
    Builder builder = builder();
    Matcher matcher = PlainText.MENTION.matcher(singleLine);
    int last = 0;
    while (matcher.find()) {
      builder.literal(singleLine.substring(last, matcher.start()));
      builder.mention(Long.parseLong(matcher.group(1)));
      last = matcher.end();
    }
    builder.literal(singleLine.substring(last));
    return builder.build();
  }

  /// 解码成段：字面与提及交替。
  ///
  /// @return 段，相邻的字面已合并
  public List<Segment> segments() {
    List<Segment> segments = new ArrayList<>();
    StringBuilder literal = new StringBuilder();
    int i = 0;
    while (i < stored.length()) {
      char c = stored.charAt(i);
      if (c == '\\' && i + 1 < stored.length()) {
        literal.append(stored.charAt(i + 1));
        i += 2;
      } else if (c == '<' && stored.startsWith("<@", i) && stored.indexOf('>', i) > i + 2) {
        int end = stored.indexOf('>', i);
        if (literal.length() > 0) {
          segments.add(new Literal(literal.toString()));
          literal.setLength(0);
        }
        segments.add(new Mention(Long.parseLong(stored.substring(i + 2, end))));
        i = end + 1;
      } else {
        literal.append(c);
        i++;
      }
    }
    if (literal.length() > 0) {
      segments.add(new Literal(literal.toString()));
    }
    return List.copyOf(segments);
  }

  /// 提及到的用户。
  ///
  /// @return 用户 ID，按第一次出现的顺序去重
  public List<Long> mentionIds() {
    Set<Long> ids = new LinkedHashSet<>();
    for (Segment segment : segments()) {
      if (segment instanceof Mention mention) {
        ids.add(mention.userId());
      }
    }
    return List.copyOf(ids);
  }

  /// 只留前 `maxUnits` 个单位：字面的扩展字素簇各算 1，提及各算 1；超出时标截断，提及不切开。
  ///
  /// @param maxUnits 单位上限
  /// @return 截断后的摘录；没超出时原样返回
  public ExcerptText cut(int maxUnits) {
    Builder builder = builder();
    int used = 0;
    for (Segment segment : segments()) {
      if (used == maxUnits) {
        return builder.truncatedBuild();
      }
      if (segment instanceof Mention mention) {
        builder.mention(mention.userId());
        used++;
      } else if (segment instanceof Literal literal) {
        String text = literal.text();
        BreakIterator graphemes = BreakIterator.getCharacterInstance();
        graphemes.setText(text);
        int end = 0;
        while (used < maxUnits && graphemes.next() != BreakIterator.DONE) {
          end = graphemes.current();
          used++;
        }
        builder.literal(text.substring(0, end));
        if (end < text.length()) {
          return builder.truncatedBuild();
        }
      }
    }
    return this;
  }

  /// 摘录的一段。
  public sealed interface Segment permits Literal, Mention {}

  /// 字面文字。
  ///
  /// @param text 文字
  public record Literal(String text) implements Segment {}

  /// 提及。
  ///
  /// @param userId 被提及的用户
  public record Mention(long userId) implements Segment {}

  /// 逐段拼摘录。
  public static final class Builder {

    private final StringBuilder stored = new StringBuilder();

    /// 只经 {@link ExcerptText#builder()} 创建。
    private Builder() {}

    /// 追加字面文字（转义 `\` 与 `<`）。
    ///
    /// @param text 文字
    /// @return 自己
    public Builder literal(String text) {
      for (int i = 0; i < text.length(); i++) {
        char c = text.charAt(i);
        if (c == '\\' || c == '<') {
          stored.append('\\');
        }
        stored.append(c);
      }
      return this;
    }

    /// 追加一个提及。
    ///
    /// @param userId 被提及的用户
    /// @return 自己
    public Builder mention(long userId) {
      stored.append("<@").append(userId).append('>');
      return this;
    }

    /// 完成，不标截断。
    ///
    /// @return 摘录
    public ExcerptText build() {
      return new ExcerptText(stored.toString(), false);
    }

    /// 完成并标截断。
    ///
    /// @return 摘录
    private ExcerptText truncatedBuild() {
      return new ExcerptText(stored.toString(), true);
    }
  }
}
