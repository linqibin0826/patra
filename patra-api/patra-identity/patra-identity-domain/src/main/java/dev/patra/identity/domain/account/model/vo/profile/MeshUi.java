package dev.patra.identity.domain.account.model.vo.profile;

import dev.patra.identity.domain.account.policy.ProfileTextRules;
import java.util.Objects;
import java.util.regex.Pattern;

/// MeSH 主题词 UI：`D` 加 6 位或 9 位数字，如 `D003920`、`D000072716`。不改大小写（`D` 是大写）。
///
/// @param value UI
public record MeshUi(String value) {

  private static final Pattern FORMAT = Pattern.compile("^D\\d{6}(\\d{3})?$");

  /// 只接受合法的 UI。
  ///
  /// @param value UI
  public MeshUi {
    Objects.requireNonNull(value, "value 不能为 null");
    if (!isValid(value)) {
      throw new IllegalArgumentException("不是合法的 MeSH UI");
    }
  }

  /// 去过首尾空白的值是否合法。
  ///
  /// @param stripped 去过首尾空白的值
  /// @return 合法时为 `true`
  public static boolean isValid(String stripped) {
    return FORMAT.matcher(stripped).matches();
  }

  /// 去首尾空白（含不换行空格）后构造。
  ///
  /// @param raw 用户输入
  /// @return UI
  /// @throws IllegalArgumentException 不合法
  public static MeshUi of(String raw) {
    return new MeshUi(ProfileTextRules.stripSpaces(raw));
  }
}
