package dev.linqibin.patra.identity.domain.model.vo;

import dev.linqibin.commons.error.field.FieldViolation;
import dev.linqibin.patra.identity.domain.exception.InvalidUserFieldsException;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/// 用户输入的明文密码。不去空格，`toString()` 不输出内容。
///
/// 长度按码点计，和前端 `[...password].length` 一致。哈希和校验前都做 NFKC，
/// 同一个密码在全角、半角或不同输入法下得到同样的结果。
public final class PlainPassword {

  private final String value;

  /// 只能经 {@link #of(String)} 创建。
  ///
  /// @param value 已经校验过的密码
  private PlainPassword(String value) {
    this.value = value;
  }

  /// 从用户输入创建。只查空值和不合法的 Unicode，长度和常见名单由 `PasswordPolicy` 查。
  ///
  /// @param raw 用户输入
  /// @return 明文密码
  /// @throws InvalidUserFieldsException 输入为空或含孤立的代理项
  public static PlainPassword of(String raw) {
    Optional<FieldViolation> violation = validate(raw);
    if (violation.isPresent()) {
      throw new InvalidUserFieldsException(List.of(violation.get()));
    }
    return new PlainPassword(raw);
  }

  /// 校验用户输入：不能为空，不能含孤立的 UTF-16 代理项。
  ///
  /// @param raw 用户输入，可以为 `null`
  /// @return 第一条不满足的规则；合法时为空
  public static Optional<FieldViolation> validate(String raw) {
    if (raw == null || raw.isEmpty()) {
      return Optional.of(UserFieldViolations.passwordRequired());
    }
    // codePoints() 会把成对的代理项合成一个码点，孤立的代理项则原样给出，落在这个区间里
    if (raw.codePoints().anyMatch(codePoint -> codePoint >= 0xD800 && codePoint <= 0xDFFF)) {
      return Optional.of(UserFieldViolations.passwordInvalidCharacter());
    }
    return Optional.empty();
  }

  /// 查常见密码名单用的比较键：NFKC、转小写、去掉首尾空白。名单条目也按同样的方式处理。
  ///
  /// @param raw 原始字符串
  /// @return 比较键
  public static String comparisonKeyOf(String raw) {
    Objects.requireNonNull(raw, "raw 不能为 null");
    return Normalizer.normalize(raw, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT).strip();
  }

  /// 返回原始输入。
  ///
  /// @return 原始输入
  public String value() {
    return value;
  }

  /// 返回码点数。
  ///
  /// @return 长度
  public int length() {
    return value.codePointCount(0, value.length());
  }

  /// 返回 NFKC 之后的形式，哈希和校验都用它。
  ///
  /// @return NFKC 之后的字符串
  public String normalized() {
    return Normalizer.normalize(value, Normalizer.Form.NFKC);
  }

  /// 不输出密码。
  ///
  /// @return `***`
  @Override
  public String toString() {
    return "***";
  }
}
