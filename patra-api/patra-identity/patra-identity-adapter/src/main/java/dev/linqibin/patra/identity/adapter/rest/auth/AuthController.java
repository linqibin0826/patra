package dev.linqibin.patra.identity.adapter.rest.auth;

import dev.linqibin.commons.cqrs.CommandBus;
import dev.linqibin.patra.identity.adapter.rest.auth.request.LoginRequest;
import dev.linqibin.patra.identity.adapter.rest.auth.request.RegisterRequest;
import dev.linqibin.patra.identity.adapter.rest.auth.response.AuthenticatedUserResponse;
import dev.linqibin.patra.identity.app.usecase.login.LoginUserCommand;
import dev.linqibin.patra.identity.app.usecase.login.LoginUserResult;
import dev.linqibin.patra.identity.app.usecase.register.RegisterUserCommand;
import dev.linqibin.patra.identity.app.usecase.register.RegisterUserResult;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/// 前台用户的注册与登录。字段校验在应用层做，这里只把请求转成命令。
@Tag(name = "Auth", description = "前台用户的注册与登录")
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

  private final CommandBus commandBus;

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
}
