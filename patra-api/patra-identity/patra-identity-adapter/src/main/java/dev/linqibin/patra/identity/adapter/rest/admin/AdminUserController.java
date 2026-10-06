package dev.linqibin.patra.identity.adapter.rest.admin;

import dev.linqibin.commons.cqrs.CommandBus;
import dev.linqibin.patra.identity.app.usecase.ban.BanUserCommand;
import dev.linqibin.patra.identity.app.usecase.ban.UnbanUserCommand;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/// 后台接口：封禁与解封前台用户。
///
/// 本版没有后台账号，接口本身不做身份校验；网关拒绝外部访问 `/admin/**`（PAP-65），只能在内网直连调用。
@Tag(name = "Admin", description = "后台接口：封禁与解封前台用户")
@RestController
@RequestMapping("/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

  private final CommandBus commandBus;

  /// 封禁。幂等，成功返回 204。
  ///
  /// @param userId 用户 ID
  @PostMapping("/{userId}/ban")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void ban(@PathVariable long userId) {
    commandBus.handle(BanUserCommand.of(userId));
  }

  /// 解封。幂等，成功返回 204。
  ///
  /// @param userId 用户 ID
  @PostMapping("/{userId}/unban")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void unban(@PathVariable long userId) {
    commandBus.handle(UnbanUserCommand.of(userId));
  }
}
