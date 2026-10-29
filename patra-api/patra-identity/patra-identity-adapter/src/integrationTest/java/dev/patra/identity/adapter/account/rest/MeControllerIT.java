package dev.patra.identity.adapter.account.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.patra.identity.app.account.usecase.login.AuthenticatedUser;
import dev.patra.identity.app.account.usecase.password.ChangePasswordCommand;
import dev.patra.identity.app.account.usecase.username.ChangeUsernameCommand;
import dev.patra.identity.app.account.usecase.username.ChangeUsernameResult;
import dev.patra.identity.domain.account.exception.CurrentPasswordMismatchException;
import dev.patra.identity.domain.account.exception.LoginTemporarilyLockedException;
import dev.patra.identity.domain.account.exception.UserIdentifierTakenException;
import dev.patra.identity.domain.account.exception.UserPendingDeletionException;
import dev.patra.identity.domain.account.exception.UsernameChangeCooldownException;
import dev.patra.identity.domain.account.model.enums.UserStatus;
import dev.patra.identity.domain.account.model.read.UserAccountReadModel;
import dev.patra.identity.domain.session.exception.InvalidUserFieldsException;
import dev.patra.identity.domain.session.model.vo.UserFieldViolations;
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
  @DisplayName("冷却期内：409，traits 含 USERNAME_CHANGE_COOLDOWN，不带 Retry-After")
  void should_render_cooldown_as_409_with_trait() {
    when(commandBus.handle(any(ChangeUsernameCommand.class)))
        .thenThrow(new UsernameChangeCooldownException());

    restClient
        .patch()
        .uri("/me/username")
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("username", "chen-yu-2"))
        .exchange()
        .expectStatus()
        .isEqualTo(409)
        .expectHeader()
        .doesNotExist(HttpHeaders.RETRY_AFTER)
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("IDN-0409")
        .jsonPath("$.traits")
        .value(
            traits ->
                assertThat(traits.toString())
                    .contains("CONFLICT")
                    .contains("USERNAME_CHANGE_COOLDOWN"))
        .jsonPath("$.retryAfterSeconds")
        .doesNotExist();
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

  /// 发改密码请求。
  ///
  /// @param current 当前密码
  /// @param next 新密码
  /// @return 响应规格
  private RestTestClient.ResponseSpec changePassword(String current, String next) {
    return restClient
        .put()
        .uri("/me/password")
        .contentType(MediaType.APPLICATION_JSON)
        .header(HttpHeaders.USER_AGENT, "Mozilla/5.0 (Macintosh) Chrome/141.0.0.0")
        .body(Map.of("currentPassword", current, "newPassword", next))
        .exchange();
  }

  @Test
  @DisplayName("改密码成功：200，和登录同构、带新令牌；两个密码与客户端 IP 交给命令")
  void should_change_password_and_return_200_with_new_token() {
    when(commandBus.handle(any(ChangePasswordCommand.class)))
        .thenReturn(
            AuthenticatedUser.of(
                "patra_user_rotated",
                UserAccountReadModel.builder()
                    .userId(42L)
                    .email("chen.yu@example.com")
                    .emailVerified(true)
                    .username("chen-yu")
                    .displayName("陈宇")
                    .status(UserStatus.ACTIVE)
                    .build()));

    changePassword("Correct-Horse-02", "Another-Horse-03")
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.sessionToken")
        .isEqualTo("patra_user_rotated")
        .jsonPath("$.user.userId")
        .isEqualTo("42");

    ArgumentCaptor<ChangePasswordCommand> command =
        ArgumentCaptor.forClass(ChangePasswordCommand.class);
    verify(commandBus).handle(command.capture());
    assertThat(command.getValue().currentPassword()).isEqualTo("Correct-Horse-02");
    assertThat(command.getValue().newPassword()).isEqualTo("Another-Horse-03");
    assertThat(command.getValue().clientIp()).isEqualTo("127.0.0.1");
    assertThat(command.getValue().userAgent())
        .isEqualTo("Mozilla/5.0 (Macintosh) Chrome/141.0.0.0");
  }

  @Test
  @DisplayName("改密码字段错误：422，两条 errors 按请求体字段名")
  void should_render_change_password_field_errors() {
    when(commandBus.handle(any(ChangePasswordCommand.class)))
        .thenThrow(
            new InvalidUserFieldsException(
                List.of(
                    UserFieldViolations.passwordRequired(UserFieldViolations.CURRENT_PASSWORD),
                    UserFieldViolations.newPasswordUnchanged())));

    String body =
        changePassword("", "x")
            .expectStatus()
            .isEqualTo(422)
            .expectBody(String.class)
            .returnResult()
            .getResponseBody();

    JsonNode errors = JSON.readTree(body).get("errors");
    assertThat(errors.size()).isEqualTo(2);
    assertThat(errors.get(0).get("field").asString()).isEqualTo("currentPassword");
    assertThat(errors.get(0).get("code").asString()).isEqualTo("REQUIRED");
    assertThat(errors.get(1).get("field").asString()).isEqualTo("newPassword");
    assertThat(errors.get(1).get("code").asString()).isEqualTo("UNCHANGED");
  }

  @Test
  @DisplayName("当前密码错误：401，traits 含 PASSWORD_MISMATCH")
  void should_render_change_password_mismatch_as_401_with_trait() {
    when(commandBus.handle(any(ChangePasswordCommand.class)))
        .thenThrow(new CurrentPasswordMismatchException());

    String body =
        changePassword("wrong", "Another-Horse-03")
            .expectStatus()
            .isUnauthorized()
            .expectBody(String.class)
            .returnResult()
            .getResponseBody();

    assertThat(JSON.readTree(body).get("traits").toString()).contains("PASSWORD_MISMATCH");
    assertThat(body).doesNotContain("Another-Horse-03");
  }

  @Test
  @DisplayName("改密码时登录限制锁定：429，带 Retry-After")
  void should_render_change_password_lock_as_429() {
    when(commandBus.handle(any(ChangePasswordCommand.class)))
        .thenThrow(new LoginTemporarilyLockedException(Duration.ofSeconds(90)));

    changePassword("wrong", "Another-Horse-03")
        .expectStatus()
        .isEqualTo(429)
        .expectHeader()
        .valueEquals(HttpHeaders.RETRY_AFTER, "90")
        .expectBody()
        .jsonPath("$.retryAfterSeconds")
        .isEqualTo(90);
  }
}
