package dev.patra.identity.adapter.rest.me;

import dev.patra.identity.adapter.rest.me.request.ChangeUsernameRequest;
import dev.patra.identity.adapter.rest.me.response.ChangeUsernameResponse;
import dev.patra.identity.app.usecase.username.ChangeUsernameCommand;
import dev.patra.identity.app.usecase.username.ChangeUsernameResult;
import dev.patra.tool.cqrs.CommandBus;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/// 当前用户自己的账号设置，网关上 `/me/**` 需要登录。本 Issue 只有改用户名；PAP-95 等在这里加资料、隐私。
@Tag(name = "Me", description = "当前用户的账号设置")
@RestController
@RequestMapping("/me")
@RequiredArgsConstructor
public class MeController {

  private final CommandBus commandBus;

  /// 改用户名。成功返回 200；422 不合法或与当前相同，409 已被使用、账号注销中或冷却期内。
  ///
  /// @param request 请求体
  /// @return 新用户名和下次可以改名的时间
  @PatchMapping("/username")
  public ChangeUsernameResponse changeUsername(@RequestBody ChangeUsernameRequest request) {
    ChangeUsernameResult result = commandBus.handle(ChangeUsernameCommand.of(request.username()));
    return ChangeUsernameResponse.of(result.username(), result.nextUsernameChangeAt());
  }
}
