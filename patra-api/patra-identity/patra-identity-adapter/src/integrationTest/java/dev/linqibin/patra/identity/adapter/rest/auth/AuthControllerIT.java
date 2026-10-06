package dev.linqibin.patra.identity.adapter.rest.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.linqibin.commons.cqrs.CommandBus;
import dev.linqibin.patra.identity.app.usecase.authenticate.AuthenticateUserCommand;
import dev.linqibin.patra.identity.app.usecase.authenticate.AuthenticateUserResult;
import dev.linqibin.patra.identity.app.usecase.register.RegisterUserCommand;
import dev.linqibin.patra.identity.app.usecase.register.RegisterUserResult;
import dev.linqibin.patra.identity.domain.exception.EmailAlreadyRegisteredException;
import dev.linqibin.patra.identity.domain.exception.InvalidCredentialsException;
import dev.linqibin.patra.identity.domain.exception.InvalidUserFieldsException;
import dev.linqibin.patra.identity.domain.exception.LoginTemporarilyLockedException;
import dev.linqibin.patra.identity.domain.exception.TemporarilyUnavailableException;
import dev.linqibin.patra.identity.domain.exception.UserBannedException;
import dev.linqibin.patra.identity.domain.model.vo.UserFieldViolations;
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

  @Test
  @DisplayName("注册成功返回 201，userId 是字符串；原始输入原样交给命令")
  void should_register_and_return_201() {
    when(commandBus.handle(any(RegisterUserCommand.class)))
        .thenReturn(RegisterUserResult.of(352303128713027974L, "chen.yu@example.com"));

    restClient
        .post()
        .uri("/auth/register")
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("email", " Chen.Yu@Example.com ", "password", "correct horse battery"))
        .exchange()
        .expectStatus()
        .isCreated()
        .expectBody()
        .jsonPath("$.userId")
        .value(
            userId -> assertThat(userId).isInstanceOf(String.class).isEqualTo("352303128713027974"))
        .jsonPath("$.email")
        .isEqualTo("chen.yu@example.com");

    ArgumentCaptor<RegisterUserCommand> command =
        ArgumentCaptor.forClass(RegisterUserCommand.class);
    verify(commandBus).handle(command.capture());
    assertThat(command.getValue().email()).isEqualTo(" Chen.Yu@Example.com ");
    assertThat(command.getValue().password()).isEqualTo("correct horse battery");
  }

  @Test
  @DisplayName("登录成功返回 200，userId 是字符串")
  void should_login_and_return_200() {
    when(commandBus.handle(any(AuthenticateUserCommand.class)))
        .thenReturn(AuthenticateUserResult.of(42L, "chen.yu@example.com"));

    restClient
        .post()
        .uri("/auth/login")
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("email", "chen.yu@example.com", "password", "correct horse battery"))
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.userId")
        .value(userId -> assertThat(userId).isInstanceOf(String.class).isEqualTo("42"));
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
  @DisplayName("邮箱已注册：409")
  void should_render_conflict() {
    when(commandBus.handle(any(RegisterUserCommand.class)))
        .thenThrow(new EmailAlreadyRegisteredException());

    restClient
        .post()
        .uri("/auth/register")
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("email", "chen.yu@example.com", "password", "correct horse battery"))
        .exchange()
        .expectStatus()
        .isEqualTo(409)
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("IDN-0409")
        .jsonPath("$.detail")
        .isEqualTo("该邮箱已注册");
  }

  @Test
  @DisplayName("邮箱或密码错误：401")
  void should_render_unauthorized() {
    when(commandBus.handle(any(AuthenticateUserCommand.class)))
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
    when(commandBus.handle(any(AuthenticateUserCommand.class)))
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
    when(commandBus.handle(any(AuthenticateUserCommand.class)))
        .thenThrow(new UserBannedException());

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
    when(commandBus.handle(any(AuthenticateUserCommand.class)))
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
}
