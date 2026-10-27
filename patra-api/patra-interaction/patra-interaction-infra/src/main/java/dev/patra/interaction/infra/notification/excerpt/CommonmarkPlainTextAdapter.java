package dev.patra.interaction.infra.notification.excerpt;

import dev.patra.interaction.domain.notification.model.vo.ExcerptText;
import dev.patra.interaction.domain.notification.port.excerpt.PlainTextPort;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.BlockQuote;
import org.commonmark.node.BulletList;
import org.commonmark.node.Code;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.HardLineBreak;
import org.commonmark.node.Heading;
import org.commonmark.node.HtmlBlock;
import org.commonmark.node.HtmlInline;
import org.commonmark.node.IndentedCodeBlock;
import org.commonmark.node.ListItem;
import org.commonmark.node.Node;
import org.commonmark.node.OrderedList;
import org.commonmark.node.Paragraph;
import org.commonmark.node.SoftLineBreak;
import org.commonmark.node.Text;
import org.commonmark.node.ThematicBreak;
import org.commonmark.parser.Parser;
import org.springframework.stereotype.Component;

/// 用 commonmark-java 把评论 Markdown 转成摘录纯文本（spec 6.2 第 1 步）。
///
/// 遍历语法树只取文字：链接取链接文字、图片取替代文字、代码与 HTML 原样当文字（反引号行内代码里的标记是字面，同讨论）；块与换行之间放一个空格，最后合并连续空白。
/// 普通文字里的 `<@ID>` 且 ID 在事件名单里的才是提及。
@Component
public class CommonmarkPlainTextAdapter implements PlainTextPort {

  /// 解析器（线程安全，可共用）。
  private static final Parser PARSER = Parser.builder().build();

  /// 提及标记。
  private static final Pattern TOKEN = Pattern.compile("<@([1-9][0-9]{0,18})>");

  /// 连续空白（含全角空格、不换行空格）。
  private static final Pattern WHITESPACE = Pattern.compile("(?U)\\s+");

  /// 转纯文本。
  ///
  /// @param markdown 评论正文
  /// @param mentionedUserIds 事件给的提及名单
  /// @return 未截断的摘录
  @Override
  public ExcerptText toExcerpt(String markdown, Set<Long> mentionedUserIds) {
    TextCollector collector = new TextCollector(mentionedUserIds);
    PARSER.parse(markdown).accept(collector);
    return collector.build();
  }

  /// 收集文字的访问器：相邻的文字节点先攒着（标记可能被拆进几个节点），遇到别的节点再切提及。
  private static final class TextCollector extends AbstractVisitor {

    private final Set<Long> mentionedUserIds;
    private final List<Object> parts = new ArrayList<>();
    private final StringBuilder pendingText = new StringBuilder();
    private final StringBuilder literal = new StringBuilder();

    /// 构造。
    ///
    /// @param mentionedUserIds 提及名单
    private TextCollector(Set<Long> mentionedUserIds) {
      this.mentionedUserIds = mentionedUserIds;
    }

    /// 普通文字：先攒着。
    ///
    /// @param text 节点
    @Override
    public void visit(Text text) {
      pendingText.append(text.getLiteral());
    }

    /// 行内代码：字面。
    ///
    /// @param code 节点
    @Override
    public void visit(Code code) {
      appendLiteral(code.getLiteral());
    }

    /// 行内 HTML：字面。
    ///
    /// @param html 节点
    @Override
    public void visit(HtmlInline html) {
      appendLiteral(html.getLiteral());
    }

    /// 软换行：空格。
    ///
    /// @param lineBreak 节点
    @Override
    public void visit(SoftLineBreak lineBreak) {
      appendLiteral(" ");
    }

    /// 硬换行：空格。
    ///
    /// @param lineBreak 节点
    @Override
    public void visit(HardLineBreak lineBreak) {
      appendLiteral(" ");
    }

    /// 代码块：前后各一个空格，名单内的标记是提及。
    ///
    /// @param block 节点
    @Override
    public void visit(FencedCodeBlock block) {
      blockText(block.getLiteral());
    }

    /// 缩进代码块：前后各一个空格，名单内的标记是提及。
    ///
    /// @param block 节点
    @Override
    public void visit(IndentedCodeBlock block) {
      blockText(block.getLiteral());
    }

    /// HTML 块：同代码块。
    ///
    /// @param block 节点
    @Override
    public void visit(HtmlBlock block) {
      blockText(block.getLiteral());
    }

    /// 段落。
    ///
    /// @param paragraph 节点
    @Override
    public void visit(Paragraph paragraph) {
      block(paragraph);
    }

    /// 标题（门户不支持，取文字）。
    ///
    /// @param heading 节点
    @Override
    public void visit(Heading heading) {
      block(heading);
    }

    /// 引用。
    ///
    /// @param blockQuote 节点
    @Override
    public void visit(BlockQuote blockQuote) {
      block(blockQuote);
    }

    /// 无序列表。
    ///
    /// @param list 节点
    @Override
    public void visit(BulletList list) {
      block(list);
    }

    /// 有序列表。
    ///
    /// @param list 节点
    @Override
    public void visit(OrderedList list) {
      block(list);
    }

    /// 列表项。
    ///
    /// @param item 节点
    @Override
    public void visit(ListItem item) {
      block(item);
    }

    /// 分隔线：空格。
    ///
    /// @param thematicBreak 节点
    @Override
    public void visit(ThematicBreak thematicBreak) {
      appendLiteral(" ");
    }

    /// 块里的原文：前后各一个空格；里面名单内的标记也是提及（讨论只把反引号行内代码当字面，块里的 @ 照常解析）。
    ///
    /// @param text 原文
    private void blockText(String text) {
      appendLiteral(" ");
      pendingText.append(text);
      appendLiteral(" ");
    }

    /// 块级节点：前后各一个空格。
    ///
    /// @param node 节点
    private void block(Node node) {
      appendLiteral(" ");
      visitChildren(node);
      appendLiteral(" ");
    }

    /// 追加字面文字（先处理攒着的普通文字）。
    ///
    /// @param text 文字
    private void appendLiteral(String text) {
      flushText();
      literal.append(text);
    }

    /// 把攒着的普通文字按提及标记切开。
    private void flushText() {
      if (pendingText.isEmpty()) {
        return;
      }
      String text = pendingText.toString();
      pendingText.setLength(0);
      Matcher matcher = TOKEN.matcher(text);
      int last = 0;
      while (matcher.find()) {
        Long userId = parseUserId(matcher.group(1));
        if (userId != null && mentionedUserIds.contains(userId)) {
          literal.append(text, last, matcher.start());
          flushLiteral();
          parts.add(userId);
          last = matcher.end();
        }
      }
      literal.append(text, last, text.length());
    }

    /// 标记里的数字；超出 long 范围的（19 位数里比最大值大的）不是用户 ID，按字面文字处理。
    ///
    /// @param digits 数字
    /// @return 用户 ID，超出范围为 `null`
    private static Long parseUserId(String digits) {
      try {
        return Long.parseLong(digits);
      } catch (NumberFormatException ex) {
        return null;
      }
    }

    /// 结束当前字面段。
    private void flushLiteral() {
      if (!literal.isEmpty()) {
        parts.add(literal.toString());
        literal.setLength(0);
      }
    }

    /// 拼摘录：字面段合并空白，首段去开头空白、末段去结尾空白。
    ///
    /// @return 摘录
    private ExcerptText build() {
      flushText();
      flushLiteral();
      ExcerptText.Builder builder = ExcerptText.builder();
      for (int i = 0; i < parts.size(); i++) {
        if (parts.get(i) instanceof Long userId) {
          builder.mention(userId);
        } else {
          String text = WHITESPACE.matcher((String) parts.get(i)).replaceAll(" ");
          if (i == 0) {
            text = text.stripLeading();
          }
          if (i == parts.size() - 1) {
            text = text.stripTrailing();
          }
          if (!text.isEmpty()) {
            builder.literal(text);
          }
        }
      }
      return builder.build();
    }
  }
}
