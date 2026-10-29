package dev.patra.identity.adapter.account.rest;

import dev.patra.identity.adapter.account.rest.request.ChangePasswordRequest;
import dev.patra.identity.adapter.account.rest.request.ChangeUsernameRequest;
import dev.patra.identity.adapter.account.rest.response.AuthenticatedUserResponse;
import dev.patra.identity.adapter.account.rest.response.ChangeUsernameResponse;
import dev.patra.identity.adapter.account.rest.response.CurrentUserResponse;
import dev.patra.identity.app.account.usecase.login.AuthenticatedUser;
import dev.patra.identity.app.account.usecase.password.ChangePasswordCommand;
import dev.patra.identity.app.account.usecase.username.ChangeUsernameCommand;
import dev.patra.identity.app.account.usecase.username.ChangeUsernameResult;
import dev.patra.tool.cqrs.CommandBus;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/// 当前用户自己的账号设置，网关上 `/me/**` 需要登录：改用户名、改密码；PAP-95 等在这里加资料、隐私。
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

  /// 改密码。成功返回 200，和登录同构（本机的新令牌，门户要换 Cookie）；422 字段错误或新旧相同，
  /// 401 当前密码错（`traits` 含 `PASSWORD_MISMATCH`），429 登录限制锁定，409 注销中。
  ///
  /// @param request 请求体
  /// @param http 原始请求，取客户端 IP
  /// @param userAgent `User-Agent` 头，可以没有；进「密码已修改」通知的设备
  /// @return 新令牌和账号信息
  @PutMapping("/password")
  public AuthenticatedUserResponse changePassword(
      @RequestBody ChangePasswordRequest request,
      HttpServletRequest http,
      @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent) {
    AuthenticatedUser result =
        commandBus.handle(
            ChangePasswordCommand.of(
                request.currentPassword(), request.newPassword(), ClientIp.from(http), userAgent));
    return AuthenticatedUserResponse.of(
        result.sessionToken(), CurrentUserResponse.from(result.account()));
  }
}
