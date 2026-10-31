package dev.patra.identity;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.identity.api.account.event.AccountDeletionCancelledMailEvent;
import dev.patra.identity.api.account.event.AccountDeletionCompletedMailEvent;
import dev.patra.identity.api.account.event.AccountDeletionReminderMailEvent;
import dev.patra.identity.api.account.event.AccountDeletionRequestedMailEvent;
import dev.patra.identity.api.account.event.EmailChangeConfirmMailEvent;
import dev.patra.identity.api.account.event.EmailChangedNoticeMailEvent;
import dev.patra.identity.app.account.usecase.deletion.DeletionRunResult;
import dev.patra.identity.app.account.usecase.deletion.ExecuteDueAccountDeletionsCommand;
import dev.patra.identity.app.account.usecase.deletion.ReminderRunResult;
import dev.patra.identity.app.account.usecase.deletion.SendAccountDeletionRemindersCommand;
import dev.patra.tool.cqrs.CommandBus;
import dev.patra.tool.objectstorage.ObjectStorageOperations;
import dev.patra.tool.objectstorage.domain.ObjectMetadata;
import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;

/// 注销的完整流程：发起（再认证、用户名确认）、冷静期内登录与取消、到期前提醒只发一次、到期匿名化与邮箱释放。
///
/// 两个任务扫的是全库，同一个库里别的用例留下的冷静期用户也会被处理，所以对任务结果只断言「至少」与「重跑为零」，
/// 对用户本身逐项断言。
@DisplayName("注销的完整流程")
class AccountDeletionFlowIT extends IdentityFlowITSupport {

  private static final Duration COOLING = Duration.ofDays(15);

  private static final String AVATAR_BUCKET = "patra-identity-avatar";

  @Autowired CommandBus commandBus;
  @Autowired ObjectStorageOperations avatarStorage;

  @Test
  @DisplayName("发起：超窗 403、用户名不符 422；成功后全部会话下线、主页 404、令牌作废但撤销链接仍有效、登记「注销已提交」")
  void should_request_deletion_after_reauth_and_username_check() {
    String email = "flow.del.req@example.com";
    RawResponse registered = register(email, "u-del-req");
    long userId = userIdOf(registered);
    String tokenA = sessionTokenOf(registered);
    long sessionA = latestSessionIdOf(userId);
    RawResponse second = login(email, PASSWORD);
    String tokenB = sessionTokenOf(second);
    long sessionB = latestSessionIdOf(userId);
    jdbcTemplate.update("UPDATE idn_user SET email_verified_at = now() WHERE id = ?", userId);
    assertThat(get("/users/u-del-req", new HttpHeaders()).status()).isEqualTo(200);
    RawResponse change =
        post(
            "/me/email/change",
            Map.of("newEmail", "flow.del.req.new@example.com"),
            assertionFor(userId, sessionA));
    assertThat(change.status()).as(change.body()).isEqualTo(202);
    String confirmToken =
        tokenIn(
            mailEvent(EmailChangeConfirmMailEvent.class, "flow.del.req.new@example.com")
                .confirmUrl());

    clock.advance(Duration.ofMinutes(11));
    RawResponse stale = deletion(userId, sessionA, "u-del-req");
    assertThat(stale.status()).as(stale.body()).isEqualTo(403);
    assertThat(json(stale).get("traits").toString()).contains("REAUTH_REQUIRED");
    clock.set(T0);
    RawResponse mismatch = deletion(userId, sessionA, "nope");
    assertThat(mismatch.status()).isEqualTo(422);
    assertThat(json(mismatch).get("errors").get(0).get("code").asString()).isEqualTo("MISMATCH");

    RawResponse requested = deletion(userId, sessionA, " U-Del-Req ");

    assertThat(requested.status()).as(requested.body()).isEqualTo(200);
    assertThat(json(requested).get("deletionDueAt").asString())
        .isEqualTo(T0.plus(COOLING).toString());
    assertThat(redis.hasKey(sessionKey(tokenA))).isFalse();
    assertThat(redis.hasKey(sessionKey(tokenB))).isFalse();
    assertThat(endReasonOf(sessionA)).isEqualTo("DELETION_REQUESTED");
    assertThat(endReasonOf(sessionB)).isEqualTo("DELETION_REQUESTED");
    assertThat(get("/users/u-del-req", new HttpHeaders()).status()).isEqualTo(404);
    assertThat(activeTokenPurposesOf(userId)).isEmpty();
    assertThat(
            post("/auth/email/change/confirm", Map.of("token", confirmToken), noHeaders()).status())
        .isEqualTo(422);
    assertThat(mailEvent(AccountDeletionRequestedMailEvent.class, email).deletionDueAt())
        .isEqualTo(T0.plus(COOLING));
  }

  @Test
  @DisplayName("发起时改邮箱的撤销令牌保留：旧邮箱的「不是我」在冷静期内仍然有效")
  void should_keep_revert_link_alive_after_request() {
    String oldEmail = "flow.del.revert@example.com";
    String newEmail = "flow.del.revert.new@example.com";
    RawResponse registered = register(oldEmail, "u-del-revert");
    long userId = userIdOf(registered);
    long sessionId = latestSessionIdOf(userId);
    post("/me/email/change", Map.of("newEmail", newEmail), assertionFor(userId, sessionId));
    String confirmToken =
        tokenIn(mailEvent(EmailChangeConfirmMailEvent.class, newEmail).confirmUrl());
    assertThat(
            post("/auth/email/change/confirm", Map.of("token", confirmToken), noHeaders()).status())
        .isEqualTo(200);
    String revertToken =
        tokenIn(mailEvent(EmailChangedNoticeMailEvent.class, oldEmail).revertUrl());

    assertThat(deletion(userId, sessionId, "u-del-revert").status()).isEqualTo(200);

    assertThat(activeTokenPurposesOf(userId)).containsExactly("EMAIL_CHANGE_REVERT");
    RawResponse reverted =
        post("/auth/email/change/revert", Map.of("token", revertToken), noHeaders());
    assertThat(reverted.status()).as(reverted.body()).isEqualTo(200);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT status FROM idn_user WHERE id = ?", String.class, userId))
        .isEqualTo("PENDING_DELETION");
  }

  @Test
  @DisplayName("冷静期内：登录拿到 PENDING_DELETION、写接口 409、再发起 409；取消后恢复 ACTIVE、写接口恢复、登记「已取消」")
  void should_allow_login_and_cancel_during_cooling_period() {
    String email = "flow.del.cancel@example.com";
    RawResponse registered = register(email, "u-del-cancel");
    long userId = userIdOf(registered);
    assertThat(deletion(userId, latestSessionIdOf(userId), "u-del-cancel").status()).isEqualTo(200);

    RawResponse loggedIn = login(email, PASSWORD);
    assertThat(loggedIn.status()).isEqualTo(200);
    assertThat(json(loggedIn).get("user").get("status").asString()).isEqualTo("PENDING_DELETION");
    long sessionId = latestSessionIdOf(userId);
    HttpHeaders auth = assertionFor(userId, sessionId);
    assertThat(json(get("/auth/me", auth)).get("status").asString()).isEqualTo("PENDING_DELETION");
    RawResponse blocked = exchange(HttpMethod.PUT, "/me/profile", Map.of(), auth);
    assertThat(blocked.status()).isEqualTo(409);
    assertThat(json(blocked).get("traits").toString()).contains("PENDING_DELETION");
    assertThat(deletion(userId, sessionId, "u-del-cancel").status()).isEqualTo(409);

    RawResponse cancelled = exchange(HttpMethod.DELETE, "/me/deletion", null, auth);

    assertThat(cancelled.status()).as(cancelled.body()).isEqualTo(200);
    assertThat(json(cancelled).get("status").asString()).isEqualTo("ACTIVE");
    assertThat(exchange(HttpMethod.PUT, "/me/profile", Map.of(), auth).status()).isEqualTo(200);
    mailEvent(AccountDeletionCancelledMailEvent.class, email);
  }

  @Test
  @DisplayName("到期前 3 天提醒只登记一次：任务跑两次，第二次一个都不登记")
  void should_send_reminder_once() {
    String email = "flow.del.remind@example.com";
    long userId = userIdOf(register(email, "u-del-remind"));
    assertThat(deletion(userId, latestSessionIdOf(userId), "u-del-remind").status()).isEqualTo(200);
    clock.set(T0.plus(Duration.ofDays(12)));

    ReminderRunResult first = commandBus.handle(SendAccountDeletionRemindersCommand.of());
    ReminderRunResult second = commandBus.handle(SendAccountDeletionRemindersCommand.of());

    assertThat(first.sent()).isGreaterThanOrEqualTo(1);
    assertThat(second).isEqualTo(ReminderRunResult.of(0, 0));
    List<AccountDeletionReminderMailEvent> reminders =
        securityMails.all(AccountDeletionReminderMailEvent.class, email);
    assertThat(reminders).hasSize(1);
    assertThat(reminders.getFirst().daysLeft()).isEqualTo(3);
  }

  @Test
  @DisplayName("到期执行：已注销、邮箱空、行保留，凭据/链接/关注领域/令牌/头像对象没了、资料行全空；冷静期会话下线；邮箱可重新注册，用户名不行")
  void should_anonymize_on_due_and_free_email_but_not_username() {
    String email = "flow.del.done@example.com";
    RawResponse registered = register(email, "u-del-done");
    long userId = userIdOf(registered);
    long sessionId = latestSessionIdOf(userId);
    RawResponse profile =
        exchange(
            HttpMethod.PUT,
            "/me/profile",
            Map.of(
                "displayName", "陈宇",
                "bio", "心内科医生",
                "links", List.of(Map.of("title", "实验室", "url", "https://lab.example"))),
            assertionFor(userId, sessionId));
    assertThat(profile.status()).as(profile.body()).isEqualTo(200);
    jdbcTemplate.update(
        "INSERT INTO idn_user_research_interest (id, user_id, position, mesh_ui, mesh_name)"
            + " VALUES (?, ?, 0, 'D006333', 'Heart Failure')",
        userId + 1,
        userId);
    String avatarKey = "avatar/" + userId + "/0123456789abcdef-512.webp";
    for (String key : List.of(avatarKey, "avatar/" + userId + "/0123456789abcdef-128.webp")) {
      avatarStorage.upload(
          AVATAR_BUCKET,
          key,
          new ByteArrayInputStream(new byte[] {1}),
          ObjectMetadata.builder().contentLength(1).contentType("image/webp").build());
    }
    jdbcTemplate.update(
        "UPDATE idn_user_profile SET avatar_object_key = ? WHERE user_id = ?", avatarKey, userId);
    assertThat(deletion(userId, sessionId, "u-del-done").status()).isEqualTo(200);
    clock.set(T0.plus(COOLING));
    RawResponse coolingLogin = login(email, PASSWORD);
    String coolingToken = sessionTokenOf(coolingLogin);
    long coolingSession = latestSessionIdOf(userId);

    DeletionRunResult first = commandBus.handle(ExecuteDueAccountDeletionsCommand.of());
    DeletionRunResult second = commandBus.handle(ExecuteDueAccountDeletionsCommand.of());

    assertThat(first.completed()).isGreaterThanOrEqualTo(1);
    assertThat(second).isEqualTo(DeletionRunResult.of(0, 0));
    Map<String, Object> user =
        jdbcTemplate.queryForMap("SELECT status, email FROM idn_user WHERE id = ?", userId);
    assertThat(user.get("status")).isEqualTo("DELETED");
    assertThat(user.get("email")).isNull();
    for (String table :
        List.of(
            "idn_user_password_credential",
            "idn_user_profile_link",
            "idn_user_research_interest",
            "idn_email_token")) {
      assertThat(countByUser(table, userId)).as(table).isZero();
    }
    Map<String, Object> erased =
        jdbcTemplate.queryForMap(
            "SELECT display_name, bio, profile_visibility, professional_visibility, search_indexing"
                + " FROM idn_user_profile WHERE user_id = ?",
            userId);
    assertThat(erased.get("display_name")).isNull();
    assertThat(erased.get("bio")).isNull();
    assertThat(erased.get("profile_visibility")).isEqualTo("PUBLIC");
    assertThat(erased.get("professional_visibility")).isEqualTo("PUBLIC");
    assertThat(erased.get("search_indexing")).isEqualTo(false);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT avatar_object_key FROM idn_user_profile WHERE user_id = ?",
                String.class,
                userId))
        .isNull();
    assertThat(avatarStorage.statObject(AVATAR_BUCKET, avatarKey)).isEmpty();
    assertThat(avatarStorage.deleteByPrefix(AVATAR_BUCKET, "avatar/" + userId + "/")).isZero();
    assertThat(redis.hasKey(sessionKey(coolingToken))).isFalse();
    assertThat(endReasonOf(coolingSession)).isEqualTo("DELETION_COMPLETED");
    List<AccountDeletionCompletedMailEvent> completed =
        securityMails.all(AccountDeletionCompletedMailEvent.class, email);
    assertThat(completed).as("「已注销」发到注销前的邮箱").hasSize(1);
    assertThat(completed.getFirst().username()).isEqualTo("u-del-done");
    assertThat(login(email, PASSWORD).status()).isEqualTo(401);
    RawResponse reborn =
        post(
            "/auth/register",
            Map.of(
                "email",
                "Flow.Del.Done@Example.com",
                "username",
                "u-del-reborn",
                "password",
                PASSWORD),
            new HttpHeaders());
    assertThat(reborn.status()).as(reborn.body()).isEqualTo(201);
    assertThat(userIdOf(reborn)).isNotEqualTo(userId);
    RawResponse taken =
        post(
            "/auth/register",
            Map.of(
                "email",
                "flow.del.other@example.com",
                "username",
                "u-del-done",
                "password",
                PASSWORD),
            new HttpHeaders());
    assertThat(taken.status()).isEqualTo(409);
    assertThat(json(taken).get("errors").toString()).contains("ALREADY_TAKEN");
  }

  /// 带断言发起注销。
  ///
  /// @param userId 用户 ID
  /// @param sessionId 会话 ID
  /// @param username 输入的用户名
  /// @return 响应
  private RawResponse deletion(long userId, long sessionId, String username) {
    return post("/me/deletion", Map.of("username", username), assertionFor(userId, sessionId));
  }

  /// 某张表里某个用户的行数。
  ///
  /// @param table 表名
  /// @param userId 用户 ID
  /// @return 行数
  private int countByUser(String table, long userId) {
    return jdbcTemplate.queryForObject(
        "SELECT count(*) FROM " + table + " WHERE user_id = ?", Integer.class, userId);
  }
}
