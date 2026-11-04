package dev.patra.identity.adapter.account.rest.response;

import dev.patra.identity.app.account.usecase.query.LoginMethodsView;
import dev.patra.identity.domain.account.model.aggregate.ExternalIdentity;
import java.time.Instant;
import java.util.List;

/// `GET /me/login-methods` 的响应体。
///
/// @param hasPassword 有没有密码
/// @param linked 绑定的第三方账号；没绑的平台不在里面
public record LoginMethodsResponse(boolean hasPassword, List<LinkedProvider> linked) {

  /// 拷贝集合。
  public LoginMethodsResponse {
    linked = linked != null ? List.copyOf(linked) : List.of();
  }

  /// 绑定的一家。
  ///
  /// @param provider 平台的小写名
  /// @param accountLabel 账号标识：Google 是邮箱，GitHub 是 `login`，ORCID 是 iD
  /// @param linkedAt 绑定时间
  /// @param lastLoginAt 最近一次用它登录的时间，没用过为 `null`
  public record LinkedProvider(
      String provider, String accountLabel, Instant linkedAt, Instant lastLoginAt) {

    /// 从聚合映射。
    ///
    /// @param identity 外部身份
    /// @return 响应
    static LinkedProvider from(ExternalIdentity identity) {
      return new LinkedProvider(
          identity.getProvider().registrationId(),
          identity.getAccountLabel(),
          identity.getLinkedAt(),
          identity.getLastLoginAt());
    }
  }

  /// 从视图映射。
  ///
  /// @param view 视图
  /// @return 响应
  public static LoginMethodsResponse from(LoginMethodsView view) {
    return new LoginMethodsResponse(
        view.hasPassword(), view.linked().stream().map(LinkedProvider::from).toList());
  }
}
