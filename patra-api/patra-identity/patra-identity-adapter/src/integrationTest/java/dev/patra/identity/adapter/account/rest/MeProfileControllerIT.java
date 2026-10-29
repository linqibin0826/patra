package dev.patra.identity.adapter.account.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.patra.identity.app.account.usecase.profile.UpdatePrivacyCommand;
import dev.patra.identity.app.account.usecase.profile.UpdateProfileCommand;
import dev.patra.identity.app.account.usecase.query.UserQueryService;
import dev.patra.identity.domain.account.exception.CatalogUnavailableException;
import dev.patra.identity.domain.account.exception.UserPendingDeletionException;
import dev.patra.identity.domain.account.model.aggregate.UserProfile;
import dev.patra.identity.domain.account.model.enums.Occupation;
import dev.patra.identity.domain.account.model.enums.ProfessionalVisibility;
import dev.patra.identity.domain.account.model.enums.ProfileVisibility;
import dev.patra.identity.domain.account.model.vo.profile.Affiliation;
import dev.patra.identity.domain.account.model.vo.profile.Bio;
import dev.patra.identity.domain.account.model.vo.profile.LinkDraft;
import dev.patra.identity.domain.account.model.vo.profile.MeshUi;
import dev.patra.identity.domain.account.model.vo.profile.PrivacySettings;
import dev.patra.identity.domain.account.model.vo.profile.ProfileDraft;
import dev.patra.identity.domain.account.model.vo.profile.ProfileLink;
import dev.patra.identity.domain.account.model.vo.profile.ResearchInterest;
import dev.patra.identity.domain.account.model.vo.profile.RorId;
import dev.patra.identity.domain.session.exception.InvalidUserFieldsException;
import dev.patra.identity.domain.session.model.vo.UserFieldViolations;
import dev.patra.tool.cqrs.CommandBus;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.client.RestTestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/// MeProfileController 切片测试：资料与隐私设置的请求映射、响应结构与错误。
@WebMvcTest
@Import(MeProfileController.class)
@AutoConfigureRestTestClient
class MeProfileControllerIT {

  private static final JsonMapper JSON = JsonMapper.builder().build();

  @Autowired private RestTestClient restClient;

  @MockitoBean private CommandBus commandBus;

  @MockitoBean private UserQueryService userQueryService;

  @Test
  void should_map_profile_request_to_draft() {
    when(commandBus.handle(any(UpdateProfileCommand.class))).thenReturn(emptyProfile());

    put(
        Map.of(
            "displayName", "陈宇",
            "bio", "简介",
            "links", List.of(Map.of("title", "实验室", "url", "https://lab.org")),
            "occupation", "PHYSICIAN",
            "jobTitle", "主治医师",
            "organization", Map.of("rorId", "04jztag35", "name", "随便写"),
            "department", "心内科",
            "researchInterests", List.of("D006333")),
        200);

    ProfileDraft draft = capturedDraft();
    assertThat(draft.displayName()).isEqualTo("陈宇");
    assertThat(draft.bio()).isEqualTo("简介");
    assertThat(draft.links()).containsExactly(LinkDraft.of("实验室", "https://lab.org"));
    assertThat(draft.occupation()).isEqualTo("PHYSICIAN");
    assertThat(draft.jobTitle()).isEqualTo("主治医师");
    assertThat(draft.organizationRorId()).isEqualTo("04jztag35");
    assertThat(draft.organizationName()).isEqualTo("随便写");
    assertThat(draft.department()).isEqualTo("心内科");
    assertThat(draft.researchInterests()).containsExactly("D006333");
  }

  @Test
  void should_map_missing_organization_to_nulls() {
    when(commandBus.handle(any(UpdateProfileCommand.class))).thenReturn(emptyProfile());

    put(Map.of("bio", "简介"), 200);

    ProfileDraft draft = capturedDraft();
    assertThat(draft.organizationRorId()).isNull();
    assertThat(draft.organizationName()).isNull();
    assertThat(draft.links()).isEmpty();
    assertThat(draft.researchInterests()).isEmpty();
  }

  @Test
  void should_map_name_only_organization() {
    when(commandBus.handle(any(UpdateProfileCommand.class))).thenReturn(emptyProfile());

    put(Map.of("organization", Map.of("name", "某县人民医院")), 200);

    ProfileDraft draft = capturedDraft();
    assertThat(draft.organizationRorId()).isNull();
    assertThat(draft.organizationName()).isEqualTo("某县人民医院");
  }

  @Test
  void should_render_full_profile() {
    when(userQueryService.myProfile()).thenReturn(fullProfile());

    JsonNode json = get("/me/profile");

    assertThat(json.get("displayName").isNull()).isTrue();
    assertThat(json.get("bio").asString()).isEqualTo("心内科医生");
    assertThat(json.get("links").get(0).get("title").isNull()).isTrue();
    assertThat(json.get("links").get(0).get("url").asString()).isEqualTo("https://lab.org");
    assertThat(json.get("occupation").asString()).isEqualTo("PHYSICIAN");
    assertThat(json.get("jobTitle").isNull()).isTrue();
    assertThat(json.get("organization").get("rorId").asString()).isEqualTo("04jztag35");
    assertThat(json.get("organization").get("name").asString()).isEqualTo("北京协和医院");
    assertThat(json.get("department").isNull()).isTrue();
    assertThat(json.get("researchInterests").get(0).get("ui").asString()).isEqualTo("D006333");
    assertThat(json.get("researchInterests").get(0).get("name").asString())
        .isEqualTo("Heart Failure");
  }

  @Test
  void should_render_empty_profile_with_nulls_and_empty_lists() {
    when(userQueryService.myProfile()).thenReturn(emptyProfile());

    JsonNode json = get("/me/profile");

    assertThat(json.get("organization").isNull()).isTrue();
    assertThat(json.get("links").isArray()).isTrue();
    assertThat(json.get("links")).isEmpty();
    assertThat(json.get("researchInterests")).isEmpty();
  }

  @Test
  void should_return_422_with_field_paths() {
    when(commandBus.handle(any(UpdateProfileCommand.class)))
        .thenThrow(
            new InvalidUserFieldsException(List.of(UserFieldViolations.linkUrlInvalidFormat(1))));

    JsonNode json = put(Map.of("bio", "x"), 422);

    assertThat(json.get("code").asString()).isEqualTo("IDN-0422");
    assertThat(json.get("errors").get(0).get("field").asString()).isEqualTo("links[1].url");
    assertThat(json.get("errors").get(0).get("code").asString()).isEqualTo("INVALID_FORMAT");
  }

  @Test
  void should_return_409_when_pending_deletion() {
    when(commandBus.handle(any(UpdateProfileCommand.class)))
        .thenThrow(new UserPendingDeletionException());

    JsonNode json = put(Map.of("bio", "x"), 409);

    assertThat(json.get("traits").toString()).contains("PENDING_DELETION");
  }

  @Test
  void should_return_503_when_catalog_unavailable() {
    when(commandBus.handle(any(UpdateProfileCommand.class)))
        .thenThrow(new CatalogUnavailableException(new IllegalStateException("down")));

    JsonNode json = put(Map.of("bio", "x"), 503);

    assertThat(json.get("code").asString()).isEqualTo("IDN-0503");
    assertThat(json.get("traits").toString()).contains("DEP_UNAVAILABLE");
  }

  @Test
  void should_read_and_update_privacy() {
    PrivacySettings privacy =
        PrivacySettings.of(ProfileVisibility.SIGNED_IN, ProfessionalVisibility.SELF, true);
    when(userQueryService.myPrivacy()).thenReturn(PrivacySettings.defaults());
    when(commandBus.handle(any(UpdatePrivacyCommand.class))).thenReturn(privacy);

    JsonNode read = get("/me/privacy");
    JsonNode written =
        exchange(
            "/me/privacy",
            Map.of(
                "profileVisibility", "SIGNED_IN",
                "professionalVisibility", "SELF",
                "searchIndexing", true),
            200);

    assertThat(read.get("profileVisibility").asString()).isEqualTo("PUBLIC");
    assertThat(read.get("searchIndexing").asBoolean()).isFalse();
    assertThat(written.get("profileVisibility").asString()).isEqualTo("SIGNED_IN");
    assertThat(written.get("professionalVisibility").asString()).isEqualTo("SELF");
    assertThat(written.get("searchIndexing").asBoolean()).isTrue();
    ArgumentCaptor<UpdatePrivacyCommand> command =
        ArgumentCaptor.forClass(UpdatePrivacyCommand.class);
    verify(commandBus).handle(command.capture());
    assertThat(command.getValue()).isEqualTo(UpdatePrivacyCommand.of("SIGNED_IN", "SELF", true));
  }

  @Test
  void should_return_422_for_missing_privacy_fields() {
    when(commandBus.handle(any(UpdatePrivacyCommand.class)))
        .thenThrow(
            new InvalidUserFieldsException(
                List.of(
                    UserFieldViolations.privacyRequired(UserFieldViolations.PROFILE_VISIBILITY),
                    UserFieldViolations.privacyRequired(
                        UserFieldViolations.PROFESSIONAL_VISIBILITY),
                    UserFieldViolations.privacyRequired(UserFieldViolations.SEARCH_INDEXING))));

    JsonNode json = exchange("/me/privacy", Map.of(), 422);

    assertThat(json.get("errors")).hasSize(3);
  }

  /// `PUT /me/profile`，断言状态码，返回响应体。
  ///
  /// @param body 请求体
  /// @param status 期望的状态码
  /// @return 响应体
  private JsonNode put(Map<String, ?> body, int status) {
    return exchange("/me/profile", body, status);
  }

  /// 发 PUT 请求，断言状态码，返回响应体。
  ///
  /// @param path 路径
  /// @param body 请求体
  /// @param status 期望的状态码
  /// @return 响应体
  private JsonNode exchange(String path, Map<String, ?> body, int status) {
    String response =
        restClient
            .put()
            .uri(path)
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .exchange()
            .expectStatus()
            .isEqualTo(status)
            .expectBody(String.class)
            .returnResult()
            .getResponseBody();
    return JSON.readTree(response);
  }

  /// 发 GET 请求，断言 200，返回响应体。
  ///
  /// @param path 路径
  /// @return 响应体
  private JsonNode get(String path) {
    String response =
        restClient
            .get()
            .uri(path)
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(String.class)
            .returnResult()
            .getResponseBody();
    return JSON.readTree(response);
  }

  /// 抓交给命令总线的资料草稿。
  ///
  /// @return 草稿
  private ProfileDraft capturedDraft() {
    ArgumentCaptor<UpdateProfileCommand> command =
        ArgumentCaptor.forClass(UpdateProfileCommand.class);
    verify(commandBus).handle(command.capture());
    return command.getValue().draft();
  }

  /// 空资料。
  ///
  /// @return 资料
  private static UserProfile emptyProfile() {
    return UserProfile.restore().id(1L).userId(42L).version(0L).build();
  }

  /// 一份有部分字段的资料：昵称跟随用户名、职称与科室为空。
  ///
  /// @return 资料
  private static UserProfile fullProfile() {
    return UserProfile.restore()
        .id(1L)
        .userId(42L)
        .bio(new Bio("心内科医生"))
        .links(List.of(ProfileLink.of(null, "https://lab.org")))
        .occupation(Occupation.PHYSICIAN)
        .affiliation(Affiliation.Registered.of(RorId.of("04jztag35"), "北京协和医院"))
        .researchInterests(List.of(ResearchInterest.of(MeshUi.of("D006333"), "Heart Failure")))
        .version(0L)
        .build();
  }
}
