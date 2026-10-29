package dev.patra.identity.adapter.account.rest;

import dev.patra.identity.adapter.account.rest.request.LoginRequest;
import dev.patra.identity.adapter.account.rest.request.ReauthRequest;
import dev.patra.identity.adapter.account.rest.request.RegisterRequest;
import dev.patra.identity.adapter.account.rest.response.AuthenticatedUserResponse;
import dev.patra.identity.adapter.account.rest.response.CurrentUserResponse;
import dev.patra.identity.adapter.account.rest.response.ReauthenticatedResponse;
import dev.patra.identity.app.account.usecase.login.AuthenticatedUser;
import dev.patra.identity.app.account.usecase.login.LoginUserCommand;
import dev.patra.identity.app.account.usecase.query.UserQueryService;
import dev.patra.identity.app.account.usecase.reauth.ReauthenticateUserCommand;
import dev.patra.identity.app.account.usecase.reauth.Reauthenticated;
import dev.patra.identity.app.account.usecase.register.RegisterUserCommand;
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

/// 前台用户的注册、登录、再认证与当前用户（登出在 session 的 `LogoutController`）。字段校验在应用层做，这里只把请求转成命令；读操作直接走查询服务。
@Tag(name = "Auth", description = "前台用户的注册、登录、登出与当前用户")
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

  private final CommandBus commandBus;
  private final UserQueryService userQueryService;

  /// 注册并登录。成功返回 201。客户端 IP 取规范化之后的 `getRemoteAddr()`（{@link ClientIp}）：网关写的 `Forwarded` /
  /// `X-Forwarded-For` 已按 `forward-headers-strategy: framework` 解析，经受信转发链路（门户 → 网关）
  /// 是浏览器的 IP（PAP-99）；UA 取 `User-Agent` 头，门户转来的是浏览器的原值。
  ///
  /// @param request 请求体
  /// @param http 原始请求，取客户端 IP
  /// @param userAgent `User-Agent` 头，可以没有
  /// @return 会话令牌和新用户的账号信息
  @PostMapping("/register")
  @ResponseStatus(HttpStatus.CREATED)
  public AuthenticatedUserResponse register(
      @RequestBody RegisterRequest request,
      HttpServletRequest http,
      @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent) {
    AuthenticatedUser result =
        commandBus.handle(
            RegisterUserCommand.builder()
                .email(request.email())
                .username(request.username())
                .password(request.password())
                .clientType(request.clientType())
                .deviceId(request.deviceId())
                .clientIp(ClientIp.from(http))
                .userAgent(userAgent)
                .build());
    return toResponse(result);
  }

  /// 登录。成功返回 200。客户端 IP 与 UA 的取法同注册。
  ///
  /// @param request 请求体
  /// @param http 原始请求，取客户端 IP
  /// @param userAgent `User-Agent` 头，可以没有
  /// @return 会话令牌和账号信息
  @PostMapping("/login")
  public AuthenticatedUserResponse login(
      @RequestBody LoginRequest request,
      HttpServletRequest http,
      @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent) {
    AuthenticatedUser result =
        commandBus.handle(
            LoginUserCommand.builder()
                .email(request.email())
                .password(request.password())
                .clientType(request.clientType())
                .deviceId(request.deviceId())
                .clientIp(ClientIp.from(http))
                .userAgent(userAgent)
                .build());
    return toResponse(result);
  }

  /// 再认证：重新输一次密码，通过后换新令牌（会话 ID 不变）并刷新再认证时间。成功 200，和登录同构，
  /// 但 `sessionToken` 为 `null` 时是宽限期内没换，客户端保持现有令牌；
  /// 密码错 401（`traits` 含 `PASSWORD_MISMATCH`），锁定 429，封禁 / 注销 401。
  ///
  /// @param request 请求体
  /// @return 账号信息和新令牌
  @PostMapping("/reauth")
  public ReauthenticatedResponse reauth(@RequestBody ReauthRequest request) {
    Reauthenticated result = commandBus.handle(ReauthenticateUserCommand.of(request.password()));
    return ReauthenticatedResponse.of(
        result.sessionToken(), CurrentUserResponse.from(result.account()));
  }

  /// 当前登录用户。没有当前用户、用户不存在、已封禁或已注销都是 401。
  ///
  /// @return 账号信息
  @GetMapping("/me")
  public CurrentUserResponse me() {
    return CurrentUserResponse.from(userQueryService.currentAccount());
  }

  /// 把登录或注册的结果转成响应。
  ///
  /// @param result 结果
  /// @return 响应
  private static AuthenticatedUserResponse toResponse(AuthenticatedUser result) {
    return AuthenticatedUserResponse.of(
        result.sessionToken(), CurrentUserResponse.from(result.account()));
  }
}
