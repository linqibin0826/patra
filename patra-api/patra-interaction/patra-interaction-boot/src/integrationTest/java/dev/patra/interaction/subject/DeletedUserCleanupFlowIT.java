package dev.patra.interaction.subject;

import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.interaction.app.subject.usecase.cleanup.CleanupDeletedUsersCommand;
import dev.patra.interaction.app.subject.usecase.cleanup.CleanupResult;
import dev.patra.interaction.support.InteractionFlowITSupport;
import dev.patra.tool.cqrs.CommandBus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.client.EntityExchangeResult;

/// 注销清理的公共流程（决策 H）：各子域登记的个人数据（收藏、发表计数）对已注销用户一并删除，别人的不动；
/// 候选用户来自各子域的并集，一批只回查一次 identity。
class DeletedUserCleanupFlowIT extends InteractionFlowITSupport {

  private static final long A = 2001L;
  private static final long B = 2002L;
  private static final long C = 2003L;

  @Autowired private CommandBus commandBus;

  /// A 有收藏和评论计数，B 只有经验计数，C 有收藏且正常：清理 A、B 在各子域的全部数据，C 不动。
  @Test
  void should_clean_every_participant_for_deleted_users_only() {
    CATALOG_DATA.put(100L, "1", "Lancet", "2019");
    assertThat(status(call(HttpMethod.PUT, A, "/me/library/publication/100", null))).isEqualTo(200);
    assertThat(status(call(HttpMethod.PUT, C, "/me/library/publication/100", null))).isEqualTo(200);
    seedQuota(A, "COMMENT");
    seedQuota(B, "POST");
    IDENTITY_DATA.put(A, "DELETED", false, "PUBLIC");
    IDENTITY_DATA.put(B, "DELETED", false, "PUBLIC");
    IDENTITY_DATA.put(C, "ACTIVE", true, "PUBLIC");
    identity.resetRequests();

    CleanupResult result = commandBus.handle(CleanupDeletedUsersCommand.of());

    assertThat(result.scannedUsers()).isEqualTo(3);
    assertThat(result.deletedUsers()).isEqualTo(2);
    assertThat(result.failedBatches()).isZero();
    assertThat(count("SELECT count(*) FROM collection.library_item WHERE user_id = ?", A)).isZero();
    assertThat(count("SELECT count(*) FROM subject.publish_quota WHERE user_id IN (?, ?)", A, B))
        .isZero();
    assertThat(count("SELECT count(*) FROM collection.library_item WHERE user_id = ?", C))
        .isEqualTo(1L);
    identity.verify(1, postRequestedFor(urlEqualTo("/_internal/users/summaries")));
  }

  /// AC（PAP-133）：注销清理删掉 A 的文献库条目后，这篇的收藏人数从 2 减到 1，批量对象统计随之变化。
  @Test
  void should_decrement_collector_counts_when_cleaning_deleted_users() {
    CATALOG_DATA.put(100L, "1", "Lancet", "2019");
    assertThat(status(call(HttpMethod.PUT, A, "/me/library/publication/100", null))).isEqualTo(200);
    assertThat(status(call(HttpMethod.PUT, C, "/me/library/publication/100", null))).isEqualTo(200);
    assertThat(collectorCountOf100()).isEqualTo(2);
    IDENTITY_DATA.put(A, "DELETED", false, "PUBLIC");
    IDENTITY_DATA.put(C, "ACTIVE", true, "PUBLIC");

    commandBus.handle(CleanupDeletedUsersCommand.of());

    assertThat(collectorCountOf100()).isEqualTo(1);
  }

  /// 匿名调批量对象统计，取文献 100 的收藏人数。
  ///
  /// @return 收藏人数
  private int collectorCountOf100() {
    EntityExchangeResult<String> result =
        call(
            HttpMethod.POST,
            null,
            "/subjects/stats",
            "{\"type\":\"publication\",\"keys\":[\"100\"]}");
    assertThat(status(result)).as(result.getResponseBody()).isEqualTo(200);
    return json(result).get("items").get(0).get("collectorCount").asInt();
  }

  /// 有帮助不接入注销清理（PAP-131）：两个注销用户互相点过的有帮助、两人的获赞在清理后原样保留。
  @Test
  void should_keep_helpful_data_of_deleted_users() {
    CATALOG_DATA.put(100L, "1", "Lancet", "2019");
    IDENTITY_DATA.put(A, "ACTIVE", true, "PUBLIC");
    IDENTITY_DATA.put(B, "ACTIVE", true, "PUBLIC");
    long fromA = comment(A, "A 的评论");
    long fromB = comment(B, "B 的评论");
    assertThat(status(call(HttpMethod.PUT, A, "/comments/" + fromB + "/helpful", null)))
        .isEqualTo(200);
    assertThat(status(call(HttpMethod.PUT, B, "/comments/" + fromA + "/helpful", null)))
        .isEqualTo(200);
    IDENTITY_DATA.put(A, "DELETED", false, "PUBLIC");
    IDENTITY_DATA.put(B, "DELETED", false, "PUBLIC");

    CleanupResult result = commandBus.handle(CleanupDeletedUsersCommand.of());

    assertThat(result.deletedUsers()).isEqualTo(2);
    assertThat(count("SELECT count(*) FROM reaction.helpful")).isEqualTo(2L);
    assertThat(count("SELECT sum(helpful_count) FROM reaction.target_stats")).isEqualTo(2L);
    assertThat(count("SELECT sum(helpful_received) FROM reaction.user_stats")).isEqualTo(2L);
    assertThat(count("SELECT count(*) FROM reaction.user_stats WHERE user_id IN (?, ?)", A, B))
        .isEqualTo(2L);
  }

  /// 在文献 100 下发一条根评论，回它的 ID。
  ///
  /// @param user 作者
  /// @param body 正文
  /// @return 评论 ID
  private long comment(long user, String body) {
    EntityExchangeResult<String> result =
        call(
            HttpMethod.POST,
            user,
            "/discussions/publication/100/comments",
            "{\"body\": \"" + body + "\"}");
    assertThat(status(result)).as(result.getResponseBody()).isEqualTo(201);
    return Long.parseLong(json(result).get("commentId").asString());
  }

  /// 预写一行发表计数。
  ///
  /// @param userId 作者
  /// @param kind 种类
  private void seedQuota(long userId, String kind) {
    jdbcTemplate.update(
        "INSERT INTO subject.publish_quota (user_id, kind, quota_day, day_count, last_published_at)"
            + " VALUES (?, ?, CURRENT_DATE, 1, now())",
        userId,
        kind);
  }
}
