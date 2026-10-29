package dev.patra.identity.domain.account.model.vo.profile;

import dev.patra.identity.domain.account.model.vo.DisplayName;
import java.util.Objects;

/// 规范化之后的科室（单行文本，规范化同昵称）。长度与拒绝字符由 `ProfilePolicy` 判。
///
/// @param value 规范化之后的科室
public record Department(String value) {

  /// 只接受已经规范化的非空值。
  ///
  /// @param value 规范化之后的科室
  public Department {
    Objects.requireNonNull(value, "value 不能为 null");
    if (value.isEmpty() || !value.equals(DisplayName.normalize(value))) {
      throw new IllegalArgumentException("科室必须是规范化之后的非空值");
    }
  }

  /// 工厂方法。
  ///
  /// @param value 规范化之后的科室
  /// @return 值对象
  public static Department of(String value) {
    return new Department(value);
  }
}
