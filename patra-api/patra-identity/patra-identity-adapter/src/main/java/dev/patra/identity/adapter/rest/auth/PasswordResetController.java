package dev.patra.identity.adapter.rest.auth;

import dev.patra.identity.adapter.rest.auth.request.ForgotPasswordRequest;
import dev.patra.identity.adapter.rest.auth.request.ResetPasswordRequest;
import dev.patra.identity.adapter.rest.auth.response.PasswordResetRequestedResponse;
import dev.patra.identity.adapter.rest.auth.response.PasswordResetResponse;
import dev.patra.identity.app.usecase.password.RequestPasswordResetCommand;
import dev.patra.identity.app.usecase.password.ResetPasswordCommand;
import dev.patra.tool.cqrs.CommandBus;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/// 找回密码：申请与提交，网关上都公开。令牌在 URL 片段里，由门户落地页读出后放进请求体 POST。
@Tag(name = "Auth", description = "前台用户的注册、登录、登出与当前用户")
@RestController
@RequestMapping("/auth/password")
@RequiredArgsConstructor
public class PasswordResetController {

  private final CommandBus commandBus;

  /// 申请重设：一律 202（已注册才发信），429 过于频繁（带 `Retry-After`），422 邮箱格式不对。
  ///
  /// @param request 请求体
  /// @param http 原始请求，取客户端 IP 给限流和签发记录
  /// @return 邮箱和下次可申请时间
  @PostMapping("/forgot")
  @ResponseStatus(HttpStatus.ACCEPTED)
  public PasswordResetRequestedResponse forgot(
      @RequestBody ForgotPasswordRequest request, HttpServletRequest http) {
    return PasswordResetRequestedResponse.from(
        commandBus.handle(RequestPasswordResetCommand.of(request.email(), http.getRemoteAddr())));
  }

  /// 提交新密码：成功 200（不自动登录）；令牌不可用或密码不合规 422；429 过于频繁。
  ///
  /// @param request 请求体
  /// @param http 原始请求，取客户端 IP
  /// @return 重设了密码的账号邮箱
  @PostMapping("/reset")
  public PasswordResetResponse reset(
      @RequestBody ResetPasswordRequest request, HttpServletRequest http) {
    return PasswordResetResponse.from(
        commandBus.handle(
            ResetPasswordCommand.of(request.token(), request.newPassword(), http.getRemoteAddr())));
  }
}
