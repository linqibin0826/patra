package dev.patra.identity.adapter.account.rest;

import dev.patra.identity.adapter.account.rest.request.ConfirmEmailChangeRequest;
import dev.patra.identity.adapter.account.rest.request.RevertEmailChangeRequest;
import dev.patra.identity.adapter.account.rest.response.EmailChangeRevertedResponse;
import dev.patra.identity.adapter.account.rest.response.EmailChangedResponse;
import dev.patra.identity.app.account.usecase.emailchange.ConfirmEmailChangeCommand;
import dev.patra.identity.app.account.usecase.emailchange.RevertEmailChangeCommand;
import dev.patra.tool.cqrs.CommandBus;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/// 改邮箱的确认与撤销：邮件链接的消费入口，网关上公开（新邮箱、旧邮箱在哪台设备点都要能用）。
/// 令牌在 URL 片段里，由门户落地页读出后放进请求体 POST。
@Tag(name = "Auth", description = "前台用户的注册、登录、登出与当前用户")
@RestController
@RequestMapping("/auth/email/change")
@RequiredArgsConstructor
public class EmailChangeController {

  private final CommandBus commandBus;

  /// 确认新邮箱：成功 200 并立即生效；令牌不可用 422；新地址已被注册 409。
  ///
  /// @param request 请求体
  /// @param http 原始请求，取客户端 IP
  /// @param userAgent `User-Agent` 头，可以没有；进安全通知的设备
  /// @return 新邮箱和验证时间
  @PostMapping("/confirm")
  public EmailChangedResponse confirm(
      @RequestBody ConfirmEmailChangeRequest request,
      HttpServletRequest http,
      @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent) {
    return EmailChangedResponse.from(
        commandBus.handle(
            ConfirmEmailChangeCommand.of(request.token(), ClientIp.from(http), userAgent)));
  }

  /// 撤销（「不是我」）：成功 200，邮箱改回、全部会话下线、密码作废并发重设链接；令牌不可用 422；
  /// 旧地址已被别人注册 409。
  ///
  /// @param request 请求体
  /// @param http 原始请求，取客户端 IP
  /// @param userAgent `User-Agent` 头，可以没有；进安全通知的设备
  /// @return 改回的旧邮箱
  @PostMapping("/revert")
  public EmailChangeRevertedResponse revert(
      @RequestBody RevertEmailChangeRequest request,
      HttpServletRequest http,
      @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent) {
    return EmailChangeRevertedResponse.from(
        commandBus.handle(
            RevertEmailChangeCommand.of(request.token(), ClientIp.from(http), userAgent)));
  }
}
