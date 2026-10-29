package dev.patra.identity.adapter.account.rest;

import dev.patra.identity.adapter.account.rest.request.VerifyEmailRequest;
import dev.patra.identity.adapter.account.rest.response.VerifyEmailResponse;
import dev.patra.identity.app.account.usecase.email.VerifyEmailCommand;
import dev.patra.tool.cqrs.CommandBus;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/// 邮件链接的消费入口。网关上公开（匿名设备点链接也要能验证），但照样翻译令牌：带着会话来的请求有当前用户。
/// 只收 POST：令牌在 URL 片段里，由门户落地页读出后放进请求体，不让邮件安全网关的 GET 预取把它用掉。
@Tag(name = "Auth", description = "前台用户的注册、登录、登出与当前用户")
@RestController
@RequestMapping("/auth/email")
@RequiredArgsConstructor
public class EmailVerificationController {

  private final CommandBus commandBus;

  /// 用令牌验证邮箱。成功返回 200；令牌为空、格式不对、不存在、过期、已用、已作废都是 422。
  ///
  /// @param request 请求体
  /// @return 验证的邮箱和验证时间
  @PostMapping("/verify")
  public VerifyEmailResponse verify(@RequestBody VerifyEmailRequest request) {
    return VerifyEmailResponse.from(commandBus.handle(VerifyEmailCommand.of(request.token())));
  }
}
