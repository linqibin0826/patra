package dev.patra.identity.app.account.usecase.query;

import dev.patra.identity.domain.account.model.aggregate.ExternalIdentity;
import java.util.List;

/// 设置页「登录方式」卡片要的内容。
///
/// @param hasPassword 有没有密码
/// @param linked 绑定的第三方账号
public record LoginMethodsView(boolean hasPassword, List<ExternalIdentity> linked) {

  /// 拷贝集合。
  public LoginMethodsView {
    linked = linked != null ? List.copyOf(linked) : List.of();
  }

  /// 创建。
  ///
  /// @param hasPassword 有没有密码
  /// @param linked 绑定的第三方账号
  /// @return 视图
  public static LoginMethodsView of(boolean hasPassword, List<ExternalIdentity> linked) {
    return new LoginMethodsView(hasPassword, linked);
  }
}
