package dev.patra.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import tools.jackson.databind.JsonNode;

/// 公开主页按隐私开关与账号状态的 404 矩阵（spec §8.3、PAP-95 AC 3 / 4 / 6）：匿名、另一个登录用户、本人
/// 三种请求者。真实过滤器链、断言用测试时钟签；资料不带机构 ROR ID 与关注领域，不需要 catalog。
class PublicProfileFlowIT extends IdentityFlowITSupport {

  private static final List<String> PUBLIC_KEYS =
      List.of(
          "username",
          "displayName",
          "avatarUrl",
          "audience",
          "joinedAt",
          "bio",
          "links",
          "professional",
          "researchInterests",
          "indexable");

  @Test
  void should_follow_profile_visibility_for_each_requester() {
    Party party = party("vis");
    verifyEmail(party.ownerId());

    assertVisibility(party, 200, 200, 200);
    assertThat(audienceSeenBy(party.owner(), party)).isEqualTo("PUBLIC");

    setPrivacy(party, "SIGNED_IN", "PUBLIC");
    assertVisibility(party, 404, 200, 200);
    assertThat(audienceSeenBy(party.other(), party)).isEqualTo("SIGNED_IN");

    setPrivacy(party, "SELF", "PUBLIC");
    assertVisibility(party, 404, 404, 200);
    assertThat(audienceSeenBy(party.owner(), party)).isEqualTo("SELF");
  }

  @Test
  void should_hide_unverified_page_from_others_and_its_links_from_everyone() {
    Party party = party("unv");
    saveProfile(party);

    assertVisibility(party, 404, 404, 200);
    assertThat(page(party, party.owner()).get("links")).isEmpty();

    verifyEmail(party.ownerId());
    assertThat(page(party, noHeaders()).get("links").get(0).get("url").asString())
        .isEqualTo("https://lab.example.edu/chen");
  }

  @Test
  void should_hide_professional_block_from_others_when_self_only() {
    Party party = party("pro");
    verifyEmail(party.ownerId());
    saveProfile(party);
    setPrivacy(party, "PUBLIC", "SELF");

    assertThat(page(party, noHeaders()).get("professional").isNull()).isTrue();
    assertThat(page(party, party.other()).get("professional").isNull()).isTrue();
    JsonNode own = page(party, party.owner()).get("professional");
    assertThat(own.get("occupation").asString()).isEqualTo("PHYSICIAN");
    assertThat(own.get("jobTitle").asString()).isEqualTo("主治医师");
    assertThat(own.get("organization").get("name").asString()).isEqualTo("某县人民医院");
    assertThat(own.get("department").asString()).isEqualTo("心内科");
  }

  @Test
  void should_hide_pending_deletion_banned_and_deleted_accounts_from_others() {
    Party pending = party("pend");
    verifyEmail(pending.ownerId());
    jdbcTemplate.update(
        "UPDATE idn_user SET status = 'PENDING_DELETION', deletion_due_at = now() + interval '15 days'"
            + " WHERE id = ?",
        pending.ownerId());
    assertVisibility(pending, 404, 404, 200);

    Party banned = party("ban");
    verifyEmail(banned.ownerId());
    jdbcTemplate.update(
        "UPDATE idn_user SET status = 'BANNED', banned_at = now() WHERE id = ?", banned.ownerId());
    assertThat(status(banned, noHeaders())).isEqualTo(404);
    assertThat(status(banned, banned.other())).isEqualTo(404);

    Party deleted = party("del");
    jdbcTemplate.update(
        "UPDATE idn_user SET status = 'DELETED', deleted_at = now(), email = NULL,"
            + " email_verified_at = NULL WHERE id = ?",
        deleted.ownerId());
    assertThat(status(deleted, noHeaders())).isEqualTo(404);
    assertThat(status(deleted, deleted.other())).isEqualTo(404);
  }

  @Test
  void should_redirect_former_username_only_to_those_who_can_see_the_owner() {
    Party party = party("old");
    verifyEmail(party.ownerId());
    setPrivacy(party, "SIGNED_IN", "PUBLIC");
    RawResponse renamed =
        exchange(
            HttpMethod.PATCH, "/me/username", Map.of("username", "old-renamed"), party.owner());
    assertThat(renamed.status()).as(renamed.body()).isEqualTo(200);

    assertThat(get("/users/" + party.username(), noHeaders()).status()).isEqualTo(404);
    RawResponse moved = get("/users/" + party.username(), party.other());
    assertThat(moved.status()).isEqualTo(301);
    assertThat(json(moved).get("username").asString()).isEqualTo("old-renamed");
  }

  @Test
  void should_expose_only_page_fields_and_no_settings() {
    Party party = party("keys");
    verifyEmail(party.ownerId());
    saveProfile(party);

    JsonNode json = page(party, noHeaders());

    List<String> keys = new ArrayList<>();
    json.propertyNames().forEach(keys::add);
    assertThat(keys).containsExactlyInAnyOrderElementsOf(PUBLIC_KEYS);
    assertThat(json.get("joinedAt").asString()).matches("\\d{4}-\\d{2}");
    assertThat(json.get("indexable").asBoolean()).isFalse();

    setPrivacy(party, "PUBLIC", "PUBLIC", true);
    assertThat(page(party, noHeaders()).get("indexable").asBoolean()).isTrue();
  }

  /// 一个主页主人和另一个登录用户。
  ///
  /// @param name 区分用例的后缀
  /// @return 两人
  private Party party(String name) {
    String username = name + "-owner";
    long ownerId = userIdOf(register(name + ".owner@example.com", username));
    long otherId = userIdOf(register(name + ".other@example.com", name + "-other"));
    return new Party(
        username,
        ownerId,
        assertionFor(ownerId, latestSessionIdOf(ownerId)),
        assertionFor(otherId, latestSessionIdOf(otherId)));
  }

  /// 主人写一份不需要 catalog 的资料：链接、专业背景（自由文本机构）。
  ///
  /// @param party 两人
  private void saveProfile(Party party) {
    RawResponse saved =
        exchange(
            HttpMethod.PUT,
            "/me/profile",
            Map.of(
                "bio", "心内科医生",
                "links", List.of(Map.of("title", "实验室", "url", "https://lab.example.edu/chen")),
                "occupation", "PHYSICIAN",
                "jobTitle", "主治医师",
                "organization", Map.of("name", "某县人民医院"),
                "department", "心内科"),
            party.owner());
    assertThat(saved.status()).as(saved.body()).isEqualTo(200);
  }

  /// 主人改隐私设置，不允许收录。
  ///
  /// @param party 两人
  /// @param profile 主页可见性
  /// @param professional 专业背景可见性
  private void setPrivacy(Party party, String profile, String professional) {
    setPrivacy(party, profile, professional, false);
  }

  /// 主人改隐私设置。
  ///
  /// @param party 两人
  /// @param profile 主页可见性
  /// @param professional 专业背景可见性
  /// @param indexing 是否允许收录
  private void setPrivacy(Party party, String profile, String professional, boolean indexing) {
    RawResponse saved =
        exchange(
            HttpMethod.PUT,
            "/me/privacy",
            Map.of(
                "profileVisibility",
                profile,
                "professionalVisibility",
                professional,
                "searchIndexing",
                indexing),
            party.owner());
    assertThat(saved.status()).as(saved.body()).isEqualTo(200);
  }

  /// 断言匿名、另一个登录用户、本人三种请求者拿到的状态码。
  ///
  /// @param party 两人
  /// @param anonymous 匿名
  /// @param other 另一个登录用户
  /// @param owner 本人
  private void assertVisibility(Party party, int anonymous, int other, int owner) {
    assertThat(status(party, noHeaders())).as("匿名").isEqualTo(anonymous);
    assertThat(status(party, party.other())).as("别人").isEqualTo(other);
    assertThat(status(party, party.owner())).as("本人").isEqualTo(owner);
  }

  /// 请求主人主页的状态码。
  ///
  /// @param party 两人
  /// @param requester 请求者的头
  /// @return 状态码
  private int status(Party party, HttpHeaders requester) {
    return get("/users/" + party.username(), requester).status();
  }

  /// 请求主人主页，断言 200，返回响应体。
  ///
  /// @param party 两人
  /// @param requester 请求者的头
  /// @return 响应体
  private JsonNode page(Party party, HttpHeaders requester) {
    RawResponse response = get("/users/" + party.username(), requester);
    assertThat(response.status()).as(response.body()).isEqualTo(200);
    return json(response);
  }

  /// 请求者看到的 `audience`。
  ///
  /// @param requester 请求者的头
  /// @param party 两人
  /// @return `audience`
  private String audienceSeenBy(HttpHeaders requester, Party party) {
    return page(party, requester).get("audience").asString();
  }

  /// 用 SQL 把邮箱标成已验证。
  ///
  /// @param userId 用户 ID
  private void verifyEmail(long userId) {
    jdbcTemplate.update("UPDATE idn_user SET email_verified_at = now() WHERE id = ?", userId);
  }

  /// 一个用例里的两个人。
  ///
  /// @param username 主人的用户名
  /// @param ownerId 主人的用户 ID
  /// @param owner 主人的断言头
  /// @param other 另一个登录用户的断言头
  private record Party(String username, long ownerId, HttpHeaders owner, HttpHeaders other) {}
}
