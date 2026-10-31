package dev.patra.interaction.infra.subject.markdown;

import java.util.Set;
import org.commonmark.node.Text;
import org.commonmark.parser.beta.InlineContentParser;
import org.commonmark.parser.beta.InlineContentParserFactory;
import org.commonmark.parser.beta.InlineParserState;
import org.commonmark.parser.beta.ParsedInline;
import org.commonmark.parser.beta.Position;
import org.commonmark.parser.beta.Scanner;
import org.commonmark.text.AsciiMatcher;

/// 碰到 `<` 时的行内解析，对齐门户的子集（`subset.ts`）：`<@` + 首位 1–9 的数字串 + `>` 是提及节点；
/// 其余的 `<` 当普通文字吃掉，尖括号网址与行内 HTML 就解析不出来（门户关掉了这两种写法，按原文显示）。
///
/// 自定义解析器排在内置的前面；转义与行内代码先于它处理，所以 `\<@42>` 和代码里的 `<@42>` 进不来。
final class AngleBracketInlineParser implements InlineContentParser {

  private static final AsciiMatcher DIGIT = AsciiMatcher.builder().range('0', '9').build();

  /// 解析。
  ///
  /// @param state 解析状态，扫描器停在 `<` 上
  /// @return 提及节点，或只含 `<` 的文字节点
  @Override
  public ParsedInline tryParse(InlineParserState state) {
    Scanner scanner = state.scanner();
    scanner.next();
    Position afterBracket = scanner.position();
    if (scanner.next('@') && scanner.peek() >= '1' && scanner.peek() <= '9') {
      Position digitsStart = scanner.position();
      scanner.match(DIGIT);
      Position digitsEnd = scanner.position();
      if (scanner.next('>')) {
        String digits = scanner.getSource(digitsStart, digitsEnd).getContent();
        return ParsedInline.of(new MentionNode(digits), scanner.position());
      }
    }
    return ParsedInline.of(new Text("<"), afterBracket);
  }

  /// 工厂：每次解析新建一个解析器。
  static final class Factory implements InlineContentParserFactory {

    /// 触发字符。
    ///
    /// @return `<`
    @Override
    public Set<Character> getTriggerCharacters() {
      return Set.of('<');
    }

    /// 新建解析器。
    ///
    /// @return 解析器
    @Override
    public InlineContentParser create() {
      return new AngleBracketInlineParser();
    }
  }
}
