package dev.patra.identity.adapter.rest.me;

import dev.patra.identity.adapter.rest.me.response.ResendEmailVerificationResponse;
import dev.patra.identity.app.usecase.email.ResendEmailVerificationCommand;
import dev.patra.tool.cqrs.CommandBus;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/// 当前用户的邮箱设置，网关上 `/me/**` 需要登录。本 Issue 只有重发验证邮件；改邮箱在 PAP-98 加到这里。
@Tag(name = "Me", description = "当前用户的账号设置")
@RestController
@RequestMapping("/me/email")
@RequiredArgsConstructor
public class MeEmailController {

  private final CommandBus commandBus;

  /// 重发验证邮件。邮件异步发出，所以是 202；429 过于频繁（带 `Retry-After`），409 已验证或注销中，401 未登录。
  ///
  /// @param http 原始请求，取客户端 IP 给限流和签发记录
  /// @return 收件邮箱和下次可重发时间
  @PostMapping("/verification/resend")
  @ResponseStatus(HttpStatus.ACCEPTED)
  public ResendEmailVerificationResponse resendVerification(HttpServletRequest http) {
    return ResendEmailVerificationResponse.from(
        commandBus.handle(ResendEmailVerificationCommand.of(http.getRemoteAddr())));
  }
}
