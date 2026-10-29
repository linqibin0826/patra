package dev.patra.identity.adapter.account.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.patra.identity.app.account.usecase.deletion.AccountDeletionRequested;
import dev.patra.identity.app.account.usecase.deletion.CancelAccountDeletionCommand;
import dev.patra.identity.app.account.usecase.deletion.RequestAccountDeletionCommand;
import dev.patra.identity.domain.account.exception.UserPendingDeletionException;
import dev.patra.identity.domain.account.model.enums.UserStatus;
import dev.patra.identity.domain.account.model.read.UserAccountReadModel;
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

/// MeDeletionController 切片测试：发起注销 200 带到期时间、取消注销 200 带账号信息、422 与 409 的形状。
@WebMvcTest
@Import(MeDeletionController.class)
@AutoConfigureRestTestClient
@DisplayName("MeDeletionController 切片测试")
class MeDeletionControllerIT {

  private static final JsonMapper JSON = JsonMapper.builder().build();
  private static final String USER_AGENT = "Mozilla/5.0 (Macintosh) Chrome/141.0.0.0";

  @Autowired private RestTestClient restClient;

  @MockitoBean private CommandBus commandBus;

  /// 发起注销。
  ///
  /// @param username 输入的用户名
  /// @return 响应体
  private String request(String username) {
    return restClient
        .post()
        .uri("/me/deletion")
        .contentType(MediaType.APPLICATION_JSON)
        .header(HttpHeaders.USER_AGENT, USER_AGENT)
        .body(Map.of("username", username))
        .exchange()
        .expectBody(String.class)
        .returnResult()
        .getResponseBody();
  }

  @Test
  @DisplayName("发起成功：200，带到期时间；用户名、客户端 IP、User-Agent 交给命令")
  void should_request_deletion_with_200_and_due_time() {
    when(commandBus.handle(any(RequestAccountDeletionCommand.class)))
        .thenReturn(AccountDeletionRequested.of(Instant.parse("2026-11-04T08:00:00Z")));

    restClient
        .post()
        .uri("/me/deletion")
        .contentType(MediaType.APPLICATION_JSON)
        .header(HttpHeaders.USER_AGENT, USER_AGENT)
        .body(Map.of("username", "chen-yu"))
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.deletionDueAt")
        .isEqualTo("2026-11-04T08:00:00Z");

    ArgumentCaptor<RequestAccountDeletionCommand> command =
        ArgumentCaptor.forClass(RequestAccountDeletionCommand.class);
    verify(commandBus).handle(command.capture());
    assertThat(command.getValue().username()).isEqualTo("chen-yu");
    assertThat(command.getValue().clientIp()).isEqualTo("127.0.0.1");
    assertThat(command.getValue().userAgent()).isEqualTo(USER_AGENT);
  }

  @Test
  @DisplayName("取消成功：200，体与 /auth/me 同构，状态回到 ACTIVE")
  void should_cancel_deletion_with_200_and_user() {
    when(commandBus.handle(any(CancelAccountDeletionCommand.class)))
        .thenReturn(
            UserAccountReadModel.builder()
                .userId(42L)
                .email("chen.yu@example.com")
                .emailVerified(true)
                .username("chen-yu")
                .displayName("陈宇")
                .status(UserStatus.ACTIVE)
                .build());

    String body =
        restClient
            .delete()
            .uri("/me/deletion")
            .header(HttpHeaders.USER_AGENT, USER_AGENT)
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(String.class)
            .returnResult()
            .getResponseBody();

    JsonNode json = JSON.readTree(body);
    assertThat(json.get("status").asString()).isEqualTo("ACTIVE");
    assertThat(json.get("username").asString()).isEqualTo("chen-yu");
    assertThat(json.path("deletionDueAt").isNull()).isTrue();
    ArgumentCaptor<CancelAccountDeletionCommand> command =
        ArgumentCaptor.forClass(CancelAccountDeletionCommand.class);
    verify(commandBus).handle(command.capture());
    assertThat(command.getValue().userAgent()).isEqualTo(USER_AGENT);
  }

  @Test
  @DisplayName("用户名不一致：422，errors[0] 是 username/MISMATCH")
  void should_map_mismatch_to_422() {
    when(commandBus.handle(any(RequestAccountDeletionCommand.class)))
        .thenThrow(new InvalidUserFieldsException(List.of(UserFieldViolations.usernameMismatch())));

    JsonNode json = JSON.readTree(request("nope"));

    assertThat(json.get("status").asInt()).isEqualTo(422);
    assertThat(json.get("errors").get(0).get("field").asString()).isEqualTo("username");
    assertThat(json.get("errors").get(0).get("code").asString()).isEqualTo("MISMATCH");
  }

  @Test
  @DisplayName("已在冷静期：409，traits 含 PENDING_DELETION")
  void should_map_pending_deletion_to_409() {
    when(commandBus.handle(any(RequestAccountDeletionCommand.class)))
        .thenThrow(new UserPendingDeletionException());

    JsonNode json = JSON.readTree(request("chen-yu"));

    assertThat(json.get("status").asInt()).isEqualTo(409);
    assertThat(json.get("traits"))
        .extracting(JsonNode::asString)
        .containsExactlyInAnyOrder("CONFLICT", "PENDING_DELETION");
  }
}
