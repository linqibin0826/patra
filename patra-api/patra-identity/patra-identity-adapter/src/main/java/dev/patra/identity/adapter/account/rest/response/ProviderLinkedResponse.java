package dev.patra.identity.adapter.account.rest.response;

import dev.patra.identity.app.account.usecase.oauth.ProviderLinked;

/// 第三方登录回调：绑定成功。
///
/// @param result 固定 `LINKED`
/// @param provider 平台的小写名
/// @param accountLabel 账号标识
public record ProviderLinkedResponse(String result, String provider, String accountLabel) {

  /// 从用例结果映射。
  ///
  /// @param linked 用例结果
  /// @return 响应
  public static ProviderLinkedResponse from(ProviderLinked linked) {
    return new ProviderLinkedResponse(
        "LINKED", linked.provider().registrationId(), linked.accountLabel());
  }
}
