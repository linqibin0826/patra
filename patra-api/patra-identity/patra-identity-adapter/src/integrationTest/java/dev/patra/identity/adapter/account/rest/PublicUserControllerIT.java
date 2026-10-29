package dev.patra.identity.adapter.account.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import dev.patra.identity.app.account.usecase.query.UserQueryService;
import dev.patra.identity.domain.account.exception.UserNotFoundException;
import dev.patra.identity.domain.account.model.enums.Occupation;
import dev.patra.identity.domain.account.model.read.PublicAudience;
import dev.patra.identity.domain.account.model.read.PublicUserLookup;
import dev.patra.identity.domain.account.model.read.PublicUserView;
import dev.patra.identity.domain.account.model.vo.Username;
import dev.patra.identity.domain.account.model.vo.avatar.Avatar;
import dev.patra.identity.domain.account.model.vo.profile.Affiliation;
import dev.patra.identity.domain.account.model.vo.profile.MeshUi;
import dev.patra.identity.domain.account.model.vo.profile.ProfileLink;
import dev.patra.identity.domain.account.model.vo.profile.ResearchInterest;
import dev.patra.identity.domain.account.model.vo.profile.RorId;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
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
  @DisplayName("有头像：avatarUrl 是带版本的 identity 相对路径")
  void should_return_avatar_url_in_public_identity() {
    when(userQueryService.publicIdentity("chen-yu"))
        .thenReturn(
            PublicUserLookup.Found.of(
                PublicUserView.builder()
                    .username("chen-yu")
                    .displayName("陈宇")
                    .avatar(Avatar.of(42L, "0123456789abcdef"))
                    .audience(PublicAudience.PUBLIC)
                    .build()));

    JsonNode json = getJson("/users/chen-yu");

    assertThat(json.get("avatarUrl").asString()).isEqualTo("/users/42/avatar?v=0123456789abcdef");
  }

  @Test
  @DisplayName("找到：200，带用户 ID（字符串）、用户名、昵称、头像（没上传为 null）、色号、audience，不带邮箱")
  void should_return_public_identity() {
    when(userQueryService.publicIdentity("chen-yu"))
        .thenReturn(
            PublicUserLookup.Found.of(
                PublicUserView.builder()
                    .userId(42L)
                    .username("chen-yu")
                    .displayName("陈宇")
                    .avatarPalette(7)
                    .audience(PublicAudience.PUBLIC)
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
    assertThat(json.get("avatarPalette").asInt()).isEqualTo(7);
    assertThat(json.get("audience").asString()).isEqualTo("PUBLIC");
    assertThat(json.has("email")).isFalse();
    assertThat(json.get("userId").asString()).isEqualTo("42");
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

  @Test
  @DisplayName("完整主页：头部、链接、专业背景、关注领域、加入年月、indexable，顶层键恰好这些")
  void should_render_full_public_page_without_settings() {
    when(userQueryService.publicIdentity("chen-yu"))
        .thenReturn(
            PublicUserLookup.Found.of(
                PublicUserView.builder()
                    .username("chen-yu")
                    .displayName("陈宇")
                    .audience(PublicAudience.PUBLIC)
                    .joinedMonth(YearMonth.of(2026, 10))
                    .bio("心内科医生")
                    .links(List.of(ProfileLink.of(null, "https://lab.org")))
                    .professional(
                        PublicUserView.Professional.of(
                            Occupation.PHYSICIAN,
                            "主治医师",
                            Affiliation.Registered.of(RorId.of("04jztag35"), "北京协和医院"),
                            "心内科"))
                    .researchInterests(
                        List.of(ResearchInterest.of(MeshUi.of("D006333"), "Heart Failure")))
                    .indexable(true)
                    .build()));

    JsonNode json = getJson("/users/chen-yu");

    List<String> keys = new ArrayList<>();
    json.propertyNames().forEach(keys::add);
    assertThat(keys)
        .containsExactlyInAnyOrder(
            "userId",
            "username",
            "displayName",
            "avatarUrl",
            "avatarPalette",
            "audience",
            "joinedAt",
            "bio",
            "links",
            "professional",
            "researchInterests",
            "indexable");
    assertThat(json.get("joinedAt").asString()).isEqualTo("2026-10");
    assertThat(json.get("bio").asString()).isEqualTo("心内科医生");
    assertThat(json.get("links").get(0).get("url").asString()).isEqualTo("https://lab.org");
    JsonNode professional = json.get("professional");
    assertThat(professional.get("occupation").asString()).isEqualTo("PHYSICIAN");
    assertThat(professional.get("jobTitle").asString()).isEqualTo("主治医师");
    assertThat(professional.get("organization").get("rorId").asString()).isEqualTo("04jztag35");
    assertThat(professional.get("organization").get("name").asString()).isEqualTo("北京协和医院");
    assertThat(professional.get("department").asString()).isEqualTo("心内科");
    assertThat(json.get("researchInterests").get(0).get("name").asString())
        .isEqualTo("Heart Failure");
    assertThat(json.get("indexable").asBoolean()).isTrue();
  }

  @Test
  @DisplayName("专业背景不可见：professional 为 null；没有加入时间时 joinedAt 为 null")
  void should_render_null_professional() {
    when(userQueryService.publicIdentity("chen-yu"))
        .thenReturn(
            PublicUserLookup.Found.of(
                PublicUserView.builder()
                    .username("chen-yu")
                    .displayName("陈宇")
                    .audience(PublicAudience.SIGNED_IN)
                    .build()));

    JsonNode json = getJson("/users/chen-yu");

    assertThat(json.get("professional").isNull()).isTrue();
    assertThat(json.get("links")).isEmpty();
    assertThat(json.get("audience").asString()).isEqualTo("SIGNED_IN");
    assertThat(json.get("indexable").asBoolean()).isFalse();
  }

  /// GET 一个路径，断言 200，返回响应体。
  ///
  /// @param path 路径
  /// @return 响应体
  private JsonNode getJson(String path) {
    String body =
        restClient
            .get()
            .uri(path)
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(String.class)
            .returnResult()
            .getResponseBody();
    return JSON.readTree(body);
  }
}
