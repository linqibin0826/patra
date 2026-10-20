package dev.patra.identity.adapter.rest.me;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.patra.identity.app.usecase.username.ChangeUsernameCommand;
import dev.patra.identity.app.usecase.username.ChangeUsernameResult;
import dev.patra.identity.domain.exception.InvalidUserFieldsException;
import dev.patra.identity.domain.exception.UserIdentifierTakenException;
import dev.patra.identity.domain.exception.UserPendingDeletionException;
import dev.patra.identity.domain.exception.UsernameChangeCooldownException;
import dev.patra.identity.domain.model.vo.UserFieldViolations;
import dev.patra.tool.core.security.AuthenticationRequiredException;
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

/// MeController 切片测试：改用户名的 200 / 422 / 409 / 429 / 401。
@WebMvcTest
@Import(MeController.class)
@AutoConfigureRestTestClient
@DisplayName("MeController 切片测试")
class MeControllerIT {

  private static final JsonMapper JSON = JsonMapper.builder().build();

  @Autowired private RestTestClient restClient;

  @MockitoBean private CommandBus commandBus;

  /// 发改名请求。
  ///
  /// @param username 新用户名
  /// @return 响应体
  private String patchUsername(String username) {
    return restClient
        .patch()
        .uri("/me/username")
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("username", username))
        .exchange()
        .expectBody(String.class)
        .returnResult()
        .getResponseBody();
  }

  @Test
  @DisplayName("改名成功：200，带新名和下次可改时间；原始输入原样交给命令")
  void should_change_username_and_return_200() {
    when(commandBus.handle(any(ChangeUsernameCommand.class)))
        .thenReturn(ChangeUsernameResult.of("chen-yu-2", Instant.parse("2026-11-19T08:00:00Z")));

    restClient
        .patch()
        .uri("/me/username")
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("username", " Chen-Yu-2 "))
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.username")
        .isEqualTo("chen-yu-2")
        .jsonPath("$.nextUsernameChangeAt")
        .isEqualTo("2026-11-19T08:00:00Z");

    ArgumentCaptor<ChangeUsernameCommand> command =
        ArgumentCaptor.forClass(ChangeUsernameCommand.class);
    verify(commandBus).handle(command.capture());
    assertThat(command.getValue().username()).isEqualTo(" Chen-Yu-2 ");
  }

  @Test
  @DisplayName("新名与当前相同：422，原因码 UNCHANGED")
  void should_render_unchanged_as_422() {
    when(commandBus.handle(any(ChangeUsernameCommand.class)))
        .thenThrow(
            new InvalidUserFieldsException(List.of(UserFieldViolations.usernameUnchanged())));

    JsonNode json = JSON.readTree(patchUsername("chen-yu"));

    assertThat(json.get("status").asInt()).isEqualTo(422);
    assertThat(json.get("code").asString()).isEqualTo("IDN-0422");
    assertThat(json.get("errors").get(0).get("code").asString()).isEqualTo("UNCHANGED");
  }

  @Test
  @DisplayName("被占用：409，errors 带 username / ALREADY_TAKEN")
  void should_render_taken_as_409_with_field_error() {
    when(commandBus.handle(any(ChangeUsernameCommand.class)))
        .thenThrow(
            new UserIdentifierTakenException(List.of(UserFieldViolations.usernameAlreadyTaken())));

    JsonNode json = JSON.readTree(patchUsername("chen-yu-2"));

    assertThat(json.get("status").asInt()).isEqualTo(409);
    assertThat(json.get("errors").get(0).get("field").asString()).isEqualTo("username");
    assertThat(json.get("errors").get(0).get("code").asString()).isEqualTo("ALREADY_TAKEN");
  }

  @Test
  @DisplayName("注销中：409，traits 同时含 CONFLICT 和 PENDING_DELETION")
  void should_render_pending_deletion_as_409_with_trait() {
    when(commandBus.handle(any(ChangeUsernameCommand.class)))
        .thenThrow(new UserPendingDeletionException());

    JsonNode json = JSON.readTree(patchUsername("chen-yu-2"));

    assertThat(json.get("status").asInt()).isEqualTo(409);
    assertThat(json.get("code").asString()).isEqualTo("IDN-0409");
    assertThat(json.get("traits"))
        .extracting(JsonNode::asString)
        .containsExactlyInAnyOrder("CONFLICT", "PENDING_DELETION");
    assertThat(json.get("detail").asString()).isEqualTo("账号正在注销中，取消注销后再操作");
  }

  @Test
  @DisplayName("冷却期内：429，Retry-After 和 retryAfterSeconds 都是剩余秒数")
  void should_render_cooldown_as_429() {
    when(commandBus.handle(any(ChangeUsernameCommand.class)))
        .thenThrow(new UsernameChangeCooldownException(Duration.ofDays(20)));

    restClient
        .patch()
        .uri("/me/username")
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("username", "chen-yu-2"))
        .exchange()
        .expectStatus()
        .isEqualTo(429)
        .expectHeader()
        .valueEquals(HttpHeaders.RETRY_AFTER, "1728000")
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("IDN-0429")
        .jsonPath("$.retryAfterSeconds")
        .isEqualTo(1728000);
  }

  @Test
  @DisplayName("没有当前用户：401")
  void should_render_unauthorized() {
    when(commandBus.handle(any(ChangeUsernameCommand.class)))
        .thenThrow(new AuthenticationRequiredException());

    JsonNode json = JSON.readTree(patchUsername("chen-yu-2"));

    assertThat(json.get("status").asInt()).isEqualTo(401);
    assertThat(json.get("code").asString()).isEqualTo("IDN-0401");
  }
}
