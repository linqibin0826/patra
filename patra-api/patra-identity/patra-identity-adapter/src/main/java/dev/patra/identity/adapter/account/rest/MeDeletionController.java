package dev.patra.identity.adapter.account.rest;

import dev.patra.identity.adapter.account.rest.request.RequestAccountDeletionRequest;
import dev.patra.identity.adapter.account.rest.response.AccountDeletionRequestedResponse;
import dev.patra.identity.adapter.account.rest.response.CurrentUserResponse;
import dev.patra.identity.app.account.usecase.deletion.CancelAccountDeletionCommand;
import dev.patra.identity.app.account.usecase.deletion.RequestAccountDeletionCommand;
import dev.patra.tool.cqrs.CommandBus;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/// 注销账号，网关上 `/me/**` 需要登录：发起（再认证 + 输入用户名确认）与冷静期内取消。
@Tag(name = "Me", description = "当前用户的账号设置")
@RestController
@RequestMapping("/me/deletion")
@RequiredArgsConstructor
public class MeDeletionController {

  private final CommandBus commandBus;

  /// 发起注销，200 带到期时间；本机会话也已撤销。422 用户名为空或不一致（`username` / `REQUIRED`、`MISMATCH`）；
  /// 409 已在冷静期（`traits` 含 `PENDING_DELETION`）；403 超过再认证窗口（`REAUTH_REQUIRED`）。
  ///
  /// @param request 请求体
  /// @param http 原始请求，取客户端 IP
  /// @param userAgent `User-Agent` 头，可以没有；进「注销已提交」通知的设备
  /// @return 到期时间
  @PostMapping
  public AccountDeletionRequestedResponse request(
      @RequestBody RequestAccountDeletionRequest request,
      HttpServletRequest http,
      @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent) {
    return AccountDeletionRequestedResponse.from(
        commandBus.handle(
            RequestAccountDeletionCommand.of(request.username(), ClientIp.from(http), userAgent)));
  }

  /// 取消注销，200 带与 `/auth/me` 同构的账号信息；本来就正常时照样 200。不要求再认证。
  ///
  /// @param http 原始请求，取客户端 IP
  /// @param userAgent `User-Agent` 头，可以没有；进「已取消注销」通知的设备
  /// @return 账号信息
  @DeleteMapping
  public CurrentUserResponse cancel(
      HttpServletRequest http,
      @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent) {
    return CurrentUserResponse.from(
        commandBus.handle(CancelAccountDeletionCommand.of(ClientIp.from(http), userAgent)));
  }
}
