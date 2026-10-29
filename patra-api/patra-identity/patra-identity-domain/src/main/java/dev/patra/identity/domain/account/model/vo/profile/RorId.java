package dev.patra.identity.domain.account.model.vo.profile;

import dev.patra.identity.domain.account.policy.ProfileTextRules;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/// 9 位裸 ROR ID（小写），如 `04jztag35`。不接受 `https://ror.org/…` 形式：门户从建议接口拿到的就是裸 ID。
///
/// @param value 裸 ID
public record RorId(String value) {

  private static final Pattern FORMAT = Pattern.compile("^0[a-z0-9]{8}$");

  /// 只接受合法的裸 ID。
  ///
  /// @param value 裸 ID
  public RorId {
    Objects.requireNonNull(value, "value 不能为 null");
    if (!isValid(value)) {
      throw new IllegalArgumentException("不是合法的 ROR ID");
    }
  }

  /// 规范化：去首尾空白（含不换行空格）、转小写。
  ///
  /// @param raw 用户输入
  /// @return 规范化之后的值
  public static String normalize(String raw) {
    return ProfileTextRules.stripSpaces(raw).toLowerCase(Locale.ROOT);
  }

  /// 规范化之后的值是否合法。
  ///
  /// @param normalized 规范化之后的值
  /// @return 合法时为 `true`
  public static boolean isValid(String normalized) {
    return FORMAT.matcher(normalized).matches();
  }

  /// 规范化后构造。
  ///
  /// @param raw 用户输入
  /// @return ROR ID
  /// @throws IllegalArgumentException 不合法
  public static RorId of(String raw) {
    return new RorId(normalize(raw));
  }
}
