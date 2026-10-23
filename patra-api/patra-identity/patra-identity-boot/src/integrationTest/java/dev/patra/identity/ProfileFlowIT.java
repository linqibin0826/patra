package dev.patra.identity;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;

/// 资料与隐私设置的完整流程（PAP-95）：真实的过滤器链、PostgreSQL、Redis，catalog 由 WireMock 扮演，
/// 经 Spring Cloud LoadBalancer + SimpleDiscoveryClient 走 `lb://patra-catalog` 这条真实的路。
///
/// 这个上下文单独打开服务发现（共用的测试配置关着它，`LoadBalancerClientConfiguration` 挂在它下面）。
class ProfileFlowIT extends IdentityFlowITSupport {

  @RegisterExtension
  static final WireMockExtension catalog =
      WireMockExtension.newInstance()
          .options(wireMockConfig().dynamicPort().http2PlainDisabled(true))
          .build();

  @DynamicPropertySource
  static void catalogInstance(DynamicPropertyRegistry registry) {
    registry.add("spring.cloud.discovery.enabled", () -> "true");
    registry.add(
        "spring.cloud.discovery.client.simple.instances.patra-catalog[0].uri", catalog::baseUrl);
  }

  /// 每个用例从干净的桩开始。
  @BeforeEach
  void resetCatalog() {
    catalog.resetAll();
  }

  @Test
  void should_default_privacy_and_empty_profile_after_register() {
    HttpHeaders auth = signIn("fresh@example.com", "fresh-user");

    JsonNode privacy = json(get("/me/privacy", auth));
    JsonNode profile = json(get("/me/profile", auth));

    assertThat(privacy.get("profileVisibility").asString()).isEqualTo("PUBLIC");
    assertThat(privacy.get("professionalVisibility").asString()).isEqualTo("PUBLIC");
    assertThat(privacy.get("searchIndexing").asBoolean()).isFalse();
    assertThat(profile.get("displayName").isNull()).isTrue();
    assertThat(profile.get("organization").isNull()).isTrue();
    assertThat(profile.get("links")).isEmpty();
    assertThat(profile.get("researchInterests")).isEmpty();
  }

  @Test
  void should_save_catalog_snapshots_and_keep_them_without_calling_catalog_again() {
    HttpHeaders auth = signIn("snap@example.com", "snap-user");
    stubOrganization();
    stubMesh("D006333", "Heart Failure");
    stubMesh("D001281", "Atrial Fibrillation");

    RawResponse saved =
        put(
            "/me/profile",
            Map.of(
                "bio", "心内科医生",
                "organization", Map.of("rorId", "04JZTAG35", "name", "随便写"),
                "researchInterests", List.of("D006333", "D001281")),
            auth);

    assertThat(saved.status()).as(saved.body()).isEqualTo(200);
    JsonNode body = json(saved);
    assertThat(body.get("organization").get("rorId").asString()).isEqualTo("04jztag35");
    assertThat(body.get("organization").get("name").asString()).isEqualTo("北京协和医院");
    assertThat(body.get("researchInterests").get(1).get("name").asString())
        .isEqualTo("Atrial Fibrillation");
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT organization_name FROM idn_user_profile WHERE user_id = ?",
                String.class,
                userIdOf("snap@example.com")))
        .isEqualTo("北京协和医院");

    catalog.resetAll();
    catalog.stubFor(WireMock.get(anyUrl()).willReturn(aResponse().withStatus(500)));
    RawResponse bioOnly =
        put(
            "/me/profile",
            Map.of(
                "bio", "只改简介",
                "organization", Map.of("rorId", "04jztag35"),
                "researchInterests", List.of("D006333", "D001281")),
            auth);

    assertThat(bioOnly.status()).as(bioOnly.body()).isEqualTo(200);
    assertThat(json(bioOnly).get("organization").get("name").asString()).isEqualTo("北京协和医院");
    assertThat(catalog.getAllServeEvents()).isEmpty();
  }

  @Test
  void should_save_free_text_affiliation_without_catalog() {
    HttpHeaders auth = signIn("free@example.com", "free-user");

    RawResponse saved =
        put("/me/profile", Map.of("organization", Map.of("name", " 某县 人民医院 ")), auth);

    assertThat(saved.status()).as(saved.body()).isEqualTo(200);
    assertThat(json(saved).get("organization").get("rorId").isNull()).isTrue();
    assertThat(json(saved).get("organization").get("name").asString()).isEqualTo("某县 人民医院");
    assertThat(catalog.getAllServeEvents()).isEmpty();
  }

  @Test
  void should_report_unknown_ror_id_and_mesh_ui_as_422() {
    HttpHeaders auth = signIn("missing@example.com", "missing-user");
    catalog.stubFor(WireMock.get(anyUrl()).willReturn(notFound()));

    RawResponse response =
        put(
            "/me/profile",
            Map.of(
                "organization",
                Map.of("rorId", "0abcdefgh"),
                "researchInterests",
                List.of("D999999")),
            auth);

    assertThat(response.status()).as(response.body()).isEqualTo(422);
    JsonNode errors = json(response).get("errors");
    assertThat(errors).hasSize(2);
    assertThat(errors.get(0).get("field").asString()).isEqualTo("organization.rorId");
    assertThat(errors.get(0).get("code").asString()).isEqualTo("NOT_FOUND");
    assertThat(errors.get(1).get("field").asString()).isEqualTo("researchInterests[0]");
    assertThat(errors.get(1).get("code").asString()).isEqualTo("NOT_FOUND");
  }

  @Test
  void should_return_503_and_keep_profile_when_catalog_fails() {
    HttpHeaders auth = signIn("down@example.com", "down-user");
    put("/me/profile", Map.of("bio", "原来的简介"), auth);
    catalog.stubFor(WireMock.get(anyUrl()).willReturn(aResponse().withStatus(500)));

    RawResponse response =
        put("/me/profile", Map.of("bio", "新简介", "researchInterests", List.of("D003920")), auth);

    assertThat(response.status()).as(response.body()).isEqualTo(503);
    assertThat(json(response).get("code").asString()).isEqualTo("IDN-0503");
    assertThat(json(get("/me/profile", auth)).get("bio").asString()).isEqualTo("原来的简介");
  }

  @Test
  void should_update_and_read_back_privacy() {
    HttpHeaders auth = signIn("privacy@example.com", "privacy-user");

    RawResponse saved =
        put(
            "/me/privacy",
            Map.of(
                "profileVisibility", "SIGNED_IN",
                "professionalVisibility", "SELF",
                "searchIndexing", true),
            auth);
    RawResponse invalid = put("/me/privacy", Map.of("profileVisibility", "PRIVATE"), auth);

    assertThat(saved.status()).as(saved.body()).isEqualTo(200);
    JsonNode read = json(get("/me/privacy", auth));
    assertThat(read.get("profileVisibility").asString()).isEqualTo("SIGNED_IN");
    assertThat(read.get("professionalVisibility").asString()).isEqualTo("SELF");
    assertThat(read.get("searchIndexing").asBoolean()).isTrue();
    assertThat(invalid.status()).isEqualTo(422);
    assertThat(json(invalid).get("errors")).hasSize(3);
  }

  @Test
  void should_require_sign_in() {
    assertThat(get("/me/profile", noHeaders()).status()).isEqualTo(401);
    assertThat(put("/me/privacy", Map.of(), noHeaders()).status()).isEqualTo(401);
  }

  /// 注册并取当前会话的断言头。
  ///
  /// @param email 邮箱
  /// @param username 用户名
  /// @return `Authorization` 头
  private HttpHeaders signIn(String email, String username) {
    long userId = userIdOf(register(email, username));
    return assertionFor(userId, latestSessionIdOf(userId));
  }

  /// 按邮箱查用户 ID。
  ///
  /// @param email 邮箱
  /// @return 用户 ID
  private long userIdOf(String email) {
    return jdbcTemplate.queryForObject(
        "SELECT id FROM idn_user WHERE email = ?", Long.class, email);
  }

  /// 发一个 JSON PUT。
  ///
  /// @param uri 路径
  /// @param body 请求体
  /// @param headers 请求头
  /// @return 响应
  private RawResponse put(String uri, Map<String, Object> body, HttpHeaders headers) {
    return exchange(HttpMethod.PUT, uri, body, headers);
  }

  /// catalog 有协和（中文名优先）。
  private static void stubOrganization() {
    catalog.stubFor(
        WireMock.get(urlEqualTo("/_internal/organizations/04jztag35"))
            .willReturn(
                okJson(
                    """
                    {"rorId":"04jztag35","displayName":"Peking Union Medical College Hospital",
                     "zhName":"北京协和医院","countryCode":"CN","types":["healthcare"]}""")));
  }

  /// catalog 有这个主题词。
  ///
  /// @param ui 主题词 UI
  /// @param name 英文名
  private static void stubMesh(String ui, String name) {
    catalog.stubFor(
        WireMock.get(urlEqualTo("/_internal/mesh/descriptors/" + ui))
            .willReturn(
                okJson("{\"ui\":\"" + ui + "\",\"name\":\"" + name + "\",\"treeNumbers\":[]}")));
  }

  /// catalog 的 404 ProblemDetail，带 `NOT_FOUND` 特征。
  ///
  /// @return 响应定义
  private static ResponseDefinitionBuilder notFound() {
    return aResponse()
        .withStatus(404)
        .withHeader("Content-Type", "application/problem+json")
        .withBody(
            """
            {"type":"about:blank","title":"Not Found","status":404,"detail":"不存在",
             "code":"CAT-0404","traits":["NOT_FOUND"]}""");
  }
}
