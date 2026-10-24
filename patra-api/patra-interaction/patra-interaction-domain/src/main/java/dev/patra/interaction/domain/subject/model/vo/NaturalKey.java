package dev.patra.interaction.domain.subject.model.vo;

import dev.patra.interaction.domain.subject.model.enums.NaturalKeyScheme;
import java.util.Objects;

/// 对象的外部身份锚，落库为 `{前缀}:{值}`（如 `pmid:12345678`）。
///
/// @param scheme 种类
/// @param value 值
public record NaturalKey(NaturalKeyScheme scheme, String value) {

  /// 校验值的格式。
  ///
  /// @param scheme 种类
  /// @param value 值
  public NaturalKey {
    Objects.requireNonNull(scheme, "scheme 不能为 null");
    if (!scheme.accepts(value)) {
      throw new IllegalArgumentException("身份锚的值不合法：" + scheme.prefix() + ":" + value);
    }
  }

  /// 创建。
  ///
  /// @param scheme 种类
  /// @param value 值
  /// @return 身份锚
  public static NaturalKey of(NaturalKeyScheme scheme, String value) {
    return new NaturalKey(scheme, value);
  }

  /// 解析落库格式。
  ///
  /// @param raw `{前缀}:{值}`
  /// @return 身份锚
  /// @throws IllegalArgumentException 格式不对或前缀未知
  public static NaturalKey parse(String raw) {
    int colon = raw == null ? -1 : raw.indexOf(':');
    if (colon < 0) {
      throw new IllegalArgumentException("身份锚缺少前缀：" + raw);
    }
    NaturalKeyScheme scheme =
        NaturalKeyScheme.fromPrefix(raw.substring(0, colon))
            .orElseThrow(() -> new IllegalArgumentException("未知的身份锚前缀：" + raw));
    return new NaturalKey(scheme, raw.substring(colon + 1));
  }

  /// 落库格式。
  ///
  /// @return `{前缀}:{值}`
  @Override
  public String toString() {
    return scheme.prefix() + ":" + value;
  }
}
