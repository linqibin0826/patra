package dev.patra.identity.adapter.rest.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.patra.identity.app.usecase.login.LoginUserCommand;
import dev.patra.identity.app.usecase.login.LoginUserResult;
import dev.patra.identity.app.usecase.logout.LogoutUserCommand;
import dev.patra.identity.app.usecase.register.RegisterUserCommand;
import dev.patra.identity.app.usecase.register.RegisterUserResult;
import dev.patra.identity.app.usecase.user.query.UserQueryService;
import dev.patra.identity.domain.exception.InvalidCredentialsException;
import dev.patra.identity.domain.exception.InvalidUserFieldsException;
import dev.patra.identity.domain.exception.LoginTemporarilyLockedException;
import dev.patra.identity.domain.exception.TemporarilyUnavailableException;
import dev.patra.identity.domain.exception.UserBannedException;
import dev.patra.identity.domain.exception.UserIdentifierTakenException;
import dev.patra.identity.domain.model.enums.UserStatus;
import dev.patra.identity.domain.model.read.UserAccountReadModel;
import dev.patra.identity.domain.model.vo.UserFieldViolations;
import dev.patra.tool.core.security.AuthenticationRequiredException;
import dev.patra.tool.cqrs.CommandBus;
import java.time.Duration;
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

  @Test
  @DisplayName("注册成功返回 201，带会话令牌，userId 是字符串；原始输入原样交给命令")
  void should_register_and_return_201() {
    when(commandBus.handle(any(RegisterUserCommand.class)))
        .thenReturn(
            RegisterUserResult.of("patra_user_x", 352303128713027974L, "chen.yu@example.com"));

    restClient
        .post()
        .uri("/auth/register")
        .contentType(MediaType.APPLICATION_JSON)
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
        .jsonPath("$.userId")
        .value(
            userId -> assertThat(userId).isInstanceOf(String.class).isEqualTo("352303128713027974"))
        .jsonPath("$.email")
        .isEqualTo("chen.yu@example.com");

    ArgumentCaptor<RegisterUserCommand> command =
        ArgumentCaptor.forClass(RegisterUserCommand.class);
    verify(commandBus).handle(command.capture());
    assertThat(command.getValue().email()).isEqualTo(" Chen.Yu@Example.com ");
    assertThat(command.getValue().username()).isEqualTo(" Chen-Yu ");
    assertThat(command.getValue().password()).isEqualTo("correct horse battery");
    assertThat(command.getValue().clientType()).isNull();
    assertThat(command.getValue().deviceId()).isNull();
  }

  @Test
  @DisplayName("登录成功返回 200，带会话令牌；clientType 和 deviceId 原样交给命令")
  void should_login_and_return_200() {
    when(commandBus.handle(any(LoginUserCommand.class)))
        .thenReturn(LoginUserResult.of("patra_user_x", 42L, "chen.yu@example.com"));

    restClient
        .post()
        .uri("/auth/login")
        .contentType(MediaType.APPLICATION_JSON)
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
        .jsonPath("$.userId")
        .isEqualTo("42")
        .jsonPath("$.email")
        .isEqualTo("chen.yu@example.com");

    ArgumentCaptor<LoginUserCommand> command = ArgumentCaptor.forClass(LoginUserCommand.class);
    verify(commandBus).handle(command.capture());
    assertThat(command.getValue().clientType()).isEqualTo("web");
    assertThat(command.getValue().deviceId()).isEqualTo("mac-safari");
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
  @DisplayName("登出返回 204，没有响应体；命令没有字段")
  void should_logout_and_return_204() {
    restClient.post().uri("/auth/logout").exchange().expectStatus().isNoContent();

    verify(commandBus).handle(LogoutUserCommand.of());
  }

  @Test
  @DisplayName("当前用户：200，带 ID、邮箱、账号类型")
  void should_return_current_user() {
    when(userQueryService.currentAccount())
        .thenReturn(UserAccountReadModel.of(42L, "chen.yu@example.com", UserStatus.ACTIVE));

    restClient
        .get()
        .uri("/auth/me")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.userId")
        .isEqualTo("42")
        .jsonPath("$.email")
        .isEqualTo("chen.yu@example.com")
        .jsonPath("$.accountType")
        .isEqualTo("user");
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
