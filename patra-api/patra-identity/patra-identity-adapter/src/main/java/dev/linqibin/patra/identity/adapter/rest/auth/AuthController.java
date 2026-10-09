package dev.linqibin.patra.identity.adapter.rest.auth;

import dev.linqibin.commons.cqrs.CommandBus;
import dev.linqibin.patra.common.security.AccountType;
import dev.linqibin.patra.identity.adapter.rest.auth.request.LoginRequest;
import dev.linqibin.patra.identity.adapter.rest.auth.request.RegisterRequest;
import dev.linqibin.patra.identity.adapter.rest.auth.response.AuthenticatedUserResponse;
import dev.linqibin.patra.identity.adapter.rest.auth.response.CurrentUserResponse;
import dev.linqibin.patra.identity.app.usecase.login.LoginUserCommand;
import dev.linqibin.patra.identity.app.usecase.login.LoginUserResult;
import dev.linqibin.patra.identity.app.usecase.logout.LogoutUserCommand;
import dev.linqibin.patra.identity.app.usecase.register.RegisterUserCommand;
import dev.linqibin.patra.identity.app.usecase.register.RegisterUserResult;
import dev.linqibin.patra.identity.app.usecase.user.query.UserQueryService;
import dev.linqibin.patra.identity.domain.model.read.UserAccountReadModel;
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
  /// @return 会话令牌、新用户的 ID 和邮箱
  @PostMapping("/register")
  @ResponseStatus(HttpStatus.CREATED)
  public AuthenticatedUserResponse register(@RequestBody RegisterRequest request) {
    RegisterUserResult result =
        commandBus.handle(
            RegisterUserCommand.of(
                request.email(), request.password(), request.clientType(), request.deviceId()));
    return AuthenticatedUserResponse.of(result.sessionToken(), result.userId(), result.email());
  }

  /// 登录。成功返回 200。
  ///
  /// @param request 请求体
  /// @return 会话令牌、用户 ID 和邮箱
  @PostMapping("/login")
  public AuthenticatedUserResponse login(@RequestBody LoginRequest request) {
    LoginUserResult result =
        commandBus.handle(
            LoginUserCommand.of(
                request.email(), request.password(), request.clientType(), request.deviceId()));
    return AuthenticatedUserResponse.of(result.sessionToken(), result.userId(), result.email());
  }

  /// 登出。有当前用户就删会话，没有也返回 204：这条路由在网关上是公开的。
  @PostMapping("/logout")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void logout() {
    commandBus.handle(LogoutUserCommand.of());
  }

  /// 当前登录用户。没有当前用户、用户不存在或已封禁都是 401。
  ///
  /// @return 用户 ID、邮箱、账号类型
  @GetMapping("/me")
  public CurrentUserResponse me() {
    UserAccountReadModel account = userQueryService.currentAccount();
    return CurrentUserResponse.of(account.userId(), account.email(), AccountType.USER.getCode());
  }
}
