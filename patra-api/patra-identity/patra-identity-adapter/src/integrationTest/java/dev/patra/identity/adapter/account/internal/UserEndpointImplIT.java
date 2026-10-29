package dev.patra.identity.adapter.account.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.patra.identity.app.account.usecase.query.ResolvedUser;
import dev.patra.identity.app.account.usecase.query.UserResolutionQueryService;
import dev.patra.identity.app.account.usecase.query.UserSuggestionQueryService;
import dev.patra.identity.app.account.usecase.query.UserSummaryQueryService;
import dev.patra.identity.domain.account.model.enums.UserStatus;
import dev.patra.identity.domain.account.model.read.UserSummaryView;
import dev.patra.identity.domain.account.model.vo.avatar.Avatar;
import dev.patra.identity.domain.session.exception.InvalidUserFieldsException;
import dev.patra.identity.domain.session.model.vo.UserFieldViolations;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.client.RestTestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/// UserEndpointImpl 切片测试：批量用户摘要（PAP-108 spec 5.1）、@ 联想与用户名批量解析（PAP-118 spec 4.1、5.1）
/// 的 JSON 形状。
@WebMvcTest
@Import(UserEndpointImpl.class)
@AutoConfigureRestTestClient
@DisplayName("UserEndpointImpl 切片测试")
class UserEndpointImplIT {

  private static final JsonMapper JSON = JsonMapper.builder().build();
  private static final String SUMMARIES = "/_internal/users/summaries";
  private static final String SUGGEST = "/_internal/users/suggest";
  private static final String RESOLVE = "/_internal/users/resolve/by-usernames";

  @Autowired private RestTestClient restClient;

  @MockitoBean private UserSummaryQueryService userSummaryQueryService;
  @MockitoBean private UserSuggestionQueryService userSuggestionQueryService;
  @MockitoBean private UserResolutionQueryService userResolutionQueryService;

  @Test
  @DisplayName("请求体里的 ID 是 JSON 数字；响应里用户 ID 是字符串，状态是名字，已注销是占位")
  void should_return_summaries_with_string_ids() {
    when(userSummaryQueryService.summaries(List.of(42L, 43L), Optional.of(7L)))
        .thenReturn(
            List.of(
                UserSummaryView.builder()
                    .userId(42L)
                    .username("chen-yu")
                    .displayName("陈宇")
                    .avatar(Avatar.of(42L, "0123456789abcdef"))
                    .avatarPalette(5)
                    .status(UserStatus.ACTIVE)
                    .emailVerified(true)
                    .profileVisible(true)
                    .build(),
                UserSummaryView.builder()
                    .userId(43L)
                    .avatarPalette(9)
                    .status(UserStatus.DELETED)
                    .build()));

    JsonNode users = post(SUMMARIES, "{\"userIds\":[42,43],\"viewerUserId\":7}").get("users");

    JsonNode active = users.get(0);
    assertThat(active.get("userId").isString()).isTrue();
    assertThat(active.get("userId").asString()).isEqualTo("42");
    assertThat(active.get("username").asString()).isEqualTo("chen-yu");
    assertThat(active.get("displayName").asString()).isEqualTo("陈宇");
    assertThat(active.get("avatarUrl").asString()).isEqualTo("/users/42/avatar?v=0123456789abcdef");
    assertThat(active.get("avatarPalette").asInt()).isEqualTo(5);
    assertThat(active.get("accountStatus").asString()).isEqualTo("ACTIVE");
    assertThat(active.get("emailVerified").asBoolean()).isTrue();
    assertThat(active.get("profileVisible").asBoolean()).isTrue();
    JsonNode deleted = users.get(1);
    assertThat(deleted.get("userId").asString()).isEqualTo("43");
    assertThat(deleted.get("username").isNull()).isTrue();
    assertThat(deleted.get("displayName").isNull()).isTrue();
    assertThat(deleted.get("avatarUrl").isNull()).isTrue();
    assertThat(deleted.get("accountStatus").asString()).isEqualTo("DELETED");
    assertThat(deleted.get("profileVisible").asBoolean()).isFalse();
  }

  @Test
  @DisplayName("不带访客：按匿名算")
  void should_pass_empty_viewer_when_absent() {
    when(userSummaryQueryService.summaries(any(), any())).thenReturn(List.of());

    post(SUMMARIES, "{\"userIds\":[42]}");

    verify(userSummaryQueryService).summaries(List.of(42L), Optional.empty());
  }

  @Test
  @DisplayName("超过上限：422，errors 带字段名与原因码")
  void should_return_422_with_field_errors() {
    when(userSummaryQueryService.summaries(any(), any()))
        .thenThrow(new InvalidUserFieldsException(List.of(UserFieldViolations.userIdsTooMany())));

    String body =
        restClient
            .post()
            .uri(SUMMARIES)
            .contentType(MediaType.APPLICATION_JSON)
            .body("{\"userIds\":[1]}")
            .exchange()
            .expectStatus()
            .isEqualTo(422)
            .expectBody(String.class)
            .returnResult()
            .getResponseBody();

    JsonNode error = JSON.readTree(body).get("errors").get(0);
    assertThat(error.get("field").asString()).isEqualTo("userIds");
    assertThat(error.get("code").asString()).isEqualTo("TOO_MANY");
  }

  @Test
  @DisplayName("联想：三个字段原样到服务；响应项是摘要卡片，用户 ID 是字符串")
  void should_return_suggestions_as_summary_cards() {
    when(userSuggestionQueryService.suggest("chen", Optional.of(7L), 10))
        .thenReturn(
            List.of(
                UserSummaryView.builder()
                    .userId(42L)
                    .username("chen-yu")
                    .displayName("陈宇")
                    .avatar(Avatar.of(42L, "0123456789abcdef"))
                    .avatarPalette(5)
                    .status(UserStatus.ACTIVE)
                    .emailVerified(true)
                    .profileVisible(true)
                    .build()));

    JsonNode user =
        post(SUGGEST, "{\"prefix\":\"chen\",\"viewerUserId\":7,\"limit\":10}").get("users").get(0);

    assertThat(user.get("userId").isString()).isTrue();
    assertThat(user.get("userId").asString()).isEqualTo("42");
    assertThat(user.get("username").asString()).isEqualTo("chen-yu");
    assertThat(user.get("displayName").asString()).isEqualTo("陈宇");
    assertThat(user.get("avatarUrl").asString()).isEqualTo("/users/42/avatar?v=0123456789abcdef");
    assertThat(user.get("avatarPalette").asInt()).isEqualTo(5);
    assertThat(user.get("accountStatus").asString()).isEqualTo("ACTIVE");
    assertThat(user.get("profileVisible").asBoolean()).isTrue();
  }

  @Test
  @DisplayName("联想不带访客：按匿名算")
  void should_pass_empty_viewer_to_suggestions_when_absent() {
    when(userSuggestionQueryService.suggest(any(), any(), any())).thenReturn(List.of());

    post(SUGGEST, "{\"prefix\":\"chen\",\"limit\":10}");

    verify(userSuggestionQueryService).suggest("chen", Optional.empty(), 10);
  }

  @Test
  @DisplayName("联想条数越界：422，errors 带字段名与原因码")
  void should_return_422_for_bad_suggestion_limit() {
    when(userSuggestionQueryService.suggest(any(), any(), any()))
        .thenThrow(new InvalidUserFieldsException(List.of(UserFieldViolations.limitOutOfRange())));

    JsonNode error = postExpecting422(SUGGEST, "{\"prefix\":\"chen\",\"limit\":21}");

    assertThat(error.get("field").asString()).isEqualTo("limit");
    assertThat(error.get("code").asString()).isEqualTo("INVALID_FORMAT");
  }

  @Test
  @DisplayName("解析：回显请求原文，用户 ID 是字符串，状态是名字")
  void should_return_resolutions_echoing_requested_text() {
    when(userResolutionQueryService.resolveByUsernames(List.of("Chen-Yu")))
        .thenReturn(List.of(ResolvedUser.of("Chen-Yu", 42L, UserStatus.BANNED)));

    JsonNode user = post(RESOLVE, "{\"usernames\":[\"Chen-Yu\"]}").get("users").get(0);

    assertThat(user.get("username").asString()).isEqualTo("Chen-Yu");
    assertThat(user.get("userId").isString()).isTrue();
    assertThat(user.get("userId").asString()).isEqualTo("42");
    assertThat(user.get("accountStatus").asString()).isEqualTo("BANNED");
  }

  @Test
  @DisplayName("解析：含 null 的列表原样到服务")
  void should_pass_null_elements_to_resolution_service() {
    when(userResolutionQueryService.resolveByUsernames(any())).thenReturn(List.of());

    post(RESOLVE, "{\"usernames\":[\"chen\",null]}");

    verify(userResolutionQueryService).resolveByUsernames(Arrays.asList("chen", null));
  }

  /// 发请求，断言 422，返回 `errors` 的第一条。
  ///
  /// @param uri 路径
  /// @param json 请求体
  /// @return 第一条字段错误
  private JsonNode postExpecting422(String uri, String json) {
    String body =
        restClient
            .post()
            .uri(uri)
            .contentType(MediaType.APPLICATION_JSON)
            .body(json)
            .exchange()
            .expectStatus()
            .isEqualTo(422)
            .expectBody(String.class)
            .returnResult()
            .getResponseBody();
    return JSON.readTree(body).get("errors").get(0);
  }

  /// 发请求，断言 200，返回响应体。
  ///
  /// @param uri 路径
  /// @param json 请求体
  /// @return 响应体
  private JsonNode post(String uri, String json) {
    String body =
        restClient
            .post()
            .uri(uri)
            .contentType(MediaType.APPLICATION_JSON)
            .body(json)
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(String.class)
            .returnResult()
            .getResponseBody();
    return JSON.readTree(body);
  }
}
