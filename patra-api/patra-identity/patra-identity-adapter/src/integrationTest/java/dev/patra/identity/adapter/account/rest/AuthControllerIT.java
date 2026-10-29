package dev.patra.identity.adapter.account.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.patra.identity.app.account.usecase.login.AuthenticatedUser;
import dev.patra.identity.app.account.usecase.login.LoginUserCommand;
import dev.patra.identity.app.account.usecase.query.UserQueryService;
import dev.patra.identity.app.account.usecase.reauth.ReauthenticateUserCommand;
import dev.patra.identity.app.account.usecase.reauth.Reauthenticated;
import dev.patra.identity.app.account.usecase.register.RegisterUserCommand;
import dev.patra.identity.domain.account.exception.CurrentPasswordMismatchException;
import dev.patra.identity.domain.account.exception.InvalidCredentialsException;
import dev.patra.identity.domain.account.exception.LoginTemporarilyLockedException;
import dev.patra.identity.domain.account.exception.UserBannedException;
import dev.patra.identity.domain.account.exception.UserIdentifierTakenException;
import dev.patra.identity.domain.account.model.enums.UserStatus;
import dev.patra.identity.domain.account.model.read.UserAccountReadModel;
import dev.patra.identity.domain.account.model.vo.avatar.Avatar;
import dev.patra.identity.domain.account.policy.AvatarPalette;
import dev.patra.identity.domain.session.exception.InvalidUserFieldsException;
import dev.patra.identity.domain.session.exception.TemporarilyUnavailableException;
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

/// AuthController 切片测试：请求怎么转成命令，领域异常怎么变成统一错误格式。
@WebMvcTest
@Import(AuthController.class)
@AutoConfigureRestTestClient
@DisplayName("AuthController 切片测试")
class AuthControllerIT {

  private static final JsonMapper JSON = JsonMapper.builder().build();

  @Autowired private RestTestClient restClient;

  @MockitoBean private CommandBus commandBus;

  @MockitoBean private UserQueryService userQueryService;

  /// 一个正常、未验证邮箱、没改过名的账号读模型。
  ///
  /// @param userId 用户 ID
  /// @return 读模型
  private static UserAccountReadModel account(long userId) {
    return UserAccountReadModel.builder()
        .userId(userId)
        .email("chen.yu@example.com")
        .emailVerified(false)
        .username("chen-yu")
        .displayName("chen-yu")
        .status(UserStatus.ACTIVE)
        .build();
  }

  @Test
  @DisplayName("注册成功返回 201，带会话令牌和嵌套的 user，userId 是字符串；原始输入原样交给命令")
  void should_register_and_return_201() {
    when(commandBus.handle(any(RegisterUserCommand.class)))
        .thenReturn(AuthenticatedUser.of("patra_user_x", account(352303128713027974L)));

    restClient
        .post()
        .uri("/auth/register")
        .contentType(MediaType.APPLICATION_JSON)
        .header(HttpHeaders.USER_AGENT, "Mozilla/5.0 (Macintosh) Chrome/141.0.0.0")
        .body(
            Map.of(
                "email", " Chen.Yu@Example.com ",
                "username", " Chen-Yu ",
                "password", "correct horse battery"))
        .exchange()
        .expectStatus()
        .isCreated()
        .expectBody()
        .jsonPath("$.sessionToken")
        .isEqualTo("patra_user_x")
        .jsonPath("$.user.userId")
        .value(
            userId -> assertThat(userId).isInstanceOf(String.class).isEqualTo("352303128713027974"))
        .jsonPath("$.user.email")
        .isEqualTo("chen.yu@example.com")
        .jsonPath("$.user.username")
        .isEqualTo("chen-yu")
        .jsonPath("$.user.displayName")
        .isEqualTo("chen-yu")
        .jsonPath("$.user.emailVerified")
        .isEqualTo(false)
        .jsonPath("$.user.status")
        .isEqualTo("ACTIVE")
        .jsonPath("$.user.accountType")
        .isEqualTo("user");

    ArgumentCaptor<RegisterUserCommand> command =
        ArgumentCaptor.forClass(RegisterUserCommand.class);
    verify(commandBus).handle(command.capture());
    assertThat(command.getValue().email()).isEqualTo(" Chen.Yu@Example.com ");
    assertThat(command.getValue().username()).isEqualTo(" Chen-Yu ");
    assertThat(command.getValue().password()).isEqualTo("correct horse battery");
    assertThat(command.getValue().clientType()).isNull();
    assertThat(command.getValue().deviceId()).isNull();
    // MockMvc 的远端地址固定是回环地址；真实请求里是 getRemoteAddr()（网关转发头已按 framework 策略解析）
    assertThat(command.getValue().clientIp()).isEqualTo("127.0.0.1");
    assertThat(command.getValue().userAgent())
        .isEqualTo("Mozilla/5.0 (Macintosh) Chrome/141.0.0.0");
  }

  @Test
  @DisplayName("登录成功返回 200，带会话令牌；clientType、deviceId、客户端 IP 与 UA 交给命令")
  void should_login_and_return_200() {
    when(commandBus.handle(any(LoginUserCommand.class)))
        .thenReturn(AuthenticatedUser.of("patra_user_x", account(42L)));

    restClient
        .post()
        .uri("/auth/login")
        .contentType(MediaType.APPLICATION_JSON)
        .header(HttpHeaders.USER_AGENT, "Mozilla/5.0 (Macintosh) Chrome/141.0.0.0")
        .body(
            Map.of(
                "email", "chen.yu@example.com",
                "password", "correct horse battery",
                "clientType", "web",
                "deviceId", "mac-safari"))
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.sessionToken")
        .isEqualTo("patra_user_x")
        .jsonPath("$.user.userId")
        .isEqualTo("42")
        .jsonPath("$.user.email")
        .isEqualTo("chen.yu@example.com")
        .jsonPath("$.user.username")
        .isEqualTo("chen-yu");

    ArgumentCaptor<LoginUserCommand> command = ArgumentCaptor.forClass(LoginUserCommand.class);
    verify(commandBus).handle(command.capture());
    assertThat(command.getValue().clientType()).isEqualTo("web");
    assertThat(command.getValue().deviceId()).isEqualTo("mac-safari");
    assertThat(command.getValue().clientIp()).isEqualTo("127.0.0.1");
    assertThat(command.getValue().userAgent())
        .isEqualTo("Mozilla/5.0 (Macintosh) Chrome/141.0.0.0");
  }

  @Test
  @DisplayName("空请求体 {} 交给命令时两个字段都是 null，返回 422")
  void should_pass_nulls_for_empty_body() {
    when(commandBus.handle(any(RegisterUserCommand.class)))
        .thenThrow(
            new InvalidUserFieldsException(
                List.of(
                    UserFieldViolations.emailRequired(), UserFieldViolations.passwordRequired())));

    restClient
        .post()
        .uri("/auth/register")
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of())
        .exchange()
        .expectStatus()
        .isEqualTo(422);

    ArgumentCaptor<RegisterUserCommand> command =
        ArgumentCaptor.forClass(RegisterUserCommand.class);
    verify(commandBus).handle(command.capture());
    assertThat(command.getValue().email()).isNull();
    assertThat(command.getValue().username()).isNull();
    assertThat(command.getValue().password()).isNull();
  }

  @Test
  @DisplayName("字段不合法：422，errors 带字段名和原因码，不回显原始值（实测点 5）")
  void should_render_field_violations() {
    when(commandBus.handle(any(RegisterUserCommand.class)))
        .thenThrow(
            new InvalidUserFieldsException(List.of(UserFieldViolations.passwordTooCommon())));

    String body =
        restClient
            .post()
            .uri("/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("email", "chen.yu@example.com", "password", "Password123"))
            .exchange()
            .expectStatus()
            .isEqualTo(422)
            .expectHeader()
            .contentType(MediaType.APPLICATION_PROBLEM_JSON)
            .expectBody(String.class)
            .returnResult()
            .getResponseBody();

    JsonNode json = JSON.readTree(body);
    assertThat(json.get("code").asString()).isEqualTo("IDN-0422");
    assertThat(json.get("detail").asString()).isEqualTo("请求参数不合法");
    JsonNode error = json.get("errors").get(0);
    assertThat(error.get("field").asString()).isEqualTo("password");
    assertThat(error.get("code").asString()).isEqualTo("TOO_COMMON");
    assertThat(error.path("rejectedValue").isNull() || error.path("rejectedValue").isMissingNode())
        .isTrue();
    assertThat(body).doesNotContain("Password123");
  }

  @Test
  @DisplayName("邮箱和用户名都被占用：409，errors 按字段给原因码，不回显原始值")
  void should_return_409_with_field_errors_when_identifiers_taken() {
    when(commandBus.handle(any(RegisterUserCommand.class)))
        .thenThrow(
            new UserIdentifierTakenException(
                List.of(
                    UserFieldViolations.emailAlreadyRegistered(),
                    UserFieldViolations.usernameAlreadyTaken())));

    String body =
        restClient
            .post()
            .uri("/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .body(
                Map.of(
                    "email", "chen.yu@example.com",
                    "username", "chen-yu",
                    "password", "correct horse battery"))
            .exchange()
            .expectStatus()
            .isEqualTo(409)
            .expectBody(String.class)
            .returnResult()
            .getResponseBody();

    JsonNode json = JSON.readTree(body);
    assertThat(json.get("code").asString()).isEqualTo("IDN-0409");
    assertThat(json.get("detail").asString()).isEqualTo("邮箱或用户名已被使用");
    assertThat(json.get("errors").size()).isEqualTo(2);
    assertThat(json.get("errors").get(0).get("field").asString()).isEqualTo("email");
    assertThat(json.get("errors").get(0).get("code").asString()).isEqualTo("ALREADY_REGISTERED");
    assertThat(json.get("errors").get(1).get("field").asString()).isEqualTo("username");
    assertThat(json.get("errors").get(1).get("code").asString()).isEqualTo("ALREADY_TAKEN");
    assertThat(json.get("errors").get(1).get("rejectedValue").isNull()).isTrue();
    assertThat(body).doesNotContain("chen.yu@example.com");
  }

  @Test
  @DisplayName("再认证成功返回 200，和登录同构；密码原样交给命令")
  void should_reauth_with_200_and_new_token() {
    when(commandBus.handle(any(ReauthenticateUserCommand.class)))
        .thenReturn(Reauthenticated.of("patra_user_rotated", account(42L)));

    restClient
        .post()
        .uri("/auth/reauth")
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("password", "Correct-Horse-02"))
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.sessionToken")
        .isEqualTo("patra_user_rotated")
        .jsonPath("$.user.userId")
        .isEqualTo("42");

    ArgumentCaptor<ReauthenticateUserCommand> command =
        ArgumentCaptor.forClass(ReauthenticateUserCommand.class);
    verify(commandBus).handle(command.capture());
    assertThat(command.getValue().password()).isEqualTo("Correct-Horse-02");
  }

  @Test
  @DisplayName("宽限期内再认证没换令牌：200，sessionToken 为 null，user 照常")
  void should_reauth_with_null_token_when_kept() {
    when(commandBus.handle(any(ReauthenticateUserCommand.class)))
        .thenReturn(Reauthenticated.of(null, account(42L)));

    restClient
        .post()
        .uri("/auth/reauth")
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("password", "Correct-Horse-02"))
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.sessionToken")
        .isEmpty()
        .jsonPath("$.user.userId")
        .isEqualTo("42");
  }

  @Test
  @DisplayName("再认证密码错误：401，traits 含 PASSWORD_MISMATCH，门户据此不当登出")
  void should_render_password_mismatch_as_401_with_trait() {
    when(commandBus.handle(any(ReauthenticateUserCommand.class)))
        .thenThrow(new CurrentPasswordMismatchException());

    String body =
        restClient
            .post()
            .uri("/auth/reauth")
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("password", "wrong"))
            .exchange()
            .expectStatus()
            .isUnauthorized()
            .expectBody(String.class)
            .returnResult()
            .getResponseBody();

    JsonNode json = JSON.readTree(body);
    assertThat(json.get("code").asString()).isEqualTo("IDN-0401");
    assertThat(json.get("detail").asString()).isEqualTo("密码不正确");
    assertThat(json.get("traits").toString())
        .contains("PASSWORD_MISMATCH")
        .contains("UNAUTHORIZED");
    assertThat(body).doesNotContain("wrong");
  }

  @Test
  @DisplayName("邮箱或密码错误：401")
  void should_render_unauthorized() {
    when(commandBus.handle(any(LoginUserCommand.class)))
        .thenThrow(new InvalidCredentialsException());

    restClient
        .post()
        .uri("/auth/login")
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("email", "chen.yu@example.com", "password", "wrong"))
        .exchange()
        .expectStatus()
        .isEqualTo(401)
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("IDN-0401")
        .jsonPath("$.detail")
        .isEqualTo("邮箱或密码错误");
  }

  @Test
  @DisplayName("被暂时限制：429，带 Retry-After 响应头和 retryAfterSeconds")
  void should_render_too_many_requests() {
    when(commandBus.handle(any(LoginUserCommand.class)))
        .thenThrow(new LoginTemporarilyLockedException(Duration.ofMinutes(15)));

    restClient
        .post()
        .uri("/auth/login")
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("email", "chen.yu@example.com", "password", "any"))
        .exchange()
        .expectStatus()
        .isEqualTo(429)
        .expectHeader()
        .valueEquals(HttpHeaders.RETRY_AFTER, "900")
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("IDN-0429")
        .jsonPath("$.retryAfterSeconds")
        .value(retryAfterSeconds -> assertThat(retryAfterSeconds).isEqualTo(900));
  }

  @Test
  @DisplayName("账号已被封禁：403")
  void should_render_forbidden() {
    when(commandBus.handle(any(LoginUserCommand.class))).thenThrow(new UserBannedException());

    restClient
        .post()
        .uri("/auth/login")
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("email", "chen.yu@example.com", "password", "right"))
        .exchange()
        .expectStatus()
        .isEqualTo(403)
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("IDN-0403")
        .jsonPath("$.detail")
        .isEqualTo("该账号已被封禁");
  }

  @Test
  @DisplayName("依赖暂时不可用：503")
  void should_render_service_unavailable() {
    when(commandBus.handle(any(LoginUserCommand.class)))
        .thenThrow(new TemporarilyUnavailableException());

    restClient
        .post()
        .uri("/auth/login")
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("email", "chen.yu@example.com", "password", "any"))
        .exchange()
        .expectStatus()
        .isEqualTo(503)
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("IDN-0503")
        .jsonPath("$.detail")
        .isEqualTo("服务暂时不可用");
  }

  @Test
  @DisplayName("当前用户有头像：avatarUrl 是带版本的 identity 相对路径")
  void should_return_avatar_url_of_current_user() {
    when(userQueryService.currentAccount())
        .thenReturn(
            UserAccountReadModel.builder()
                .userId(42L)
                .email("chen.yu@example.com")
                .username("chen-yu")
                .displayName("陈宇")
                .avatar(Avatar.of(42L, "0123456789abcdef"))
                .status(UserStatus.ACTIVE)
                .build());

    restClient
        .get()
        .uri("/auth/me")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.avatarUrl")
        .isEqualTo("/users/42/avatar?v=0123456789abcdef");
  }

  @Test
  @DisplayName("当前用户：200，带 ID、邮箱、验证状态、用户名、昵称、头像（没上传为 null）、默认头像色号、状态、两个时间、账号类型")
  void should_return_current_user() {
    when(userQueryService.currentAccount())
        .thenReturn(
            UserAccountReadModel.builder()
                .userId(42L)
                .email("chen.yu@example.com")
                .emailVerified(true)
                .username("chen-yu")
                .displayName("陈宇")
                .status(UserStatus.PENDING_DELETION)
                .deletionDueAt(Instant.parse("2026-11-04T08:00:00Z"))
                .nextUsernameChangeAt(Instant.parse("2026-11-19T08:00:00Z"))
                .build());

    String body =
        restClient
            .get()
            .uri("/auth/me")
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(String.class)
            .returnResult()
            .getResponseBody();

    JsonNode json = JSON.readTree(body);
    assertThat(json.get("userId").asString()).isEqualTo("42");
    assertThat(json.get("email").asString()).isEqualTo("chen.yu@example.com");
    assertThat(json.get("emailVerified").asBoolean()).isTrue();
    assertThat(json.get("username").asString()).isEqualTo("chen-yu");
    assertThat(json.get("displayName").asString()).isEqualTo("陈宇");
    assertThat(json.has("avatarUrl")).isTrue();
    assertThat(json.get("avatarUrl").isNull()).isTrue();
    assertThat(json.get("avatarPalette").asInt()).isEqualTo(AvatarPalette.of(42L));
    assertThat(json.get("status").asString()).isEqualTo("PENDING_DELETION");
    assertThat(json.get("deletionDueAt").asString()).isEqualTo("2026-11-04T08:00:00Z");
    assertThat(json.get("nextUsernameChangeAt").asString()).isEqualTo("2026-11-19T08:00:00Z");
    assertThat(json.get("accountType").asString()).isEqualTo("user");
  }

  @Test
  @DisplayName("当前用户没改过名、不在冷静期：两个时间输出 null")
  void should_output_null_times_for_plain_account() {
    when(userQueryService.currentAccount()).thenReturn(account(42L));

    restClient
        .get()
        .uri("/auth/me")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.deletionDueAt")
        .value(due -> assertThat(due).isNull())
        .jsonPath("$.nextUsernameChangeAt")
        .value(next -> assertThat(next).isNull())
        .jsonPath("$.status")
        .isEqualTo("ACTIVE");
  }

  @Test
  @DisplayName("没有当前用户：401，IDN-0401")
  void should_return_401_without_current_user() {
    when(userQueryService.currentAccount()).thenThrow(new AuthenticationRequiredException());

    restClient
        .get()
        .uri("/auth/me")
        .exchange()
        .expectStatus()
        .isUnauthorized()
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("IDN-0401");
  }
}
