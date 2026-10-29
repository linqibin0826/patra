package dev.patra.identity.adapter.account.rest;

import dev.patra.identity.adapter.account.rest.request.ChangeEmailRequest;
import dev.patra.identity.adapter.account.rest.response.EmailChangeRequestedResponse;
import dev.patra.identity.adapter.account.rest.response.PendingEmailChangeResponse;
import dev.patra.identity.adapter.account.rest.response.ResendEmailVerificationResponse;
import dev.patra.identity.app.account.usecase.email.ResendEmailVerificationCommand;
import dev.patra.identity.app.account.usecase.emailchange.CancelEmailChangeCommand;
import dev.patra.identity.app.account.usecase.emailchange.RequestEmailChangeCommand;
import dev.patra.identity.app.account.usecase.query.UserQueryService;
import dev.patra.tool.cqrs.CommandBus;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/// 当前用户的邮箱设置，网关上 `/me/**` 需要登录：重发验证邮件；改邮箱的申请、查看待确认、取消。
@Tag(name = "Me", description = "当前用户的账号设置")
@RestController
@RequestMapping("/me/email")
@RequiredArgsConstructor
public class MeEmailController {

  private final CommandBus commandBus;
  private final UserQueryService userQueryService;

  /// 重发验证邮件。邮件异步发出，所以是 202；429 过于频繁（带 `Retry-After`），409 已验证或注销中，401 未登录。
  ///
  /// @param http 原始请求，取客户端 IP 给限流和签发记录
  /// @return 收件邮箱和下次可重发时间
  @PostMapping("/verification/resend")
  @ResponseStatus(HttpStatus.ACCEPTED)
  public ResendEmailVerificationResponse resendVerification(HttpServletRequest http) {
    return ResendEmailVerificationResponse.from(
        commandBus.handle(ResendEmailVerificationCommand.of(ClientIp.from(http))));
  }

  /// 申请改邮箱：向新邮箱发 1 小时有效的确认链接，202。422 字段错误或与当前相同；403 超过再认证窗口
  /// （`traits` 含 `REAUTH_REQUIRED`）；409 已有待确认（`EMAIL_CHANGE_PENDING`）或新邮箱已被使用
  /// （`errors[]` 的 `email`）；429 今天的配额用完。
  ///
  /// @param request 请求体
  /// @param http 原始请求，取客户端 IP
  /// @return 新邮箱和确认链接的过期时间
  @PostMapping("/change")
  @ResponseStatus(HttpStatus.ACCEPTED)
  public EmailChangeRequestedResponse requestChange(
      @RequestBody ChangeEmailRequest request, HttpServletRequest http) {
    return EmailChangeRequestedResponse.from(
        commandBus.handle(RequestEmailChangeCommand.of(request.newEmail(), ClientIp.from(http))));
  }

  /// 待确认的改邮箱：有 200，没有 204。
  ///
  /// @return 新邮箱与到期时间
  @GetMapping("/change")
  public ResponseEntity<PendingEmailChangeResponse> pendingChange() {
    return userQueryService
        .pendingEmailChange()
        .map(pending -> ResponseEntity.ok(PendingEmailChangeResponse.from(pending)))
        .orElseGet(() -> ResponseEntity.noContent().build());
  }

  /// 取消待确认的改邮箱，204；没有待确认也 204。
  @DeleteMapping("/change")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void cancelChange() {
    commandBus.handle(CancelEmailChangeCommand.of());
  }
}
