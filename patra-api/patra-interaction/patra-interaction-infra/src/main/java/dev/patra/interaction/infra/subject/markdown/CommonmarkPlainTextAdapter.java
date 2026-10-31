package dev.patra.interaction.infra.subject.markdown;

import dev.patra.interaction.domain.subject.model.vo.PlainText;
import dev.patra.interaction.domain.subject.port.markdown.PlainTextPort;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.BlockQuote;
import org.commonmark.node.Code;
import org.commonmark.node.CustomNode;
import org.commonmark.node.HardLineBreak;
import org.commonmark.node.Heading;
import org.commonmark.node.Image;
import org.commonmark.node.Link;
import org.commonmark.node.LinkReferenceDefinition;
import org.commonmark.node.ListBlock;
import org.commonmark.node.ListItem;
import org.commonmark.node.Node;
import org.commonmark.node.OrderedList;
import org.commonmark.node.Paragraph;
import org.commonmark.node.SoftLineBreak;
import org.commonmark.node.Text;
import org.commonmark.parser.IncludeSourceSpans;
import org.commonmark.parser.Parser;
import org.springframework.stereotype.Component;

/// 用 commonmark-java 把正文 Markdown 转成纯文本（PAP-128，规则见
/// `docs/patra/specs/2026-10-30-plaintext-conversion-design.md` 第 3、4 节）。
///
/// 总原则：读者在门户上看到的那些字，去掉样式，结构只留换行。解析器配成门户的 Markdown 子集；门户按原文显示的
/// 写法（图片、跨行三反引号、列表项里套的列表、链接定义、引用式链接）靠源位置取回原文。
@Component
public class CommonmarkPlainTextAdapter implements PlainTextPort {

  /// 解析器（线程安全，可共用），对齐门户的子集：块级只认标题、引用、列表，缩进代码块、围栏代码块、HTML 块、
  /// 分隔线落回普通文字；`<` 交给 {@link AngleBracketInlineParser}；记下每个节点的源位置。
  private static final Parser PARSER =
      Parser.builder()
          .enabledBlockTypes(Set.of(Heading.class, BlockQuote.class, ListBlock.class))
          .customInlineContentParserFactory(new AngleBracketInlineParser.Factory())
          .includeSourceSpans(IncludeSourceSpans.BLOCKS_AND_INLINES)
          .build();

  /// 连续空白（Unicode 空白，含全角空格、不换行空格）。
  private static final Pattern WHITESPACE = Pattern.compile("(?U)\\s+");

  /// 转纯文本。
  ///
  /// @param markdown 正文
  /// @param mentionedUserIds 提及名单
  /// @return 纯文本
  @Override
  public PlainText toPlainText(String markdown, Set<Long> mentionedUserIds) {
    TextCollector collector = new TextCollector(markdown, mentionedUserIds);
    PARSER.parse(markdown).accept(collector);
    return PlainText.of(collector.render());
  }

  /// 收集文字的访问器：字面文字先攒着（同形文字可能拆在几个节点里），碰到真提及或结束时再断开同形文字、写出。
  /// 引用块、列表项先用一个新的收集器单独转好，再加引号或前缀。
  private static final class TextCollector extends AbstractVisitor {

    private final String source;
    private final Set<Long> mentionedUserIds;
    private final StringBuilder out = new StringBuilder();
    private final StringBuilder pending = new StringBuilder();

    /// 构造。
    ///
    /// @param source 正文原文（取源位置用）
    /// @param mentionedUserIds 提及名单
    private TextCollector(String source, Set<Long> mentionedUserIds) {
      this.source = source;
      this.mentionedUserIds = mentionedUserIds;
    }

    /// 普通文字。
    ///
    /// @param text 节点
    @Override
    public void visit(Text text) {
      pending.append(text.getLiteral());
    }

    /// 行内代码：只留代码文字，里面的 `<@ID>` 是字面。跨行的三反引号（门户当成被关掉的围栏代码块）给原文。
    ///
    /// @param code 节点
    @Override
    public void visit(Code code) {
      String raw = sourceOf(code);
      boolean fence = raw.startsWith("```") && code.getSourceSpans().size() > 1;
      pending.append(fence ? raw : code.getLiteral());
    }

    /// 提及节点：名单里的写成 `<@ID>`，其余当字面文字。
    ///
    /// @param node 节点
    @Override
    public void visit(CustomNode node) {
      if (node instanceof MentionNode mention) {
        Long userId = parseUserId(mention.digits());
        if (userId != null && mentionedUserIds.contains(userId)) {
          flush();
          out.append("<@").append(userId).append('>');
        } else {
          pending.append("<@").append(mention.digits()).append('>');
        }
      }
    }

    /// 软换行：门户装了 remark-breaks，显示成换行。
    ///
    /// @param lineBreak 节点
    @Override
    public void visit(SoftLineBreak lineBreak) {
      pending.append('\n');
    }

    /// 硬换行。
    ///
    /// @param lineBreak 节点
    @Override
    public void visit(HardLineBreak lineBreak) {
      pending.append('\n');
    }

    /// 段落：另起一行。
    ///
    /// @param paragraph 节点
    @Override
    public void visit(Paragraph paragraph) {
      block(paragraph);
    }

    /// 标题：门户降成粗体段落，只留文字。
    ///
    /// @param heading 节点
    @Override
    public void visit(Heading heading) {
      block(heading);
    }

    /// 引用块：另起一行，内容外面加中文引号，分得清哪句是引用（多层各加一对）；空引用省略。
    ///
    /// @param blockQuote 节点
    @Override
    public void visit(BlockQuote blockQuote) {
      String inner = nested(blockQuote);
      if (!inner.isEmpty()) {
        appendBlock("“" + inner + "”");
      }
    }

    /// 列表项：另起一行，加上门户看得见的前缀。
    ///
    /// @param item 节点
    @Override
    public void visit(ListItem item) {
      String inner = nested(item);
      if (!inner.isEmpty()) {
        appendBlock(prefixOf(item) + inner);
      }
    }

    /// 链接只留文字；引用式链接（`[x]`、`[文字][x]`，原文以 `]` 结尾）门户解析不出来，给原文。
    ///
    /// @param link 节点
    @Override
    public void visit(Link link) {
      String raw = sourceOf(link);
      if (raw.endsWith("]")) {
        pending.append(raw);
      } else {
        visitChildren(link);
      }
    }

    /// 链接定义：门户关掉了这个写法，给原文，单独一行。
    ///
    /// @param definition 节点
    @Override
    public void visit(LinkReferenceDefinition definition) {
      pending.append('\n').append(sourceOf(definition)).append('\n');
    }

    /// 图片：门户不加载图片，显示原文。
    ///
    /// @param image 节点
    @Override
    public void visit(Image image) {
      pending.append(sourceOf(image));
    }

    /// 块级节点：前后各一个换行，多余的空行最后整理掉。
    ///
    /// @param node 节点
    private void block(Node node) {
      pending.append('\n');
      visitChildren(node);
      pending.append('\n');
    }

    /// 用一个新的收集器单独转一个容器的子节点。列表项里直接套的列表门户不支持，给原文。
    ///
    /// @param container 容器节点
    /// @return 转好、整理过空白的文字
    private String nested(Node container) {
      TextCollector collector = new TextCollector(source, mentionedUserIds);
      for (Node child = container.getFirstChild(); child != null; child = child.getNext()) {
        if (container instanceof ListItem && child instanceof ListBlock) {
          collector.pending.append('\n').append(sourceOf(child)).append('\n');
        } else {
          child.accept(collector);
        }
      }
      return collector.render();
    }

    /// 列表项的前缀：无序 `• `；有序「编号. 」，编号从列表的起始编号数起（门户按 `ol start` 显示）。
    ///
    /// @param item 列表项
    /// @return 前缀
    private static String prefixOf(ListItem item) {
      if (!(item.getParent() instanceof OrderedList list)) {
        return "• ";
      }
      int index = 0;
      for (Node node = item.getPrevious(); node != null; node = node.getPrevious()) {
        index++;
      }
      int start = list.getMarkerStartNumber() == null ? 1 : list.getMarkerStartNumber();
      return (start + index) + ". ";
    }

    /// 另起一行写出已经转好的文字（不再断开同形文字：里面的 `<@ID>` 都是真提及）。
    ///
    /// @param rendered 转好的文字
    private void appendBlock(String rendered) {
      flush();
      out.append('\n').append(rendered).append('\n');
    }

    /// 节点的原文：每行一个源位置（容器里的行不含开头的 `>`），行之间用换行拼回。
    ///
    /// @param node 节点
    /// @return 原文
    private String sourceOf(Node node) {
      return node.getSourceSpans().stream()
          .map(
              span ->
                  source.substring(span.getInputIndex(), span.getInputIndex() + span.getLength()))
          .collect(Collectors.joining("\n"));
    }

    /// 数字转用户 ID；超出 long 范围的不是用户 ID。
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

    /// 写出攒着的字面文字：每处同形文字在 `<` 与 `@` 之间插零宽连接符。
    private void flush() {
      out.append(
          PlainText.MENTION.matcher(pending).replaceAll("<" + PlainText.WORD_JOINER + "@$1>"));
      pending.setLength(0);
    }

    /// 拼好的纯文本：每行连续空白合成一个空格、去掉首尾空白，删掉空行。
    ///
    /// @return 纯文本
    private String render() {
      flush();
      return out.toString()
          .lines()
          .map(line -> WHITESPACE.matcher(line).replaceAll(" ").strip())
          .filter(line -> !line.isEmpty())
          .collect(Collectors.joining("\n"));
    }
  }
}
