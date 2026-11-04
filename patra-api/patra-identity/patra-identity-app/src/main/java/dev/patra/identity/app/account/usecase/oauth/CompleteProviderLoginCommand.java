package dev.patra.identity.app.account.usecase.oauth;

import dev.patra.identity.domain.account.model.vo.ProviderAccount;
import dev.patra.tool.cqrs.Command;
import lombok.Builder;

/// 第三方平台确认身份之后完成登录：老用户发会话，新用户建待补填记录。
///
/// @param account 平台确认过的账号
/// @param deviceId 设备标识，原始输入，可以为 `null`；格式不对按没带处理
/// @param clientIp 客户端 IP，可以为 `null`
/// @param userAgent User-Agent，可以为 `null`
@Builder
public record CompleteProviderLoginCommand(
    ProviderAccount account, String deviceId, String clientIp, String userAgent)
    implements Command<ProviderLoginResult> {

  /// 不输出设备标识和 UA 原文。
  ///
  /// @return 描述
  @Override
  public String toString() {
    return "CompleteProviderLoginCommand[provider="
        + account.provider()
        + ", deviceId="
        + (deviceId == null ? null : "***")
        + ", clientIp="
        + clientIp
        + "]";
  }
}
