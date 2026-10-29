package dev.patra.identity.adapter.account.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.patra.identity.app.account.usecase.emailchange.ConfirmEmailChangeCommand;
import dev.patra.identity.app.account.usecase.emailchange.EmailChangeReverted;
import dev.patra.identity.app.account.usecase.emailchange.EmailChanged;
import dev.patra.identity.app.account.usecase.emailchange.RevertEmailChangeCommand;
import dev.patra.identity.domain.account.exception.UserIdentifierTakenException;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.client.RestTestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/// EmailChangeController 切片测试：确认与撤销的 200 / 422 / 409。
@WebMvcTest
@Import(EmailChangeController.class)
@AutoConfigureRestTestClient
@DisplayName("EmailChangeController 切片测试")
class EmailChangeControllerIT {

  private static final JsonMapper JSON = JsonMapper.builder().build();
  private static final String TOKEN = "T".repeat(43);

  @Autowired private RestTestClient restClient;

  @MockitoBean private CommandBus commandBus;

  /// 发一个带令牌的 POST。
  ///
  /// @param path 路径
  /// @return 响应规格
  private RestTestClient.ResponseSpec post(String path) {
    return restClient
        .post()
        .uri(path)
        .contentType(MediaType.APPLICATION_JSON)
        .header(HttpHeaders.USER_AGENT, "Mozilla/5.0 (Macintosh) Chrome/141.0.0.0")
        .body(Map.of("token", TOKEN))
        .exchange();
  }

  @Test
  @DisplayName("确认：200，带新邮箱与验证时间；令牌与客户端 IP 交给命令；响应里没有令牌")
  void should_confirm_with_200() {
    when(commandBus.handle(any(ConfirmEmailChangeCommand.class)))
        .thenReturn(EmailChanged.of("new@example.com", Instant.parse("2026-10-22T08:00:00Z")));

    String body =
        post("/auth/email/change/confirm")
            .expectStatus()
            .isOk()
            .expectBody(String.class)
            .returnResult()
            .getResponseBody();

    JsonNode json = JSON.readTree(body);
    assertThat(json.get("email").asString()).isEqualTo("new@example.com");
    assertThat(json.get("emailVerifiedAt").asString()).isEqualTo("2026-10-22T08:00:00Z");
    assertThat(body).doesNotContain(TOKEN);
    ArgumentCaptor<ConfirmEmailChangeCommand> command =
        ArgumentCaptor.forClass(ConfirmEmailChangeCommand.class);
    verify(commandBus).handle(command.capture());
    assertThat(command.getValue().token()).isEqualTo(TOKEN);
    assertThat(command.getValue().clientIp()).isEqualTo("127.0.0.1");
    assertThat(command.getValue().userAgent())
        .isEqualTo("Mozilla/5.0 (Macintosh) Chrome/141.0.0.0");
  }

  @Test
  @DisplayName("确认：令牌不可用 422 token/INVALID；地址已被注册 409 errors[0].field 是 email")
  void should_render_confirm_errors() {
    when(commandBus.handle(any(ConfirmEmailChangeCommand.class)))
        .thenThrow(new InvalidUserFieldsException(List.of(UserFieldViolations.tokenInvalid())))
        .thenThrow(
            new UserIdentifierTakenException(
                List.of(UserFieldViolations.emailAlreadyRegistered())));

    post("/auth/email/change/confirm")
        .expectStatus()
        .isEqualTo(422)
        .expectBody()
        .jsonPath("$.errors[0].field")
        .isEqualTo("token")
        .jsonPath("$.errors[0].code")
        .isEqualTo("INVALID");
    post("/auth/email/change/confirm")
        .expectStatus()
        .isEqualTo(409)
        .expectBody()
        .jsonPath("$.errors[0].field")
        .isEqualTo("email")
        .jsonPath("$.errors[0].code")
        .isEqualTo("ALREADY_REGISTERED");
  }

  @Test
  @DisplayName("撤销：200，带改回的邮箱；令牌与客户端 IP 交给命令")
  void should_revert_with_200() {
    when(commandBus.handle(any(RevertEmailChangeCommand.class)))
        .thenReturn(EmailChangeReverted.of("chen.yu@example.com"));

    post("/auth/email/change/revert")
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.email")
        .isEqualTo("chen.yu@example.com");

    ArgumentCaptor<RevertEmailChangeCommand> command =
        ArgumentCaptor.forClass(RevertEmailChangeCommand.class);
    verify(commandBus).handle(command.capture());
    assertThat(command.getValue().token()).isEqualTo(TOKEN);
    assertThat(command.getValue().clientIp()).isEqualTo("127.0.0.1");
    assertThat(command.getValue().userAgent())
        .isEqualTo("Mozilla/5.0 (Macintosh) Chrome/141.0.0.0");
  }

  @Test
  @DisplayName("撤销：令牌不可用 422；旧地址已被注册 409")
  void should_render_revert_errors() {
    when(commandBus.handle(any(RevertEmailChangeCommand.class)))
        .thenThrow(new InvalidUserFieldsException(List.of(UserFieldViolations.tokenInvalid())))
        .thenThrow(
            new UserIdentifierTakenException(
                List.of(UserFieldViolations.emailAlreadyRegistered())));

    post("/auth/email/change/revert").expectStatus().isEqualTo(422);
    post("/auth/email/change/revert").expectStatus().isEqualTo(409);
  }
}
