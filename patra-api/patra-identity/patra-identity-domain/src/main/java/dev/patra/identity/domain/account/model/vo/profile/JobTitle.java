package dev.patra.identity.domain.account.model.vo.profile;

import dev.patra.identity.domain.account.model.vo.DisplayName;
import java.util.Objects;

/// 规范化之后的职称（单行文本，规范化同昵称）。长度与拒绝字符由 `ProfilePolicy` 判。
///
/// @param value 规范化之后的职称
public record JobTitle(String value) {

  /// 只接受已经规范化的非空值。
  ///
  /// @param value 规范化之后的职称
  public JobTitle {
    Objects.requireNonNull(value, "value 不能为 null");
    if (value.isEmpty() || !value.equals(DisplayName.normalize(value))) {
      throw new IllegalArgumentException("职称必须是规范化之后的非空值");
    }
  }

  /// 工厂方法。
  ///
  /// @param value 规范化之后的职称
  /// @return 值对象
  public static JobTitle of(String value) {
    return new JobTitle(value);
  }
}
