package dev.patra.identity;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.mail.internet.MimeMessage;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import tools.jackson.databind.JsonNode;

/// 改邮箱的完整流程：申请、确认、撤销、并存的撤销链接、被占用、配额与取消。
@DisplayName("改邮箱的完整流程")
class EmailChangeFlowIT extends IdentityFlowITSupport {

  private static final String CONFIRM_SUBJECT = "确认你的新 Patra 邮箱";
  private static final String NOTICE_SUBJECT = "你的 Patra 邮箱已更改";
  private static final String BOUND_SUBJECT = "本邮箱已绑定你的 Patra 账号";
  private static final String REVERTED_SUBJECT = "你的 Patra 邮箱已改回，请重设密码";

  @Test
  @DisplayName("申请后旧邮箱仍是登录标识、第二次申请 409；确认后立即用新邮箱登录、旧邮箱收到含撤销链接与遮掩新地址的通知、新邮箱收到「已绑定」、会话不动")
  void should_request_confirm_and_switch_login_email() {
    String old = "flow.change.old@example.com";
    String fresh = "flow.change.new@example.com";
    RawResponse registered = register(old, "u-flow-change");
    long userId = userIdOf(registered);
    String sessionToken = sessionTokenOf(registered);
    long sessionId = latestSessionIdOf(userId);

    RawResponse requested =
        post("/me/email/change", Map.of("newEmail", fresh), assertionFor(userId, sessionId));
    assertThat(requested.status()).as(requested.body()).isEqualTo(202);
    assertThat(json(requested).get("newEmail").asString()).isEqualTo(fresh);
    assertThat(json(requested).get("expiresAt").asString())
        .isEqualTo(T0.plus(Duration.ofHours(1)).toString());
    RawResponse pending = get("/me/email/change", assertionFor(userId, sessionId));
    assertThat(pending.status()).isEqualTo(200);
    assertThat(json(pending).get("newEmail").asString()).isEqualTo(fresh);
    assertThat(login(old, PASSWORD).status()).isEqualTo(200);
    assertThat(login(fresh, PASSWORD).status()).isEqualTo(401);
    RawResponse secondRequest =
        post(
            "/me/email/change",
            Map.of("newEmail", "flow.change.other@example.com"),
            assertionFor(userId, sessionId));
    assertThat(secondRequest.status()).isEqualTo(409);
    assertThat(json(secondRequest).get("traits").toString()).contains("EMAIL_CHANGE_PENDING");
    waitForMails(2);
    String confirmToken = tokenInMail(fresh, CONFIRM_SUBJECT);

    RawResponse confirmed =
        post("/auth/email/change/confirm", Map.of("token", confirmToken), noHeaders());

    assertThat(confirmed.status()).as(confirmed.body()).isEqualTo(200);
    assertThat(json(confirmed).get("email").asString()).isEqualTo(fresh);
    JsonNode me = json(get("/auth/me", assertionFor(userId, sessionId)));
    assertThat(me.get("email").asString()).isEqualTo(fresh);
    assertThat(me.get("emailVerified").asBoolean()).isTrue();
    assertThat(redis.hasKey(sessionKey(sessionToken))).as("确认不撤销会话").isTrue();
    assertThat(login(fresh, PASSWORD).status()).isEqualTo(200);
    assertThat(login(old, PASSWORD).status()).isEqualTo(401);
    assertThat(get("/me/email/change", assertionFor(userId, sessionId)).status()).isEqualTo(204);
    waitForMails(4);
    MimeMessage notice = mailFor(old, NOTICE_SUBJECT);
    String noticeText = partText(notice, "text/plain");
    assertThat(noticeText)
        .contains("f***@example.com")
        .doesNotContain(fresh)
        .contains("/revert-email-change#token=")
        .contains("7 天");
    assertThat(mailFor(fresh, BOUND_SUBJECT)).isNotNull();
    assertThat(activeTokenPurposesOf(userId)).containsExactly("EMAIL_CHANGE_REVERT");
  }

  @Test
  @DisplayName("7 天内撤销：邮箱改回、全部会话 401 并记 REVOKED、旧密码不可用、改回的邮箱收到重设链接、重设后能登录；8 天后撤销链接 422")
  void should_revert_within_seven_days_lock_account_and_send_reset_link() {
    String old = "flow.revert.old@example.com";
    String fresh = "flow.revert.new@example.com";
    RawResponse registered = register(old, "u-flow-revert");
    long userId = userIdOf(registered);
    String sessionToken = sessionTokenOf(registered);
    long sessionId = latestSessionIdOf(userId);
    String revertToken = changeEmail(userId, sessionId, fresh, old, 2);
    String freshSessionToken = sessionTokenOf(login(fresh, PASSWORD));
    long freshSessionId = latestSessionIdOf(userId);

    clock.set(T0.plus(Duration.ofDays(6)));
    RawResponse reverted =
        post("/auth/email/change/revert", Map.of("token", revertToken), noHeaders());

    assertThat(reverted.status()).as(reverted.body()).isEqualTo(200);
    assertThat(json(reverted).get("email").asString()).isEqualTo(old);
    assertThat(redis.hasKey(sessionKey(sessionToken))).isFalse();
    assertThat(redis.hasKey(sessionKey(freshSessionToken))).isFalse();
    assertThat(endReasonOf(sessionId)).isEqualTo("REVOKED");
    assertThat(endReasonOf(freshSessionId)).isEqualTo("REVOKED");
    assertThat(json(get("/auth/me", assertionFor(userId, sessionId))).get("email").asString())
        .isEqualTo(old);
    assertThat(login(old, PASSWORD).status()).as("凭据已删").isEqualTo(401);
    assertThat(login(fresh, PASSWORD).status()).isEqualTo(401);
    waitForMails(5);
    String resetToken = tokenInMail(old, REVERTED_SUBJECT);
    assertThat(partText(mailFor(old, REVERTED_SUBJECT), "text/plain"))
        .doesNotContain("/settings/security");
    assertThat(
            post(
                    "/auth/password/reset",
                    Map.of("token", resetToken, "newPassword", "Another-Horse-03"),
                    noHeaders())
                .status())
        .isEqualTo(200);
    assertThat(login(old, "Another-Horse-03").status()).isEqualTo(200);

    clock.set(T0);
    RawResponse expiredSetup = register("flow.revert2.old@example.com", "u-flow-revert2");
    long user2 = userIdOf(expiredSetup);
    // 此前 5 封 + 重设后的「密码已重设」+ 第二个用户的验证邮件
    waitForMails(7);
    String revert2 =
        changeEmail(
            user2,
            latestSessionIdOf(user2),
            "flow.revert2.new@example.com",
            "flow.revert2.old@example.com",
            8);
    clock.set(T0.plus(Duration.ofDays(7)).plusSeconds(1));
    assertThat(post("/auth/email/change/revert", Map.of("token", revert2), noHeaders()).status())
        .isEqualTo(422);
  }

  @Test
  @DisplayName("X → Y，改密码，Y → Z：X 的撤销链接仍能用（改回 X），之后 Y 的撤销链接 422")
  void should_keep_revert_link_alive_through_password_change_and_second_change() {
    String x = "flow.chain.x@example.com";
    String y = "flow.chain.y@example.com";
    String z = "flow.chain.z@example.com";
    RawResponse registered = register(x, "u-flow-chain");
    long userId = userIdOf(registered);
    long sessionId = latestSessionIdOf(userId);
    String revertX = changeEmail(userId, sessionId, y, x, 2);

    RawResponse changed =
        exchange(
            HttpMethod.PUT,
            "/me/password",
            Map.of("currentPassword", PASSWORD, "newPassword", "Another-Horse-03"),
            assertionFor(userId, sessionId));
    assertThat(changed.status()).as(changed.body()).isEqualTo(200);
    waitForMails(5);
    String revertY = changeEmail(userId, sessionId, z, y, 6);
    assertThat(activeTokenPurposesOf(userId))
        .as("两条撤销令牌并存")
        .containsExactly("EMAIL_CHANGE_REVERT", "EMAIL_CHANGE_REVERT");

    RawResponse revertedToX =
        post("/auth/email/change/revert", Map.of("token", revertX), noHeaders());
    assertThat(revertedToX.status()).as(revertedToX.body()).isEqualTo(200);
    assertThat(json(revertedToX).get("email").asString()).isEqualTo(x);
    assertThat(post("/auth/email/change/revert", Map.of("token", revertY), noHeaders()).status())
        .as("邮箱已经回到 X，Y 没有资格再改回去")
        .isEqualTo(422);
    assertThat(activeTokenPurposesOf(userId)).containsExactly("PASSWORD_RESET");
  }

  @Test
  @DisplayName("X → Y → Z：攻击者先点 Y 的撤销链接改回 Y，原主再点 X 的仍然胜出（更早的地址更可信）")
  void should_let_the_earlier_revert_link_win_over_a_later_one() {
    String x = "flow.race.x@example.com";
    String y = "flow.race.y@example.com";
    String z = "flow.race.z@example.com";
    RawResponse registered = register(x, "u-flow-race");
    long userId = userIdOf(registered);
    long sessionId = latestSessionIdOf(userId);
    String revertX = changeEmail(userId, sessionId, y, x, 2);
    String revertY = changeEmail(userId, sessionId, z, y, 5);

    RawResponse revertedToY =
        post("/auth/email/change/revert", Map.of("token", revertY), noHeaders());
    assertThat(revertedToY.status()).as(revertedToY.body()).isEqualTo(200);
    assertThat(json(revertedToY).get("email").asString()).isEqualTo(y);
    assertThat(activeTokenPurposesOf(userId))
        .as("X 的撤销令牌还活着")
        .containsExactlyInAnyOrder("EMAIL_CHANGE_REVERT", "PASSWORD_RESET");

    RawResponse revertedToX =
        post("/auth/email/change/revert", Map.of("token", revertX), noHeaders());
    assertThat(revertedToX.status()).as(revertedToX.body()).isEqualTo(200);
    assertThat(json(revertedToX).get("email").asString()).isEqualTo(x);
    assertThat(login(y, PASSWORD).status()).isEqualTo(401);
    assertThat(activeTokenPurposesOf(userId)).containsExactly("PASSWORD_RESET");
  }

  @Test
  @DisplayName("新邮箱已被别人使用：409 errors[0].field 是 email、不发信、没有待确认；改密码后待确认的改邮箱消失")
  void should_reject_taken_email_with_409_and_leave_no_pending_change() {
    RawResponse alice = register("flow.taken.alice@example.com", "u-flow-taken-alice");
    register("flow.taken.bob@example.com", "u-flow-taken-bob");
    long aliceId = userIdOf(alice);
    long aliceSession = latestSessionIdOf(aliceId);
    waitForMails(2);

    RawResponse taken =
        post(
            "/me/email/change",
            Map.of("newEmail", "flow.taken.bob@example.com"),
            assertionFor(aliceId, aliceSession));

    assertThat(taken.status()).as(taken.body()).isEqualTo(409);
    JsonNode error = json(taken).get("errors").get(0);
    assertThat(error.get("field").asString()).isEqualTo("email");
    assertThat(error.get("code").asString()).isEqualTo("ALREADY_REGISTERED");
    assertThat(get("/me/email/change", assertionFor(aliceId, aliceSession)).status())
        .isEqualTo(204);
    assertThat(greenMail.getReceivedMessages()).hasSize(2);

    assertThat(
            post(
                    "/me/email/change",
                    Map.of("newEmail", "flow.taken.free@example.com"),
                    assertionFor(aliceId, aliceSession))
                .status())
        .isEqualTo(202);
    assertThat(get("/me/email/change", assertionFor(aliceId, aliceSession)).status())
        .isEqualTo(200);
    assertThat(
            exchange(
                    HttpMethod.PUT,
                    "/me/password",
                    Map.of("currentPassword", PASSWORD, "newPassword", "Another-Horse-03"),
                    assertionFor(aliceId, aliceSession))
                .status())
        .isEqualTo(200);
    assertThat(get("/me/email/change", assertionFor(aliceId, aliceSession)).status())
        .as("改密码批量作废令牌，待确认的改邮箱随之消失")
        .isEqualTo(204);
  }

  @Test
  @DisplayName("取消后可再申请；每天第 4 次申请 429 带 Retry-After；取消后 GET 204")
  void should_limit_requests_to_three_per_day_and_reject_second_pending() {
    RawResponse registered = register("flow.quota@example.com", "u-flow-quota");
    long userId = userIdOf(registered);
    long sessionId = latestSessionIdOf(userId);
    HttpHeaders assertion = assertionFor(userId, sessionId);

    for (int i = 1; i <= 3; i++) {
      RawResponse requested =
          post(
              "/me/email/change",
              Map.of("newEmail", "flow.quota." + i + "@example.com"),
              assertion);
      assertThat(requested.status()).as("第 %d 次", i).isEqualTo(202);
      assertThat(get("/me/email/change", assertion).status()).isEqualTo(200);
      assertThat(exchange(HttpMethod.DELETE, "/me/email/change", null, assertion).status())
          .isEqualTo(204);
      assertThat(get("/me/email/change", assertion).status()).isEqualTo(204);
    }
    RawResponse fourth =
        post("/me/email/change", Map.of("newEmail", "flow.quota.4@example.com"), assertion);
    assertThat(fourth.status()).as(fourth.body()).isEqualTo(429);
    assertThat(fourth.header(HttpHeaders.RETRY_AFTER)).isNotNull();
    assertThat(json(fourth).get("detail").asString()).isEqualTo("今天更改邮箱的次数已用完，请明天再试");
  }

  /// 申请并确认一次改邮箱，返回旧邮箱收到的撤销令牌。
  ///
  /// @param userId 用户 ID
  /// @param sessionId 会话 ID（再认证窗口内）
  /// @param fresh 新邮箱
  /// @param old 旧邮箱
  /// @param mailsAfterRequest 申请后累计应收到的邮件数
  /// @return 撤销令牌
  private String changeEmail(
      long userId, long sessionId, String fresh, String old, int mailsAfterRequest) {
    RawResponse requested =
        post("/me/email/change", Map.of("newEmail", fresh), assertionFor(userId, sessionId));
    assertThat(requested.status()).as(requested.body()).isEqualTo(202);
    waitForMails(mailsAfterRequest);
    RawResponse confirmed =
        post(
            "/auth/email/change/confirm",
            Map.of("token", tokenInMail(fresh, CONFIRM_SUBJECT)),
            noHeaders());
    assertThat(confirmed.status()).as(confirmed.body()).isEqualTo(200);
    waitForMails(mailsAfterRequest + 2);
    return tokenInMail(old, NOTICE_SUBJECT);
  }
}
