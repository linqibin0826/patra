package dev.patra.identity.adapter.rest.users;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import dev.patra.identity.app.usecase.user.query.UserQueryService;
import dev.patra.identity.domain.exception.UserNotFoundException;
import dev.patra.identity.domain.model.enums.UserStatus;
import dev.patra.identity.domain.model.read.PublicUserLookup;
import dev.patra.identity.domain.model.read.PublicUserReadModel;
import dev.patra.identity.domain.model.vo.Username;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.client.RestTestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/// PublicUserController 切片测试：找到 200、旧名 301、不存在 404。
@WebMvcTest
@Import(PublicUserController.class)
@AutoConfigureRestTestClient
@DisplayName("PublicUserController 切片测试")
class PublicUserControllerIT {

  private static final JsonMapper JSON = JsonMapper.builder().build();

  @Autowired private RestTestClient restClient;

  @MockitoBean private UserQueryService userQueryService;

  @Test
  @DisplayName("找到：200，带用户名、昵称、头像（恒 null）、audience，不带邮箱和 ID")
  void should_return_public_identity() {
    when(userQueryService.publicIdentity("chen-yu"))
        .thenReturn(
            PublicUserLookup.Found.of(
                PublicUserReadModel.builder()
                    .userId(42L)
                    .username("chen-yu")
                    .displayName("陈宇")
                    .status(UserStatus.ACTIVE)
                    .emailVerified(true)
                    .build()));

    String body =
        restClient
            .get()
            .uri("/users/chen-yu")
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(String.class)
            .returnResult()
            .getResponseBody();

    JsonNode json = JSON.readTree(body);
    assertThat(json.get("username").asString()).isEqualTo("chen-yu");
    assertThat(json.get("displayName").asString()).isEqualTo("陈宇");
    assertThat(json.has("avatarUrl")).isTrue();
    assertThat(json.get("avatarUrl").isNull()).isTrue();
    assertThat(json.get("audience").asString()).isEqualTo("PUBLIC");
    assertThat(json.has("email")).isFalse();
    assertThat(json.has("userId")).isFalse();
    assertThat(json.has("status")).isFalse();
  }

  @Test
  @DisplayName("旧名：301，Location 指向新名，JSON 体带新名，不缓存")
  void should_redirect_former_username() {
    when(userQueryService.publicIdentity("old-name"))
        .thenReturn(PublicUserLookup.Moved.of(Username.of("new-name")));

    restClient
        .get()
        .uri("/users/old-name")
        .exchange()
        .expectStatus()
        .isEqualTo(301)
        .expectHeader()
        .valueEquals(HttpHeaders.LOCATION, "/users/new-name")
        .expectHeader()
        .valueEquals(HttpHeaders.CACHE_CONTROL, "no-store")
        .expectBody()
        .jsonPath("$.username")
        .isEqualTo("new-name");
  }

  @Test
  @DisplayName("不存在或不可见：404，IDN-0404")
  void should_return_404() {
    when(userQueryService.publicIdentity("nobody")).thenThrow(new UserNotFoundException());

    restClient
        .get()
        .uri("/users/nobody")
        .exchange()
        .expectStatus()
        .isNotFound()
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("IDN-0404")
        .jsonPath("$.detail")
        .isEqualTo("用户不存在");
  }
}
