package dev.patra.identity.domain.policy;

import dev.patra.identity.domain.exception.InvalidUserFieldsException;
import dev.patra.identity.domain.model.vo.DisplayName;
import dev.patra.identity.domain.model.vo.UserFieldViolations;
import dev.patra.tool.core.error.field.FieldViolation;
import java.nio.charset.StandardCharsets;
import java.text.BreakIterator;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/// 昵称规则：2 ~ 30 个字素且 UTF-8 不超过 120 字节；拒绝控制、零宽、双向、换行等字符；
/// 拒绝网址、邮箱、手机号、微信号的形态；拒绝官方 / 职业身份词和站名。
///
/// 字素按 `BreakIterator` 的扩展字素簇数：汉字、带变音符的字母、ZWJ 组合的 emoji、国旗各计 1。
/// 格式字符（Cf）只有一种例外：它是 emoji 组件（ZWJ、标签字符）且所在字素簇含有扩展象形字。
/// 以组合用字符开头的字素簇也拒绝：只由组合字符拼成的昵称渲染出来是空白。
public final class DisplayNamePolicy {

  /// 至少 2 个字素。
  public static final int MIN_GRAPHEMES = 2;

  /// 最多 30 个字素。
  public static final int MAX_GRAPHEMES = 30;

  /// UTF-8 最多 120 字节。
  public static final int MAX_BYTES = 120;

  private static final Pattern URL =
      Pattern.compile(
          "(https?://|www\\.)|\\.(com|cn|net|org|io|me|top|xyz|info|site|app|dev|co|cc|tv|edu|gov|ac)"
              + "(?=$|[\\s/])");

  private static final Pattern EMAIL = Pattern.compile("[^\\s@]+@[^\\s@]+\\.[^\\s@]{2,}");

  private static final Pattern PHONE_SEPARATORS = Pattern.compile("[\\s\\-.()+]");

  private static final Pattern PHONE = Pattern.compile("\\d{7,}");

  private static final Pattern WECHAT =
      Pattern.compile("微信|weixin|wechat|(?<![a-z])(wx|vx)\\s*[:：]");

  /// 按规则校验，只报第一条。先规范化，再依次查空、拒绝字符、字素与字节长度、形态、拒绝词。
  ///
  /// @param raw 用户输入，可以为 `null`
  /// @return 第一条不满足的规则；合法时为空
  public Optional<FieldViolation> validate(String raw) {
    if (raw == null) {
      return Optional.of(UserFieldViolations.displayNameRequired());
    }
    String normalized = DisplayName.normalize(raw);
    if (normalized.isEmpty()) {
      return Optional.of(UserFieldViolations.displayNameRequired());
    }
    if (hasRejectedCharacter(normalized)) {
      return Optional.of(UserFieldViolations.displayNameInvalidCharacter());
    }
    int graphemes = graphemeCount(normalized);
    if (graphemes < MIN_GRAPHEMES) {
      return Optional.of(UserFieldViolations.displayNameTooShort());
    }
    if (graphemes > MAX_GRAPHEMES
        || normalized.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
      return Optional.of(UserFieldViolations.displayNameTooLong());
    }
    if (looksLikeContact(normalized)) {
      return Optional.of(UserFieldViolations.displayNameInvalidFormat());
    }
    if (RejectedDisplayNameWords.matches(normalized)) {
      return Optional.of(UserFieldViolations.displayNameReserved());
    }
    return Optional.empty();
  }

  /// 校验并构造。
  ///
  /// @param raw 用户输入
  /// @return 规范化之后的昵称
  /// @throws InvalidUserFieldsException 不满足规则
  public DisplayName parse(String raw) {
    Optional<FieldViolation> violation = validate(raw);
    if (violation.isPresent()) {
      throw new InvalidUserFieldsException(List.of(violation.get()));
    }
    return new DisplayName(DisplayName.normalize(raw));
  }

  /// 数扩展字素簇。
  ///
  /// @param normalized 规范化之后的昵称
  /// @return 字素数
  public static int graphemeCount(String normalized) {
    BreakIterator clusters = BreakIterator.getCharacterInstance(Locale.ROOT);
    clusters.setText(normalized);
    int count = 0;
    while (clusters.next() != BreakIterator.DONE) {
      count++;
    }
    return count;
  }

  /// 逐个字素簇查拒绝字符。
  ///
  /// @param normalized 规范化之后的昵称
  /// @return 含拒绝字符时为 `true`
  private static boolean hasRejectedCharacter(String normalized) {
    BreakIterator clusters = BreakIterator.getCharacterInstance(Locale.ROOT);
    clusters.setText(normalized);
    int start = clusters.first();
    for (int end = clusters.next(); end != BreakIterator.DONE; start = end, end = clusters.next()) {
      if (isRejectedCluster(normalized, start, end)) {
        return true;
      }
    }
    return false;
  }

  /// 一个字素簇是否含拒绝字符：以组合用字符开头；含控制、行 / 段分隔、私用、未分配、孤立代理项；
  /// 含格式字符而它不是 emoji 组件，或簇里没有扩展象形字。
  ///
  /// @param text 整个昵称
  /// @param start 簇的起点
  /// @param end 簇的终点（不含）
  /// @return 要拒绝时为 `true`
  private static boolean isRejectedCluster(String text, int start, int end) {
    int firstType = Character.getType(text.codePointAt(start));
    if (firstType == Character.NON_SPACING_MARK
        || firstType == Character.ENCLOSING_MARK
        || firstType == Character.COMBINING_SPACING_MARK) {
      return true;
    }
    boolean format = false;
    boolean pictographic = false;
    int i = start;
    while (i < end) {
      int cp = text.codePointAt(i);
      int type = Character.getType(cp);
      if (type == Character.CONTROL
          || type == Character.LINE_SEPARATOR
          || type == Character.PARAGRAPH_SEPARATOR
          || type == Character.PRIVATE_USE
          || type == Character.UNASSIGNED
          || type == Character.SURROGATE) {
        return true;
      }
      if (type == Character.FORMAT) {
        if (!Character.isEmojiComponent(cp)) {
          return true;
        }
        format = true;
      }
      if (Character.isExtendedPictographic(cp)) {
        pictographic = true;
      }
      i += Character.charCount(cp);
    }
    return format && !pictographic;
  }

  /// 是否像网址、邮箱、手机号或微信号。在 NFKC 小写副本上判，全角数字和冒号也算。
  ///
  /// @param normalized 规范化之后的昵称
  /// @return 像联系方式时为 `true`
  private static boolean looksLikeContact(String normalized) {
    String folded = Normalizer.normalize(normalized, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
    if (URL.matcher(folded).find() || EMAIL.matcher(folded).find()) {
      return true;
    }
    if (PHONE.matcher(PHONE_SEPARATORS.matcher(folded).replaceAll("")).find()) {
      return true;
    }
    return WECHAT.matcher(folded).find();
  }
}
