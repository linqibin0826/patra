package dev.patra.notification.kernel;

import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.notification.app.kernel.usecase.cleanup.CleanupDeletedUsersCommand;
import dev.patra.notification.app.kernel.usecase.cleanup.CleanupResult;
import dev.patra.notification.support.NotificationFlowITSupport;
import dev.patra.tool.cqrs.CommandBus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;

/// 注销清理的底座流程：各子域登记的个人数据（现在只有 preference 的偏好与退订令牌）对已注销用户删除，别人的
/// 不动；identity 不认识的不删；一批只回查一次 `/summaries`。
class DeletedUserCleanupFlowIT extends NotificationFlowITSupport {

  private static final long GONE = 7L;
  private static final long ACTIVE = 8L;
  private static final long UNKNOWN = 9L;

  @Autowired private CommandBus commandBus;

  /// 三人都有偏好行：已注销的被删、旧的退订链接随之无效；正常的与 identity 不认识的保留。
  @Test
  void should_delete_preferences_of_deleted_users_only() {
    for (long user : new long[] {GONE, ACTIVE, UNKNOWN}) {
      call(HttpMethod.PUT, user, "/me/notification-preferences/MENTION", "{\"mode\":\"OFF\"}");
    }
    String goneToken = tokenOf(GONE);
    identity.stubFor(
        post(urlPathEqualTo("/_internal/users/summaries"))
            .willReturn(
                okJson(
                    "{\"users\":["
                        + summary(GONE, "DELETED")
                        + ","
                        + summary(ACTIVE, "ACTIVE")
                        + "]}")));

    CleanupResult result = commandBus.handle(CleanupDeletedUsersCommand.of());

    assertThat(result).isEqualTo(CleanupResult.of(3, 1, 1, 0));
    assertThat(rows(GONE)).isZero();
    assertThat(rows(ACTIVE)).isEqualTo(1L);
    assertThat(rows(UNKNOWN)).isEqualTo(1L);
    identity.verify(1, postRequestedFor(urlPathEqualTo("/_internal/users/summaries")));
    assertThat(status(form("/unsubscribe/one-click?token=" + goneToken + "&category=REPLY", null)))
        .isEqualTo(404);
  }

  /// 信箱与在站记录也接进清理：已注销的人作为收件人的提醒、他的在站记录被删，别人的保留。
  @Test
  void should_delete_inbox_and_presence_of_deleted_users() {
    jdbcTemplate.update(
        "INSERT INTO inbox.notification (recipient_id, notification_id, category, type, actor_id,"
            + " actor_anonymous, subject_id, comment_id, occurred_at, created_at)"
            + " VALUES (?, 1, 'REPLY', 'COMMENT_REPLY', 3, false, 40, 500, now(), now()),"
            + " (?, 2, 'REPLY', 'COMMENT_REPLY', 3, false, 40, 500, now(), now())",
        GONE,
        ACTIVE);
    jdbcTemplate.update(
        "INSERT INTO kernel.presence (user_id, last_seen_at) VALUES (?, now()), (?, now())",
        GONE,
        ACTIVE);
    identity.stubFor(
        post(urlPathEqualTo("/_internal/users/summaries"))
            .willReturn(
                okJson(
                    "{\"users\":["
                        + summary(GONE, "DELETED")
                        + ","
                        + summary(ACTIVE, "ACTIVE")
                        + "]}")));

    CleanupResult result = commandBus.handle(CleanupDeletedUsersCommand.of());

    assertThat(result.failedBatches()).isZero();
    assertThat(jdbcTemplate.queryForList("SELECT recipient_id FROM inbox.notification", Long.class))
        .containsExactly(ACTIVE);
    assertThat(jdbcTemplate.queryForList("SELECT user_id FROM kernel.presence", Long.class))
        .containsExactly(ACTIVE);
  }

  /// identity 不可用：这一批计失败，什么都不删。
  @Test
  void should_keep_everything_when_identity_down() {
    call(HttpMethod.PUT, GONE, "/me/notification-preferences/MENTION", "{\"mode\":\"OFF\"}");
    identityDown();

    CleanupResult result = commandBus.handle(CleanupDeletedUsersCommand.of());

    assertThat(result.failedBatches()).isEqualTo(1);
    assertThat(rows(GONE)).isEqualTo(1L);
  }

  /// identity 批量用户摘要里的一条：只有清理用到的状态，其余字段给默认值。
  ///
  /// @param userId 用户 ID
  /// @param status 账号状态
  /// @return JSON
  private static String summary(long userId, String status) {
    return "{\"userId\":\""
        + userId
        + "\",\"accountStatus\":\""
        + status
        + "\",\"avatarPalette\":0,\"emailVerified\":false,\"profileVisible\":false}";
  }

  /// 这个人的偏好行数。
  ///
  /// @param userId 用户 ID
  /// @return 行数
  private long rows(long userId) {
    return jdbcTemplate.queryForObject(
        "SELECT count(*) FROM preference.email_preference WHERE user_id = ?", Long.class, userId);
  }

  /// 这个人的退订令牌。
  ///
  /// @param userId 用户 ID
  /// @return 令牌
  private String tokenOf(long userId) {
    return jdbcTemplate.queryForObject(
        "SELECT unsubscribe_token FROM preference.email_preference WHERE user_id = ?",
        String.class,
        userId);
  }
}
