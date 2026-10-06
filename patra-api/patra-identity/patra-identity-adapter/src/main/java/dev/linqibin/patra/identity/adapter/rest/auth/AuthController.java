package dev.linqibin.patra.identity.adapter.rest.auth;

import dev.linqibin.commons.cqrs.CommandBus;
import dev.linqibin.patra.identity.adapter.rest.auth.request.LoginRequest;
import dev.linqibin.patra.identity.adapter.rest.auth.request.RegisterRequest;
import dev.linqibin.patra.identity.adapter.rest.auth.response.UserAccountResponse;
import dev.linqibin.patra.identity.app.usecase.authenticate.AuthenticateUserCommand;
import dev.linqibin.patra.identity.app.usecase.authenticate.AuthenticateUserResult;
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

  /// 注册。成功返回 201。
  ///
  /// @param request 请求体
  /// @return 新用户的 ID 和邮箱
  @PostMapping("/register")
  @ResponseStatus(HttpStatus.CREATED)
  public UserAccountResponse register(@RequestBody RegisterRequest request) {
    RegisterUserResult result =
        commandBus.handle(RegisterUserCommand.of(request.email(), request.password()));
    return UserAccountResponse.of(result.userId(), result.email());
  }

  /// 登录（本 Issue 只做到凭据校验通过）。成功返回 200。
  ///
  /// @param request 请求体
  /// @return 用户 ID 和邮箱
  @PostMapping("/login")
  public UserAccountResponse login(@RequestBody LoginRequest request) {
    AuthenticateUserResult result =
        commandBus.handle(AuthenticateUserCommand.of(request.email(), request.password()));
    return UserAccountResponse.of(result.userId(), result.email());
  }
}
