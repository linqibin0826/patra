package dev.patra.identity.adapter.session.rest;

import dev.patra.identity.app.session.usecase.logout.LogoutUserCommand;
import dev.patra.tool.cqrs.CommandBus;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/// 前台用户登出。只把请求转成命令，删会话在应用层做。
@Tag(name = "Auth", description = "前台用户的注册、登录、登出与当前用户")
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class LogoutController {

  private final CommandBus commandBus;

  /// 登出。有当前用户就删会话，没有也返回 204：这条路由在网关上是公开的。
  @PostMapping("/logout")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void logout() {
    commandBus.handle(LogoutUserCommand.of());
  }
}
