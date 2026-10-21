package dev.patra.identity.adapter.rest.me;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.patra.identity.app.usecase.email.EmailVerificationResent;
import dev.patra.identity.app.usecase.email.ResendEmailVerificationCommand;
import dev.patra.identity.domain.exception.EmailAlreadyVerifiedException;
import dev.patra.identity.domain.exception.EmailResendLimitedException;
import dev.patra.identity.domain.exception.UserPendingDeletionException;
import dev.patra.tool.core.security.AuthenticationRequiredException;
import dev.patra.tool.cqrs.CommandBus;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.client.RestTestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/// MeEmailController 切片测试：重发验证邮件的 202 / 429 / 409 / 401。
@WebMvcTest
@Import(MeEmailController.class)
@AutoConfigureRestTestClient
@DisplayName("MeEmailController 切片测试")
class MeEmailControllerIT {

  private static final JsonMapper JSON = JsonMapper.builder().build();

  @Autowired private RestTestClient restClient;

  @MockitoBean private CommandBus commandBus;

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
}
