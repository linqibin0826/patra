package dev.patra.identity.app.account.usecase.oauth;

import dev.patra.identity.app.account.usecase.login.AuthenticatedUser;
import dev.patra.tool.cqrs.Command;
import lombok.Builder;

/// 邮箱撞车时就地绑定：提交验证码，绑到发码时那个邮箱对应的账号并登录。
///
/// @param ticket 待补填凭证原文，可以为 `null`
/// @param code 验证码，原始输入
/// @param clientType 客户端类型，原始输入，可以为 `null`
/// @param deviceId 设备标识，原始输入，可以为 `null`
/// @param clientIp 客户端 IP，可以为 `null`
/// @param userAgent User-Agent，可以为 `null`
@Builder
public record LinkPendingRegistrationCommand(
    String ticket,
    String code,
    String clientType,
    String deviceId,
    String clientIp,
    String userAgent)
    implements Command<AuthenticatedUser> {

  /// 不输出凭证、验证码、设备标识和 UA 原文。
  ///
  /// @return 描述
  @Override
  public String toString() {
    return "LinkPendingRegistrationCommand[ticket=***, code=***, clientType="
        + clientType
        + ", clientIp="
        + clientIp
        + "]";
  }
}
