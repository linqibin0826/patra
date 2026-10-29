package dev.patra.identity.adapter.session.rest;

import dev.patra.identity.adapter.session.rest.response.SessionListResponse;
import dev.patra.identity.adapter.session.rest.response.SessionsRevokedResponse;
import dev.patra.identity.app.session.usecase.query.SessionQueryService;
import dev.patra.identity.app.session.usecase.revoke.RevokeOtherSessionsCommand;
import dev.patra.identity.app.session.usecase.revoke.RevokeSessionCommand;
import dev.patra.tool.cqrs.CommandBus;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/// 当前用户的登录设备（PAP-99），网关上 `/me/**` 需要登录：列出、踢单个、踢其他全部。
@Tag(name = "Me", description = "当前用户的账号设置")
@RestController
@RequestMapping("/me/sessions")
@RequiredArgsConstructor
public class MeSessionController {

  private final CommandBus commandBus;
  private final SessionQueryService sessionQueryService;

  /// 登录设备列表，只含活会话，本机第一。401 当前会话已不在。
  ///
  /// @return 列表
  @GetMapping
  public SessionListResponse list() {
    return SessionListResponse.from(sessionQueryService.list());
  }

  /// 踢掉除本机外的全部设备。403 超过再认证窗口（`traits` 含 `REAUTH_REQUIRED`）。
  ///
  /// @return 被踢掉的会话数
  @DeleteMapping("/others")
  public SessionsRevokedResponse revokeOthers() {
    return SessionsRevokedResponse.from(commandBus.handle(RevokeOtherSessionsCommand.of()));
  }

  /// 踢掉一个设备。已经不在也是 200（数量 0）；409 踢的是本机（`CURRENT_SESSION`，本机走退出登录）；
  /// 403 超过再认证窗口。
  ///
  /// @param id 会话 ID
  /// @return 被踢掉的会话数
  @DeleteMapping("/{id}")
  public SessionsRevokedResponse revoke(@PathVariable long id) {
    return SessionsRevokedResponse.from(commandBus.handle(RevokeSessionCommand.of(id)));
  }
}
