package dev.patra.identity.adapter.account.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.patra.identity.app.account.usecase.password.PasswordReset;
import dev.patra.identity.app.account.usecase.password.PasswordResetRequested;
import dev.patra.identity.app.account.usecase.password.RequestPasswordResetCommand;
import dev.patra.identity.app.account.usecase.password.ResetPasswordCommand;
import dev.patra.identity.domain.account.exception.PasswordResetLimitedException;
import dev.patra.identity.domain.session.exception.InvalidUserFieldsException;
import dev.patra.identity.domain.session.model.vo.UserFieldViolations;
import dev.patra.tool.cqrs.CommandBus;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
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

/// PasswordResetController 切片测试：申请 202 / 422 / 429，重设 200 / 422 / 429。
@WebMvcTest
@Import(PasswordResetController.class)
@AutoConfigureRestTestClient
@DisplayName("PasswordResetController 切片测试")
class PasswordResetControllerIT {

  private static final JsonMapper JSON = JsonMapper.builder().build();
  private static final String TOKEN = "T".repeat(43);

  @Autowired private RestTestClient restClient;

  @MockitoBean private CommandBus commandBus;

  @Test
  @DisplayName("申请：202，带邮箱与下次可申请时间；邮箱与客户端 IP 交给命令")
  void should_accept_request_with_202() {
    when(commandBus.handle(any(RequestPasswordResetCommand.class)))
        .thenReturn(
            PasswordResetRequested.of(
                "chen.yu@example.com", Instant.parse("2026-10-22T08:01:00Z")));

    restClient
        .post()
        .uri("/auth/password/forgot")
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("email", "chen.yu@example.com"))
        .exchange()
        .expectStatus()
        .isAccepted()
        .expectBody()
        .jsonPath("$.email")
        .isEqualTo("chen.yu@example.com")
        .jsonPath("$.nextRequestAt")
        .isEqualTo("2026-10-22T08:01:00Z");

    ArgumentCaptor<RequestPasswordResetCommand> command =
        ArgumentCaptor.forClass(RequestPasswordResetCommand.class);
    verify(commandBus).handle(command.capture());
    assertThat(command.getValue().email()).isEqualTo("chen.yu@example.com");
    assertThat(command.getValue().clientIp()).isEqualTo("127.0.0.1");
  }

  @Test
  @DisplayName("申请：邮箱格式不对 422")
  void should_render_request_field_errors() {
    when(commandBus.handle(any(RequestPasswordResetCommand.class)))
        .thenThrow(
            new InvalidUserFieldsException(List.of(UserFieldViolations.emailInvalidFormat())));

    restClient
        .post()
        .uri("/auth/password/forgot")
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("email", "nope"))
        .exchange()
        .expectStatus()
        .isEqualTo(422)
        .expectBody()
        .jsonPath("$.errors[0].field")
        .isEqualTo("email");
  }

  @Test
  @DisplayName("申请被限流：429，Retry-After 向上取整到秒")
  void should_render_request_limit_as_429() {
    when(commandBus.handle(any(RequestPasswordResetCommand.class)))
        .thenThrow(new PasswordResetLimitedException(Duration.ofMillis(49_500)));

    restClient
        .post()
        .uri("/auth/password/forgot")
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("email", "chen.yu@example.com"))
        .exchange()
        .expectStatus()
        .isEqualTo(429)
        .expectHeader()
        .valueEquals(HttpHeaders.RETRY_AFTER, "50")
        .expectBody()
        .jsonPath("$.retryAfterSeconds")
        .isEqualTo(50)
        .jsonPath("$.detail")
        .isEqualTo("操作过于频繁，请稍后再试");
  }

  @Test
  @DisplayName("重设：200，带邮箱；令牌、新密码与客户端 IP 交给命令；响应里没有令牌")
  void should_reset_with_200() {
    when(commandBus.handle(any(ResetPasswordCommand.class)))
        .thenReturn(PasswordReset.of("chen.yu@example.com"));

    String body =
        restClient
            .post()
            .uri("/auth/password/reset")
            .contentType(MediaType.APPLICATION_JSON)
            .header(HttpHeaders.USER_AGENT, "Mozilla/5.0 (Macintosh) Chrome/141.0.0.0")
            .body(Map.of("token", TOKEN, "newPassword", "Another-Horse-03"))
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(String.class)
            .returnResult()
            .getResponseBody();

    assertThat(JSON.readTree(body).get("email").asString()).isEqualTo("chen.yu@example.com");
    assertThat(body).doesNotContain(TOKEN);
    ArgumentCaptor<ResetPasswordCommand> command =
        ArgumentCaptor.forClass(ResetPasswordCommand.class);
    verify(commandBus).handle(command.capture());
    assertThat(command.getValue().token()).isEqualTo(TOKEN);
    assertThat(command.getValue().newPassword()).isEqualTo("Another-Horse-03");
    assertThat(command.getValue().clientIp()).isEqualTo("127.0.0.1");
    assertThat(command.getValue().userAgent())
        .isEqualTo("Mozilla/5.0 (Macintosh) Chrome/141.0.0.0");
  }

  @Test
  @DisplayName("重设：令牌为空 422 token/REQUIRED，新密码不合规 422 newPassword")
  void should_render_reset_field_errors() {
    when(commandBus.handle(any(ResetPasswordCommand.class)))
        .thenThrow(
            new InvalidUserFieldsException(
                List.of(
                    UserFieldViolations.tokenRequired(),
                    UserFieldViolations.passwordTooShort(UserFieldViolations.NEW_PASSWORD))));

    String body =
        restClient
            .post()
            .uri("/auth/password/reset")
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("token", "", "newPassword", "short"))
            .exchange()
            .expectStatus()
            .isEqualTo(422)
            .expectBody(String.class)
            .returnResult()
            .getResponseBody();

    JsonNode errors = JSON.readTree(body).get("errors");
    assertThat(errors.get(0).get("field").asString()).isEqualTo("token");
    assertThat(errors.get(0).get("code").asString()).isEqualTo("REQUIRED");
    assertThat(errors.get(1).get("field").asString()).isEqualTo("newPassword");
    assertThat(errors.get(1).get("code").asString()).isEqualTo("TOO_SHORT");
  }

  @Test
  @DisplayName("重设被限流：429 带 Retry-After")
  void should_render_reset_limit_as_429() {
    when(commandBus.handle(any(ResetPasswordCommand.class)))
        .thenThrow(new PasswordResetLimitedException(Duration.ofSeconds(30)));

    restClient
        .post()
        .uri("/auth/password/reset")
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("token", TOKEN, "newPassword", "Another-Horse-03"))
        .exchange()
        .expectStatus()
        .isEqualTo(429)
        .expectHeader()
        .valueEquals(HttpHeaders.RETRY_AFTER, "30");
  }
}
