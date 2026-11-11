package dev.patra.identity.domain.account.policy;

import dev.patra.identity.domain.account.model.vo.PlainPassword;
import dev.patra.identity.domain.account.model.vo.UserIdentifiers;
import dev.patra.identity.domain.account.port.password.CommonPasswordPort;
import dev.patra.identity.domain.session.model.vo.UserFieldViolations;
import dev.patra.tool.core.error.field.FieldViolation;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/// 新密码的规则（注册、改密码、重设密码）：不为空、Unicode 合法、8 到 64 个码点、不在常见密码名单里、
/// 不和账号的邮箱或用户名相同或相近。
///
/// 「相近」的算法：密码和账号信息都先转成比较键（`PlainPassword.comparisonKeyOf`：NFKC、转小写、
/// 去掉首尾空白），再拿密码去比三样东西——完整邮箱、邮箱 `@` 前面那一段、用户名。密码等于其中一样就拒；其中一样至少
/// {@link #MIN_CONTAINED_LENGTH} 个码点、且密码包含它，也拒。这类密码最容易被猜中（PAP-191）。
/// 只在设置密码时查：库里只有哈希，以后改用户名或邮箱时没法回头判断旧密码像不像新值。
///
/// 登录不用注册规则：密码规则以后调整时旧密码不该被拦住。登录只设一个宽松的长度上限
/// {@link #MAX_LOGIN_LENGTH}，拦下不可能是合法密码的超长输入。
public final class PasswordPolicy {

  /// 最短 8 个码点。
  public static final int MIN_LENGTH = 8;

  /// 最长 64 个码点。
  public static final int MAX_LENGTH = 64;

  /// 登录时密码最长 1024 个码点。远大于注册上限，超过的不可能是合法密码，不必再做哈希。
  public static final int MAX_LOGIN_LENGTH = 1024;

  /// 邮箱、邮箱 `@` 前面那一段、用户名至少这么多个码点，才查密码是否包含它。
  /// 再短的片段太容易碰巧出现在无关的密码里（用户名 `amy` 和密码 `dreamy-cabin`）。
  private static final int MIN_CONTAINED_LENGTH = 4;

  private final CommonPasswordPort commonPasswords;

  /// 创建规则。
  ///
  /// @param commonPasswords 常见密码名单
  public PasswordPolicy(CommonPasswordPort commonPasswords) {
    this.commonPasswords = Objects.requireNonNull(commonPasswords, "commonPasswords 不能为 null");
  }

  /// 按新密码的规则校验（注册、改密码、重设密码都用它），按指定的字段名报错。
  /// 长度按原始输入算，名单和账号信息按比较键比；超长的输入不做规范化、不查名单、不和账号比。
  ///
  /// @param raw 用户输入，可以为 `null`
  /// @param field 报错时的字段名（`password` 或 `newPassword`）
  /// @param identifiers 这个密码所属账号的邮箱和用户名；还不知道是哪个账号时传
  ///     {@link UserIdentifiers#unknown()}，知道之后要再校验一次
  /// @return 第一条不满足的规则；合法时为空
  public Optional<FieldViolation> validate(String raw, String field, UserIdentifiers identifiers) {
    Optional<FieldViolation> basic = PlainPassword.validate(raw, field);
    if (basic.isPresent()) {
      return basic;
    }
    int length = raw.codePointCount(0, raw.length());
    if (length < MIN_LENGTH) {
      return Optional.of(UserFieldViolations.passwordTooShort(field));
    }
    if (length > MAX_LENGTH) {
      return Optional.of(UserFieldViolations.passwordTooLong(field));
    }
    String key = PlainPassword.comparisonKeyOf(raw);
    if (commonPasswords.isCommon(key)) {
      return Optional.of(UserFieldViolations.passwordTooCommon(field));
    }
    if (resembles(key, identifiers)) {
      return Optional.of(UserFieldViolations.passwordTooSimilar(field));
    }
    return Optional.empty();
  }

  /// 密码是否和账号的邮箱或用户名相同或相近，算法见类注释。
  ///
  /// @param passwordKey 密码的比较键
  /// @param identifiers 账号的邮箱和用户名
  /// @return 相同或相近时为 `true`
  private static boolean resembles(String passwordKey, UserIdentifiers identifiers) {
    String email = keyOrEmpty(identifiers.email());
    int at = email.indexOf('@');
    String localPart = at < 0 ? "" : email.substring(0, at);
    return Stream.of(email, localPart, keyOrEmpty(identifiers.username()))
        // 没有的那一项比较键是空串。空串不能参与：任何字符串都「包含」空串
        .filter(candidate -> !candidate.isEmpty())
        .anyMatch(
            candidate ->
                passwordKey.equals(candidate)
                    || candidate.codePointCount(0, candidate.length()) >= MIN_CONTAINED_LENGTH
                        && passwordKey.contains(candidate));
  }

  /// 比较键；`null` 当作空串。
  ///
  /// @param raw 原始字符串，可以为 `null`
  /// @return 比较键
  private static String keyOrEmpty(String raw) {
    return raw == null ? "" : PlainPassword.comparisonKeyOf(raw);
  }
}
