package dev.patra.identity.adapter.account.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.patra.identity.app.account.usecase.email.EmailVerified;
import dev.patra.identity.app.account.usecase.email.VerifyEmailCommand;
import dev.patra.identity.domain.session.exception.InvalidUserFieldsException;
import dev.patra.identity.domain.session.model.vo.UserFieldViolations;
import dev.patra.tool.cqrs.CommandBus;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.client.RestTestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/// EmailVerificationController 切片测试：验证邮箱的 200 / 422 / 400。
@WebMvcTest
@Import(EmailVerificationController.class)
@AutoConfigureRestTestClient
@DisplayName("EmailVerificationController 切片测试")
class EmailVerificationControllerIT {

  private static final JsonMapper JSON = JsonMapper.builder().build();
  private static final String TOKEN = "G".repeat(43);

  @Autowired private RestTestClient restClient;

  @MockitoBean private CommandBus commandBus;

  /// 发验证请求。
  ///
  /// @param body 请求体
  /// @return 响应体
  private String post(Map<String, Object> body) {
    return restClient
        .post()
        .uri("/auth/email/verify")
        .contentType(MediaType.APPLICATION_JSON)
        .body(body)
        .exchange()
        .expectBody(String.class)
        .returnResult()
        .getResponseBody();
  }

  @Test
  @DisplayName("验证成功：200，带邮箱和验证时间；令牌原文原样交给命令")
  void should_verify_with_200() {
    when(commandBus.handle(any(VerifyEmailCommand.class)))
        .thenReturn(EmailVerified.of("chen.yu@example.com", Instant.parse("2026-10-21T08:03:12Z")));

    restClient
        .post()
        .uri("/auth/email/verify")
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("token", TOKEN))
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.email")
        .isEqualTo("chen.yu@example.com")
        .jsonPath("$.emailVerifiedAt")
        .isEqualTo("2026-10-21T08:03:12Z");

    ArgumentCaptor<VerifyEmailCommand> command = ArgumentCaptor.forClass(VerifyEmailCommand.class);
    verify(commandBus).handle(command.capture());
    assertThat(command.getValue().token()).isEqualTo(TOKEN);
    assertThat(command.getValue().toString()).doesNotContain(TOKEN);
  }

  @Test
  @DisplayName("令牌不可用：422，errors 带 token / INVALID")
  void should_render_invalid_token_as_422() {
    when(commandBus.handle(any(VerifyEmailCommand.class)))
        .thenThrow(new InvalidUserFieldsException(List.of(UserFieldViolations.tokenInvalid())));

    JsonNode json = JSON.readTree(post(Map.of("token", "bad")));

    assertThat(json.get("status").asInt()).isEqualTo(422);
    assertThat(json.get("code").asString()).isEqualTo("IDN-0422");
    assertThat(json.get("errors").get(0).get("field").asString()).isEqualTo("token");
    assertThat(json.get("errors").get(0).get("code").asString()).isEqualTo("INVALID");
    assertThat(json.get("errors").get(0).get("message").asString()).isEqualTo("验证链接无效或已过期");
  }

  @Test
  @DisplayName("令牌缺失：422，errors 带 token / REQUIRED")
  void should_render_missing_token_as_422_required() {
    when(commandBus.handle(any(VerifyEmailCommand.class)))
        .thenThrow(new InvalidUserFieldsException(List.of(UserFieldViolations.tokenRequired())));

    JsonNode json = JSON.readTree(post(Map.of()));

    assertThat(json.get("status").asInt()).isEqualTo(422);
    assertThat(json.get("errors").get(0).get("code").asString()).isEqualTo("REQUIRED");
  }

  @Test
  @DisplayName("请求体不是 JSON 对象：400，由 Spring 直接处理")
  void should_reject_non_json_body_with_400() {
    restClient
        .post()
        .uri("/auth/email/verify")
        .contentType(MediaType.APPLICATION_JSON)
        .body("\"x\"")
        .exchange()
        .expectStatus()
        .isBadRequest();
  }
}
