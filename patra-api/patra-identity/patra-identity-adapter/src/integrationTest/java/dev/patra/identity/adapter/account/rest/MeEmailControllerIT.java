package dev.patra.identity.adapter.account.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.patra.identity.app.account.usecase.email.EmailVerificationResent;
import dev.patra.identity.app.account.usecase.email.ResendEmailVerificationCommand;
import dev.patra.identity.app.account.usecase.emailchange.CancelEmailChangeCommand;
import dev.patra.identity.app.account.usecase.emailchange.EmailChangeRequested;
import dev.patra.identity.app.account.usecase.emailchange.RequestEmailChangeCommand;
import dev.patra.identity.app.account.usecase.query.PendingEmailChange;
import dev.patra.identity.app.account.usecase.query.UserQueryService;
import dev.patra.identity.domain.account.exception.EmailAlreadyVerifiedException;
import dev.patra.identity.domain.account.exception.EmailChangeLimitedException;
import dev.patra.identity.domain.account.exception.EmailChangePendingException;
import dev.patra.identity.domain.account.exception.EmailResendLimitedException;
import dev.patra.identity.domain.account.exception.UserIdentifierTakenException;
import dev.patra.identity.domain.account.exception.UserPendingDeletionException;
import dev.patra.identity.domain.session.exception.ReauthenticationRequiredException;
import dev.patra.identity.domain.session.model.vo.UserFieldViolations;
import dev.patra.tool.core.security.AuthenticationRequiredException;
import dev.patra.tool.cqrs.CommandBus;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.client.RestTestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/// MeEmailController 切片测试：重发验证邮件的 202 / 429 / 409 / 401；改邮箱的申请、查看、取消。
@WebMvcTest
@Import(MeEmailController.class)
@AutoConfigureRestTestClient
@DisplayName("MeEmailController 切片测试")
class MeEmailControllerIT {

  private static final JsonMapper JSON = JsonMapper.builder().build();

  @Autowired private RestTestClient restClient;

  @MockitoBean private CommandBus commandBus;

  @MockitoBean private UserQueryService userQueryService;

  /// 发重发请求。
  ///
  /// @return 响应体
  private String resend() {
    return restClient
        .post()
        .uri("/me/email/verification/resend")
        .exchange()
        .expectBody(String.class)
        .returnResult()
        .getResponseBody();
  }

  @Test
  @DisplayName("重发成功：202，带邮箱和下次可重发时间；客户端 IP 交给命令")
  void should_accept_resend_with_202() {
    when(commandBus.handle(any(ResendEmailVerificationCommand.class)))
        .thenReturn(
            EmailVerificationResent.of(
                "chen.yu@example.com", Instant.parse("2026-10-21T08:01:00Z")));

    restClient
        .post()
        .uri("/me/email/verification/resend")
        .exchange()
        .expectStatus()
        .isAccepted()
        .expectBody()
        .jsonPath("$.email")
        .isEqualTo("chen.yu@example.com")
        .jsonPath("$.nextResendAt")
        .isEqualTo("2026-10-21T08:01:00Z");

    ArgumentCaptor<ResendEmailVerificationCommand> command =
        ArgumentCaptor.forClass(ResendEmailVerificationCommand.class);
    verify(commandBus).handle(command.capture());
    assertThat(command.getValue().clientIp()).isEqualTo("127.0.0.1");
  }

  @Test
  @DisplayName("被限流：429，Retry-After 向上取整到秒，体里 retryAfterSeconds 同值")
  void should_render_limit_as_429_with_retry_after() {
    when(commandBus.handle(any(ResendEmailVerificationCommand.class)))
        .thenThrow(new EmailResendLimitedException(Duration.ofMillis(36_500)));

    restClient
        .post()
        .uri("/me/email/verification/resend")
        .exchange()
        .expectStatus()
        .isEqualTo(429)
        .expectHeader()
        .valueEquals(HttpHeaders.RETRY_AFTER, "37")
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("IDN-0429")
        .jsonPath("$.retryAfterSeconds")
        .isEqualTo(37)
        .jsonPath("$.detail")
        .isEqualTo("发送过于频繁，请稍后再试");
  }

  @Test
  @DisplayName("已验证：409，traits 同时含 CONFLICT 和 EMAIL_ALREADY_VERIFIED")
  void should_render_already_verified_as_409_with_trait() {
    when(commandBus.handle(any(ResendEmailVerificationCommand.class)))
        .thenThrow(new EmailAlreadyVerifiedException());

    JsonNode json = JSON.readTree(resend());

    assertThat(json.get("status").asInt()).isEqualTo(409);
    assertThat(json.get("code").asString()).isEqualTo("IDN-0409");
    assertThat(json.get("traits"))
        .extracting(JsonNode::asString)
        .containsExactlyInAnyOrder("CONFLICT", "EMAIL_ALREADY_VERIFIED");
    assertThat(json.get("detail").asString()).isEqualTo("邮箱已经验证过了");
  }

  @Test
  @DisplayName("注销中：409，traits 含 PENDING_DELETION")
  void should_render_pending_deletion_as_409() {
    when(commandBus.handle(any(ResendEmailVerificationCommand.class)))
        .thenThrow(new UserPendingDeletionException());

    JsonNode json = JSON.readTree(resend());

    assertThat(json.get("status").asInt()).isEqualTo(409);
    assertThat(json.get("traits"))
        .extracting(JsonNode::asString)
        .containsExactlyInAnyOrder("CONFLICT", "PENDING_DELETION");
  }

  @Test
  @DisplayName("没有当前用户：401")
  void should_render_unauthorized_as_401() {
    when(commandBus.handle(any(ResendEmailVerificationCommand.class)))
        .thenThrow(new AuthenticationRequiredException());

    JsonNode json = JSON.readTree(resend());

    assertThat(json.get("status").asInt()).isEqualTo(401);
    assertThat(json.get("code").asString()).isEqualTo("IDN-0401");
  }

  /// 发改邮箱申请。
  ///
  /// @param newEmail 新邮箱
  /// @return 响应规格
  private RestTestClient.ResponseSpec requestChange(String newEmail) {
    return restClient
        .post()
        .uri("/me/email/change")
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("newEmail", newEmail))
        .exchange();
  }

  @Test
  @DisplayName("申请改邮箱：202，带新邮箱与到期时间；新邮箱与客户端 IP 交给命令")
  void should_accept_email_change_request_with_202() {
    when(commandBus.handle(any(RequestEmailChangeCommand.class)))
        .thenReturn(
            EmailChangeRequested.of("new@example.com", Instant.parse("2026-10-22T09:00:00Z")));

    requestChange("new@example.com")
        .expectStatus()
        .isAccepted()
        .expectBody()
        .jsonPath("$.newEmail")
        .isEqualTo("new@example.com")
        .jsonPath("$.expiresAt")
        .isEqualTo("2026-10-22T09:00:00Z");

    ArgumentCaptor<RequestEmailChangeCommand> command =
        ArgumentCaptor.forClass(RequestEmailChangeCommand.class);
    verify(commandBus).handle(command.capture());
    assertThat(command.getValue().newEmail()).isEqualTo("new@example.com");
    assertThat(command.getValue().clientIp()).isEqualTo("127.0.0.1");
  }

  @Test
  @DisplayName("超过再认证窗口：403，traits 含 FORBIDDEN 与 REAUTH_REQUIRED")
  void should_render_reauth_required_as_403_with_trait() {
    when(commandBus.handle(any(RequestEmailChangeCommand.class)))
        .thenThrow(new ReauthenticationRequiredException());

    String body =
        requestChange("new@example.com")
            .expectStatus()
            .isForbidden()
            .expectBody(String.class)
            .returnResult()
            .getResponseBody();

    JsonNode json = JSON.readTree(body);
    assertThat(json.get("code").asString()).isEqualTo("IDN-0403");
    assertThat(json.get("detail").asString()).isEqualTo("请重新输入密码以继续");
    assertThat(json.get("traits"))
        .extracting(JsonNode::asString)
        .containsExactlyInAnyOrder("FORBIDDEN", "REAUTH_REQUIRED");
  }

  @Test
  @DisplayName("已有待确认：409，traits 含 EMAIL_CHANGE_PENDING；新邮箱被占用：409 errors[0].field 是 email")
  void should_render_pending_and_taken_as_409() {
    when(commandBus.handle(any(RequestEmailChangeCommand.class)))
        .thenThrow(new EmailChangePendingException())
        .thenThrow(
            new UserIdentifierTakenException(
                List.of(UserFieldViolations.emailAlreadyRegistered())));

    JsonNode pending =
        JSON.readTree(
            requestChange("new@example.com")
                .expectStatus()
                .isEqualTo(409)
                .expectBody(String.class)
                .returnResult()
                .getResponseBody());
    assertThat(pending.get("traits"))
        .extracting(JsonNode::asString)
        .containsExactlyInAnyOrder("CONFLICT", "EMAIL_CHANGE_PENDING");

    JsonNode taken =
        JSON.readTree(
            requestChange("new@example.com")
                .expectStatus()
                .isEqualTo(409)
                .expectBody(String.class)
                .returnResult()
                .getResponseBody());
    assertThat(taken.get("errors").get(0).get("field").asString()).isEqualTo("email");
    assertThat(taken.get("errors").get(0).get("code").asString()).isEqualTo("ALREADY_REGISTERED");
  }

  @Test
  @DisplayName("今天的配额用完：429 带 Retry-After")
  void should_render_email_change_limit_as_429() {
    when(commandBus.handle(any(RequestEmailChangeCommand.class)))
        .thenThrow(new EmailChangeLimitedException(Duration.ofHours(6)));

    requestChange("new@example.com")
        .expectStatus()
        .isEqualTo(429)
        .expectHeader()
        .valueEquals(HttpHeaders.RETRY_AFTER, "21600")
        .expectBody()
        .jsonPath("$.detail")
        .isEqualTo("今天更改邮箱的次数已用完，请明天再试");
  }

  @Test
  @DisplayName("查看待确认：有 200 带新邮箱与到期时间，没有 204")
  void should_render_pending_change_as_200_or_204() {
    when(userQueryService.pendingEmailChange())
        .thenReturn(
            Optional.of(
                PendingEmailChange.of("new@example.com", Instant.parse("2026-10-22T09:00:00Z"))))
        .thenReturn(Optional.empty());

    restClient
        .get()
        .uri("/me/email/change")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.newEmail")
        .isEqualTo("new@example.com")
        .jsonPath("$.expiresAt")
        .isEqualTo("2026-10-22T09:00:00Z");
    restClient.get().uri("/me/email/change").exchange().expectStatus().isNoContent();
  }

  @Test
  @DisplayName("取消待确认：204")
  void should_cancel_pending_change_with_204() {
    restClient.delete().uri("/me/email/change").exchange().expectStatus().isNoContent();

    verify(commandBus).handle(any(CancelEmailChangeCommand.class));
  }
}
