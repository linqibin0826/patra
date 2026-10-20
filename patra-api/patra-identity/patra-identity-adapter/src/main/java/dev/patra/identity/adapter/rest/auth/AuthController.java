package dev.patra.identity.adapter.rest.auth;

import dev.patra.identity.adapter.rest.auth.request.LoginRequest;
import dev.patra.identity.adapter.rest.auth.request.RegisterRequest;
import dev.patra.identity.adapter.rest.auth.response.AuthenticatedUserResponse;
import dev.patra.identity.adapter.rest.auth.response.CurrentUserResponse;
import dev.patra.identity.app.usecase.auth.AuthenticatedUser;
import dev.patra.identity.app.usecase.login.LoginUserCommand;
import dev.patra.identity.app.usecase.logout.LogoutUserCommand;
import dev.patra.identity.app.usecase.register.RegisterUserCommand;
import dev.patra.identity.app.usecase.user.query.UserQueryService;
import dev.patra.tool.cqrs.CommandBus;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/// 前台用户的注册、登录、登出与当前用户。字段校验在应用层做，这里只把请求转成命令；读操作直接走查询服务。
@Tag(name = "Auth", description = "前台用户的注册、登录、登出与当前用户")
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

  private final CommandBus commandBus;
  private final UserQueryService userQueryService;

  /// 注册并登录。成功返回 201。
  ///
  /// @param request 请求体
  /// @return 会话令牌和新用户的账号信息
  @PostMapping("/register")
  @ResponseStatus(HttpStatus.CREATED)
  public AuthenticatedUserResponse register(@RequestBody RegisterRequest request) {
    AuthenticatedUser result =
        commandBus.handle(
            RegisterUserCommand.builder()
                .email(request.email())
                .username(request.username())
                .password(request.password())
                .clientType(request.clientType())
                .deviceId(request.deviceId())
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
