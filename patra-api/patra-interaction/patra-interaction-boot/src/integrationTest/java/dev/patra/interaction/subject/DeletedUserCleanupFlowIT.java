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
