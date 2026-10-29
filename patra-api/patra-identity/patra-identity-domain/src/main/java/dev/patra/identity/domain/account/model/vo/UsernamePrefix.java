package dev.patra.identity.domain.account.model.vo;

import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/// 规范化之后的用户名前缀：可能是某个合法用户名的开头（@ 联想用，PAP-118）。
///
/// 比用户名语法宽：长度 1 ~ 30；首字符是字母或数字；`_` 和 `-` 不连续；末尾可以是一个 `_` 或 `-`；
/// 可以不够 3 个字符、可以全是数字（后面还能接字母）。只看语法，不看保留字。
///
/// @param value 规范化之后的前缀
public record UsernamePrefix(String value) {

  private static final Pattern SYNTAX = Pattern.compile("^[a-z0-9]([_-]?[a-z0-9])*[_-]?$");

  /// 只接受已经规范化且合法的值：语法只允许小写字母、数字、`_` 和 `-`，合法即已规范化。
  ///
  /// @param value 规范化之后的前缀
  public UsernamePrefix {
    Objects.requireNonNull(value, "value 不能为 null");
    if (!isPossible(value)) {
      throw new IllegalArgumentException("前缀必须是规范化之后的合法值");
    }
  }

  /// 从用户输入解析：先按 `Username.normalize` 规范化，再判语法。
  ///
  /// @param raw 用户输入，可以为 `null`
  /// @return 前缀；为 `null` 或不可能是任何合法用户名的开头时为空
  public static Optional<UsernamePrefix> parse(String raw) {
    if (raw == null) {
      return Optional.empty();
    }
    String normalized = Username.normalize(raw);
    return isPossible(normalized) ? Optional.of(new UsernamePrefix(normalized)) : Optional.empty();
  }

  /// 规范化之后的值能不能是某个合法用户名的开头。先判长度，正则只跑在不超过 30 个字符的串上。
  ///
  /// @param normalized 规范化之后的值
  /// @return 能时为 `true`
  private static boolean isPossible(String normalized) {
    return normalized.length() <= Username.MAX_LENGTH && SYNTAX.matcher(normalized).matches();
  }
}
