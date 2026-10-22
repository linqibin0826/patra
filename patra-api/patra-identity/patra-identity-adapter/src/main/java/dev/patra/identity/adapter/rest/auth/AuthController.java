package dev.patra.identity.adapter.rest.auth;

import dev.patra.identity.adapter.rest.auth.request.LoginRequest;
import dev.patra.identity.adapter.rest.auth.request.ReauthRequest;
import dev.patra.identity.adapter.rest.auth.request.RegisterRequest;
import dev.patra.identity.adapter.rest.auth.response.AuthenticatedUserResponse;
import dev.patra.identity.adapter.rest.auth.response.CurrentUserResponse;
import dev.patra.identity.app.usecase.auth.AuthenticatedUser;
import dev.patra.identity.app.usecase.login.LoginUserCommand;
import dev.patra.identity.app.usecase.logout.LogoutUserCommand;
import dev.patra.identity.app.usecase.reauth.ReauthenticateUserCommand;
import dev.patra.identity.app.usecase.register.RegisterUserCommand;
import dev.patra.identity.app.usecase.user.query.UserQueryService;
import dev.patra.tool.cqrs.CommandBus;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/// 前台用户的注册、登录、再认证、登出与当前用户。字段校验在应用层做，这里只把请求转成命令；读操作直接走查询服务。
@Tag(name = "Auth", description = "前台用户的注册、登录、登出与当前用户")
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

  private final CommandBus commandBus;
  private final UserQueryService userQueryService;

  /// 注册并登录。成功返回 201。客户端 IP 取 `getRemoteAddr()`：网关加的 `X-Forwarded-For` 已按
  /// `forward-headers-strategy: framework` 解析；门户是 BFF，所以在受信转发链路接通前这是门户的地址。
  ///
  /// @param request 请求体
  /// @param http 原始请求，取客户端 IP
  /// @return 会话令牌和新用户的账号信息
  @PostMapping("/register")
  @ResponseStatus(HttpStatus.CREATED)
  public AuthenticatedUserResponse register(
      @RequestBody RegisterRequest request, HttpServletRequest http) {
    AuthenticatedUser result =
        commandBus.handle(
            RegisterUserCommand.builder()
                .email(request.email())
                .username(request.username())
                .password(request.password())
                .clientType(request.clientType())
                .deviceId(request.deviceId())
                .clientIp(http.getRemoteAddr())
                .build());
    return toResponse(result);
  }

  /// 登录。成功返回 200。
  ///
  /// @param request 请求体
  /// @return 会话令牌和账号信息
  @PostMapping("/login")
  public AuthenticatedUserResponse login(@RequestBody LoginRequest request) {
    AuthenticatedUser result =
        commandBus.handle(
            LoginUserCommand.of(
                request.email(), request.password(), request.clientType(), request.deviceId()));
    return toResponse(result);
  }

  /// 再认证：重新输一次密码，通过后换新令牌（会话 ID 不变）并刷新再认证时间。成功 200，和登录同构；
  /// 密码错 401（`traits` 含 `PASSWORD_MISMATCH`），锁定 429，封禁 / 注销 401。
  ///
  /// @param request 请求体
  /// @return 新令牌和账号信息
  @PostMapping("/reauth")
  public AuthenticatedUserResponse reauth(@RequestBody ReauthRequest request) {
    return toResponse(commandBus.handle(ReauthenticateUserCommand.of(request.password())));
  }

  /// 登出。有当前用户就删会话，没有也返回 204：这条路由在网关上是公开的。
  @PostMapping("/logout")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void logout() {
    commandBus.handle(LogoutUserCommand.of());
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
