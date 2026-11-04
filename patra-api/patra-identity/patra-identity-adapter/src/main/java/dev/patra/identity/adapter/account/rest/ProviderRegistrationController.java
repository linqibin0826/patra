package dev.patra.identity.adapter.account.rest;

import dev.patra.identity.adapter.account.rest.request.CompleteProviderRegistrationRequest;
import dev.patra.identity.adapter.account.rest.request.SendRegistrationCodeRequest;
import dev.patra.identity.adapter.account.rest.response.AuthenticatedUserResponse;
import dev.patra.identity.adapter.account.rest.response.CurrentUserResponse;
import dev.patra.identity.adapter.account.rest.response.PendingRegistrationResponse;
import dev.patra.identity.adapter.account.rest.response.RegistrationCodeSentResponse;
import dev.patra.identity.app.account.usecase.login.AuthenticatedUser;
import dev.patra.identity.app.account.usecase.oauth.CompleteProviderRegistrationCommand;
import dev.patra.identity.app.account.usecase.oauth.SendRegistrationCodeCommand;
import dev.patra.identity.app.account.usecase.query.PendingRegistrationQueryService;
import dev.patra.tool.cqrs.CommandBus;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/// 第三方首次注册：补填用户名和邮箱、发验证码、验证通过后建账号。网关上公开，凭证在请求头里，不进 URL。
@Tag(name = "Auth", description = "前台用户的注册、登录、登出与当前用户")
@RestController
@RequestMapping("/auth/oauth/registration")
@RequiredArgsConstructor
public class ProviderRegistrationController {

  /// 放待补填凭证的请求头：门户从 HttpOnly Cookie 读出来转发。
  private static final String TICKET_HEADER = "X-Patra-Registration-Ticket";

  private final CommandBus commandBus;
  private final PendingRegistrationQueryService pendingQueries;

  /// 补填页要显示的内容。凭证不可用 422。
  ///
  /// @param ticket 待补填凭证，可以没有
  /// @return 预填值和发出去还没用掉的验证码
  @GetMapping
  public PendingRegistrationResponse pending(
      @RequestHeader(value = TICKET_HEADER, required = false) String ticket) {
    return PendingRegistrationResponse.from(pendingQueries.view(ticket));
  }

  /// 提交用户名和邮箱，发验证码，重发也调它。成功 202；422 字段或凭证不合法，409 邮箱或用户名已被使用，
  /// 429 过于频繁（带 `Retry-After`）。
  ///
  /// @param ticket 待补填凭证，可以没有
  /// @param request 请求体
  /// @param http 原始请求，取客户端 IP 给限流
  /// @return 邮箱和时间
  @PostMapping("/code")
  @ResponseStatus(HttpStatus.ACCEPTED)
  public RegistrationCodeSentResponse sendCode(
      @RequestHeader(value = TICKET_HEADER, required = false) String ticket,
      @RequestBody SendRegistrationCodeRequest request,
      HttpServletRequest http) {
    return RegistrationCodeSentResponse.from(
        commandBus.handle(
            SendRegistrationCodeCommand.builder()
                .ticket(ticket)
                .username(request.username())
                .email(request.email())
                .clientIp(ClientIp.from(http))
                .build()));
  }

  /// 提交验证码，建账号并登录。成功 201，和注册同构；422 验证码或凭证不可用，409 邮箱、用户名或第三方账号已被使用。
  ///
  /// @param ticket 待补填凭证，可以没有
  /// @param request 请求体
  /// @param http 原始请求，取客户端 IP
  /// @param userAgent `User-Agent` 头，可以没有
  /// @return 会话令牌和新用户的账号信息
  @PostMapping("/complete")
  @ResponseStatus(HttpStatus.CREATED)
  public AuthenticatedUserResponse complete(
      @RequestHeader(value = TICKET_HEADER, required = false) String ticket,
      @RequestBody CompleteProviderRegistrationRequest request,
      HttpServletRequest http,
      @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent) {
    AuthenticatedUser result =
        commandBus.handle(
            CompleteProviderRegistrationCommand.builder()
                .ticket(ticket)
                .code(request.code())
                .clientType(request.clientType())
                .deviceId(request.deviceId())
                .clientIp(ClientIp.from(http))
                .userAgent(userAgent)
                .build());
    return AuthenticatedUserResponse.of(
        result.sessionToken(), CurrentUserResponse.from(result.account()));
  }
}
