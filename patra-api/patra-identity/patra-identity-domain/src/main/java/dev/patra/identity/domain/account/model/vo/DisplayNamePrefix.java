package dev.patra.identity.domain.account.model.vo;

import dev.patra.identity.domain.account.policy.DisplayNamePolicy;
import dev.patra.identity.domain.account.policy.ProfileTextRules;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/// 昵称前缀：可能是某个昵称的开头（@ 联想用，PAP-118），比较时不分大小写。
///
/// @param value 规范化并转小写之后的前缀
public record DisplayNamePrefix(String value) {

  /// 只接受非空值；规范化与拒绝字符由 `parse` 判。
  ///
  /// @param value 规范化并转小写之后的前缀
  public DisplayNamePrefix {
    Objects.requireNonNull(value, "value 不能为 null");
    if (value.isEmpty()) {
      throw new IllegalArgumentException("昵称前缀不能为空");
    }
  }

  /// 从用户输入解析：按 `DisplayName.normalize` 规范化（与存库的昵称同一套），不能为空、不超过 120 字节
  /// （存库的昵称不会更长）、不能含昵称里不可能出现的字符（`ProfileTextRules.hasRejectedCharacter`），再转小写。
  ///
  /// @param raw 用户输入，可以为 `null`
  /// @return 前缀；为 `null` 或不可能是任何昵称的开头时为空
  public static Optional<DisplayNamePrefix> parse(String raw) {
    if (raw == null) {
      return Optional.empty();
    }
    String normalized = DisplayName.normalize(raw);
    if (normalized.isEmpty()
        || ProfileTextRules.utf8Length(normalized) > DisplayNamePolicy.MAX_BYTES
        || ProfileTextRules.hasRejectedCharacter(normalized, false)) {
      return Optional.empty();
    }
    return Optional.of(new DisplayNamePrefix(normalized.toLowerCase(Locale.ROOT)));
  }
}
