package dev.patra.identity.domain.account.policy;

import dev.patra.identity.domain.account.model.vo.DisplayName;
import dev.patra.identity.domain.session.exception.InvalidUserFieldsException;
import dev.patra.identity.domain.session.model.vo.UserFieldViolations;
import dev.patra.tool.core.error.field.FieldViolation;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/// 昵称规则：2 ~ 30 个字素且 UTF-8 不超过 120 字节；拒绝控制、零宽、双向、换行等字符；
/// 拒绝网址、邮箱、手机号、微信号的形态；拒绝官方 / 职业身份词和站名。
///
/// 拒绝字符与字素计数见 {@link ProfileTextRules}（与资料文本共用）。
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
    if (ProfileTextRules.hasRejectedCharacter(normalized, false)) {
      return Optional.of(UserFieldViolations.displayNameInvalidCharacter());
    }
    int graphemes = ProfileTextRules.graphemeCount(normalized);
    if (graphemes < MIN_GRAPHEMES) {
      return Optional.of(UserFieldViolations.displayNameTooShort());
    }
    if (graphemes > MAX_GRAPHEMES || ProfileTextRules.utf8Length(normalized) > MAX_BYTES) {
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
