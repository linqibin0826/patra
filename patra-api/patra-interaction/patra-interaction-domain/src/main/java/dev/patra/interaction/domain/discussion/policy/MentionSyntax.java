package dev.patra.interaction.domain.discussion.policy;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;

/// 正文里的 @ 提及（spec 5.4.1、5.4.3、5.4.5）：识别候选、换成 `<@用户ID>` 标记、抽取标记、换回编辑原文。
///
/// 一次从左到右扫描，近似 CommonMark 的行内规则：
///
/// 1. `\` 后跟 ASCII 标点是转义，两个字符原样保留；
/// 2. N 个反引号到下一串恰好 N 个反引号（不跨空行）是行内代码，里面什么都不处理；没有闭合的反引号是普通字符；
/// 3. 链接 `[文字](目标)` 与网址（`http://`、`https://` 到空白或 `<`、`>`）里不识别 `@`；
/// 4. `<@数字>` 是标记：客户端发来的一律转义，存储形式里没转义的就是服务端写下的提及；
/// 5. 普通文本里，`@` 前面是开头、或者不是字母 / 数字 / `_` / `@` 的字符时，后面贪婪取 `[A-Za-z0-9_-]+`、去掉末尾的
///    `_` 与 `-`，符合用户名语法（3 ~ 30 个字符、连接符不连续、不全是数字）的就是候选。
///
/// 门户渲染时按同样的规则认标记；两边的判断在边角情形可能有出入，最坏是某个 `@名字` 没被当成提及。
public final class MentionSyntax {

  /// 拿不到用户名（已注销）的提及在编辑原文里写成的文字：不合用户名语法，保存后就是普通文字。
  public static final String DELETED_USER = "@已注销用户";

  private static final Pattern USERNAME =
      Pattern.compile("[A-Za-z0-9](?:[A-Za-z0-9]|[_-][A-Za-z0-9])*");
  private static final Pattern DIGITS_ONLY = Pattern.compile("[0-9]+");
  private static final int MIN_NAME = 3;
  private static final int MAX_NAME = 30;
  private static final int MAX_ID_DIGITS = 19;
  private static final String ASCII_PUNCTUATION = "!\"#$%&'()*+,-./:;<=>?@[\\]^_`{|}~";

  /// 只有静态方法。
  private MentionSyntax() {}

  /// 普通文本里的 @ 候选。
  ///
  /// @param text 规范化后的原文
  /// @return 候选（不带 `@`），按原文去重、保持第一次出现的顺序
  public static List<String> candidates(String text) {
    Set<String> names = new LinkedHashSet<>();
    for (Segment segment : segments(text)) {
      if (segment instanceof Mention mention) {
        names.add(mention.name());
      }
    }
    return List.copyOf(names);
  }

  /// 原文 → 存储形式：解析到的候选整段换成 `<@用户ID>`，客户端发来的标记加 `\` 转义。
  ///
  /// @param text 规范化后的原文
  /// @param resolve 候选原文 → 用户 ID；返回 `null` 的保持原样
  /// @return 存储形式
  public static String toStored(String text, Function<String, Long> resolve) {
    StringBuilder out = new StringBuilder(text.length());
    for (Segment segment : segments(text)) {
      switch (segment) {
        case Literal literal -> out.append(literal.text());
        case Token token -> out.append('\\').append(token.raw());
        case Mention mention -> {
          Long userId = resolve.apply(mention.name());
          out.append(userId == null ? mention.raw() : "<@" + userId + ">");
        }
      }
    }
    return out.toString();
  }

  /// 存储形式里提及到的用户。
  ///
  /// @param stored 存储形式
  /// @return 用户 ID，去重、按第一次出现
  public static List<Long> mentionedIds(String stored) {
    Set<Long> ids = new LinkedHashSet<>();
    for (Segment segment : segments(stored)) {
      if (segment instanceof Token token && token.userId() != null) {
        ids.add(token.userId());
      }
    }
    return List.copyOf(ids);
  }

  /// 存储形式 → 编辑原文：标记换回 `@用户名`。
  ///
  /// @param stored 存储形式
  /// @param usernameOf 用户 ID → 当前用户名；返回 `null`（已注销）时写成 {@link #DELETED_USER}
  /// @return 编辑原文
  public static String toSource(String stored, Function<Long, String> usernameOf) {
    StringBuilder out = new StringBuilder(stored.length());
    for (Segment segment : segments(stored)) {
      switch (segment) {
        case Literal literal -> out.append(literal.text());
        case Mention mention -> out.append(mention.raw());
        case Token token -> {
          if (token.userId() == null) {
            out.append(token.raw());
          } else {
            String username = usernameOf.apply(token.userId());
            out.append(username == null ? DELETED_USER : "@" + username);
          }
        }
      }
    }
    return out.toString();
  }

  /// 把正文切成片段。
  ///
  /// @param text 正文
  /// @return 片段，拼起来就是原文
  private static List<Segment> segments(String text) {
    Scanner scanner = new Scanner(text);
    scanner.scan(0, text.length(), true);
    return scanner.finish();
  }

  /// 片段。
  private sealed interface Segment permits Literal, Token, Mention {}

  /// 原样的文字。
  ///
  /// @param text 文字
  private record Literal(String text) implements Segment {}

  /// 标记 `<@数字>`（不在行内代码里）。
  ///
  /// @param raw 原文
  /// @param userId 用户 ID；数字超出 `long` 时为 `null`
  private record Token(String raw, Long userId) implements Segment {}

  /// 候选 `@名字`。
  ///
  /// @param raw 原文（含 `@`）
  /// @param name 名字（不含 `@`）
  private record Mention(String raw, String name) implements Segment {}

  /// 一次扫描：线性时间，链接的边界靠预先算好的「下一个 `]`」「下一个 `)`」表定位。
  private static final class Scanner {

    private final String text;
    private final int[] bracketStop;
    private final int[] parenStop;
    private final List<Segment> segments = new ArrayList<>();
    private final StringBuilder literal = new StringBuilder();

    /// 准备扫描。
    ///
    /// @param text 正文
    Scanner(String text) {
      this.text = text;
      int n = text.length();
      this.bracketStop = new int[n + 1];
      this.parenStop = new int[n + 1];
      bracketStop[n] = n;
      parenStop[n] = n;
      for (int i = n - 1; i >= 0; i--) {
        char c = text.charAt(i);
        bracketStop[i] = c == ']' || c == '\n' ? i : bracketStop[i + 1];
        parenStop[i] = c == ')' || Character.isWhitespace(c) ? i : parenStop[i + 1];
      }
    }

    /// 扫描 `[from, to)`。
    ///
    /// @param from 起点
    /// @param to 终点（不含）
    /// @param plain 是否普通文本：链接里为 `false`，不识别 `@`、链接与网址
    void scan(int from, int to, boolean plain) {
      int prev = from == 0 ? -1 : text.codePointBefore(from);
      int i = from;
      while (i < to) {
        char c = text.charAt(i);
        if (c == '\\' && i + 1 < to && ASCII_PUNCTUATION.indexOf(text.charAt(i + 1)) >= 0) {
          literal.append(c).append(text.charAt(i + 1));
          prev = text.charAt(i + 1);
          i += 2;
          continue;
        }
        if (c == '`') {
          int run = runLength(i, to);
          int close = closingRun(i + run, run, to);
          int end = close < 0 ? i + run : close + run;
          literal.append(text, i, end);
          prev = '`';
          i = end;
          continue;
        }
        if (c == '<') {
          int end = tokenEnd(i, to);
          if (end > 0) {
            emit(new Token(text.substring(i, end), tokenId(i, end)));
            prev = '>';
            i = end;
            continue;
          }
        }
        if (plain && c == '[') {
          int end = linkEnd(i, to);
          if (end > 0) {
            scan(i, end, false);
            prev = ')';
            i = end;
            continue;
          }
        }
        if (plain && startsWithUrl(i, to)) {
          int end = urlEnd(i, to);
          literal.append(text, i, end);
          prev = text.codePointBefore(end);
          i = end;
          continue;
        }
        if (plain && c == '@' && isBoundary(prev) && !startsWithUrl(i + 1, to)) {
          int end = nameEnd(i + 1, to);
          String name = text.substring(i + 1, end);
          if (isUsername(name)) {
            emit(new Mention(text.substring(i, end), name));
            prev = text.charAt(end - 1);
            i = end;
            continue;
          }
        }
        int codePoint = text.codePointAt(i);
        literal.appendCodePoint(codePoint);
        prev = codePoint;
        i += Character.charCount(codePoint);
      }
    }

    /// 收尾，返回全部片段。
    ///
    /// @return 片段
    List<Segment> finish() {
      flushLiteral();
      return segments;
    }

    /// 记下一个非文字片段（先把攒着的文字收成一段）。
    ///
    /// @param segment 片段
    private void emit(Segment segment) {
      flushLiteral();
      segments.add(segment);
    }

    /// 把攒着的文字收成一段。
    private void flushLiteral() {
      if (!literal.isEmpty()) {
        segments.add(new Literal(literal.toString()));
        literal.setLength(0);
      }
    }

    /// 从 `i` 起连续的反引号个数。
    ///
    /// @param i 起点
    /// @param to 终点
    /// @return 个数
    private int runLength(int i, int to) {
      int j = i;
      while (j < to && text.charAt(j) == '`') {
        j++;
      }
      return j - i;
    }

    /// 找闭合行内代码的那串反引号：长度恰好为 `length`，不跨空行。
    ///
    /// @param from 从哪里找
    /// @param length 开头那串的长度
    /// @param to 终点
    /// @return 闭合串的起点；没有为 -1
    private int closingRun(int from, int length, int to) {
      int j = from;
      while (j < to) {
        char c = text.charAt(j);
        if (c == '\n' && blankLineAfter(j, to)) {
          return -1;
        }
        if (c == '`') {
          int run = runLength(j, to);
          if (run == length) {
            return j;
          }
          j += run;
        } else {
          j++;
        }
      }
      return -1;
    }

    /// 换行之后是不是一个空行（中间只有空格、制表符）。
    ///
    /// @param newline 换行符的位置
    /// @param to 终点
    /// @return 是空行时为 `true`
    private boolean blankLineAfter(int newline, int to) {
      int k = newline + 1;
      while (k < to && (text.charAt(k) == ' ' || text.charAt(k) == '\t')) {
        k++;
      }
      return k < to && text.charAt(k) == '\n';
    }

    /// `<@数字>` 的终点。
    ///
    /// @param i `<` 的位置
    /// @param to 终点
    /// @return 终点（不含）；不是标记为 -1
    private int tokenEnd(int i, int to) {
      if (i + 2 >= to || text.charAt(i + 1) != '@' || !isNonZeroDigit(text.charAt(i + 2))) {
        return -1;
      }
      int j = i + 2;
      while (j < to && isDigit(text.charAt(j))) {
        j++;
      }
      boolean fits = j - (i + 2) <= MAX_ID_DIGITS;
      return fits && j < to && text.charAt(j) == '>' ? j + 1 : -1;
    }

    /// 标记里的用户 ID。
    ///
    /// @param start 标记起点
    /// @param end 标记终点（不含）
    /// @return 用户 ID；超出 `long` 为 `null`
    private Long tokenId(int start, int end) {
      try {
        return Long.parseLong(text.substring(start + 2, end - 1));
      } catch (NumberFormatException ex) {
        return null;
      }
    }

    /// 链接 `[文字](目标)` 的终点：`]` 前没有 `]` 与换行，紧跟 `(`，`)` 前没有空白。
    ///
    /// @param i `[` 的位置
    /// @param to 终点
    /// @return 终点（不含）；不是链接为 -1
    private int linkEnd(int i, int to) {
      int close = bracketStop[i + 1];
      if (close >= to || text.charAt(close) != ']') {
        return -1;
      }
      int open = close + 1;
      if (open >= to || text.charAt(open) != '(') {
        return -1;
      }
      int end = parenStop[open + 1];
      return end < to && text.charAt(end) == ')' ? end + 1 : -1;
    }

    /// 从 `i` 起是不是网址（`http://` 或 `https://`，不分大小写）。
    ///
    /// @param i 位置
    /// @param to 终点
    /// @return 是网址时为 `true`
    private boolean startsWithUrl(int i, int to) {
      return startsWithIgnoreCase(i, to, "http://") || startsWithIgnoreCase(i, to, "https://");
    }

    /// 从 `i` 起是不是某个前缀（不分大小写）。
    ///
    /// @param i 位置
    /// @param to 终点
    /// @param prefix 前缀
    /// @return 是时为 `true`
    private boolean startsWithIgnoreCase(int i, int to, String prefix) {
      return i + prefix.length() <= to && text.regionMatches(true, i, prefix, 0, prefix.length());
    }

    /// 网址的终点：到空白、`<`、`>` 为止。
    ///
    /// @param i 网址起点
    /// @param to 终点
    /// @return 终点（不含）
    private int urlEnd(int i, int to) {
      int j = i;
      while (j < to) {
        char c = text.charAt(j);
        if (Character.isWhitespace(c) || c == '<' || c == '>') {
          break;
        }
        j++;
      }
      return j;
    }

    /// 名字的终点：贪婪取 `[A-Za-z0-9_-]`，再去掉末尾的 `_` 与 `-`。
    ///
    /// @param start 名字起点（`@` 之后）
    /// @param to 终点
    /// @return 终点（不含）
    private int nameEnd(int start, int to) {
      int j = start;
      while (j < to && isNameChar(text.charAt(j))) {
        j++;
      }
      while (j > start && (text.charAt(j - 1) == '_' || text.charAt(j - 1) == '-')) {
        j--;
      }
      return j;
    }
  }

  /// `@` 前面的码点允不允许提及：开头，或者不是字母、数字、`_`、`@`。
  ///
  /// @param prev 前一个码点；开头为 -1
  /// @return 允许时为 `true`
  private static boolean isBoundary(int prev) {
    return prev < 0 || !(Character.isLetterOrDigit(prev) || prev == '_' || prev == '@');
  }

  /// 是否符合用户名语法（不分大小写）。
  ///
  /// @param name 名字
  /// @return 符合时为 `true`
  private static boolean isUsername(String name) {
    return name.length() >= MIN_NAME
        && name.length() <= MAX_NAME
        && USERNAME.matcher(name).matches()
        && !DIGITS_ONLY.matcher(name).matches();
  }

  /// 名字里能出现的字符。
  ///
  /// @param c 字符
  /// @return 能出现时为 `true`
  private static boolean isNameChar(char c) {
    return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || isDigit(c) || c == '_' || c == '-';
  }

  /// ASCII 数字。
  ///
  /// @param c 字符
  /// @return 是数字时为 `true`
  private static boolean isDigit(char c) {
    return c >= '0' && c <= '9';
  }

  /// ASCII 的 1 ~ 9。
  ///
  /// @param c 字符
  /// @return 是时为 `true`
  private static boolean isNonZeroDigit(char c) {
    return c >= '1' && c <= '9';
  }
}
