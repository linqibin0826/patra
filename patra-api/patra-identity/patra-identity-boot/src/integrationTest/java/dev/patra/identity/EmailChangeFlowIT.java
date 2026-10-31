package dev.patra.identity;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.identity.api.account.event.EmailChangeConfirmMailEvent;
import dev.patra.identity.api.account.event.EmailChangeRevertedMailEvent;
import dev.patra.identity.api.account.event.EmailChangedConfirmationMailEvent;
import dev.patra.identity.api.account.event.EmailChangedNoticeMailEvent;
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

  @Test
  @DisplayName("申请后旧邮箱仍是登录标识、第二次申请 409；确认信发到新邮箱；确认后立即用新邮箱登录、变更通知（撤销链接、遮掩的新地址）只发旧邮箱、「已绑定」只发新邮箱、会话不动")
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
    EmailChangeConfirmMailEvent confirmMail = mailEvent(EmailChangeConfirmMailEvent.class, fresh);
    assertThat(confirmMail.linkExpiresAt()).isEqualTo(T0.plus(Duration.ofHours(1)));
    String confirmToken = tokenIn(confirmMail.confirmUrl());

    RawResponse confirmed =
        post(
            "/auth/email/change/confirm",
            Map.of("token", confirmToken),
            fromShenzhenChrome(noHeaders()));

    assertThat(confirmed.status()).as(confirmed.body()).isEqualTo(200);
    assertThat(json(confirmed).get("email").asString()).isEqualTo(fresh);
    JsonNode me = json(get("/auth/me", assertionFor(userId, sessionId)));
    assertThat(me.get("email").asString()).isEqualTo(fresh);
    assertThat(me.get("emailVerified").asBoolean()).isTrue();
    assertThat(redis.hasKey(sessionKey(sessionToken))).as("确认不撤销会话").isTrue();
    assertThat(login(fresh, PASSWORD).status()).isEqualTo(200);
    assertThat(login(old, PASSWORD).status()).isEqualTo(401);
    assertThat(get("/me/email/change", assertionFor(userId, sessionId)).status()).isEqualTo(204);
    EmailChangedNoticeMailEvent notice = mailEvent(EmailChangedNoticeMailEvent.class, old);
    assertThat(notice.newEmailMasked()).isEqualTo("f***@example.com");
    assertThat(notice.revertUrl()).contains("/revert-email-change#token=").doesNotContain(fresh);
    assertThat(notice.linkExpiresAt()).isEqualTo(T0.plus(Duration.ofDays(7)));
    assertThat(notice.context().device()).isEqualTo("Chrome 141 · macOS");
    assertThat(notice.context().ipAddress()).isEqualTo("113.92.157.29");
    assertThat(notice.context().ipLocation()).isEqualTo("中国 广东 深圳");
    assertThat(mailEvent(EmailChangedConfirmationMailEvent.class, fresh).context().device())
        .isEqualTo("Chrome 141 · macOS");
    assertThat(securityMails.all(EmailChangedNoticeMailEvent.class, fresh))
        .as("变更通知不发新邮箱")
        .isEmpty();
    assertThat(securityMails.all(EmailChangedConfirmationMailEvent.class, old))
        .as("「已绑定」不发旧邮箱")
        .isEmpty();
    assertThat(activeTokenPurposesOf(userId)).containsExactly("EMAIL_CHANGE_REVERT");
  }

  @Test
  @DisplayName("7 天内撤销：邮箱改回、全部会话 401 并记 EMAIL_REVERTED、旧密码不可用、改回的邮箱收到重设链接、重设后能登录；8 天后撤销链接 422")
  void should_revert_within_seven_days_lock_account_and_send_reset_link() {
    String old = "flow.revert.old@example.com";
    String fresh = "flow.revert.new@example.com";
    RawResponse registered = register(old, "u-flow-revert");
    long userId = userIdOf(registered);
    String sessionToken = sessionTokenOf(registered);
    long sessionId = latestSessionIdOf(userId);
    String revertToken = changeEmail(userId, sessionId, fresh, old);
    String freshSessionToken = sessionTokenOf(login(fresh, PASSWORD));
    long freshSessionId = latestSessionIdOf(userId);

    clock.set(T0.plus(Duration.ofDays(6)));
    RawResponse reverted =
        post("/auth/email/change/revert", Map.of("token", revertToken), noHeaders());

    assertThat(reverted.status()).as(reverted.body()).isEqualTo(200);
    assertThat(json(reverted).get("email").asString()).isEqualTo(old);
    assertThat(redis.hasKey(sessionKey(sessionToken))).isFalse();
    assertThat(redis.hasKey(sessionKey(freshSessionToken))).isFalse();
    assertThat(endReasonOf(sessionId)).isEqualTo("EMAIL_REVERTED");
    assertThat(endReasonOf(freshSessionId)).isEqualTo("EMAIL_REVERTED");
    assertThat(json(get("/auth/me", assertionFor(userId, sessionId))).get("email").asString())
        .isEqualTo(old);
    assertThat(login(old, PASSWORD).status()).as("凭据已删").isEqualTo(401);
    assertThat(login(fresh, PASSWORD).status()).isEqualTo(401);
    String resetToken = tokenIn(mailEvent(EmailChangeRevertedMailEvent.class, old).resetUrl());
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
    String revert2 =
        changeEmail(
            user2,
            latestSessionIdOf(user2),
            "flow.revert2.new@example.com",
            "flow.revert2.old@example.com");
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
    String revertX = changeEmail(userId, sessionId, y, x);

    RawResponse changed =
        exchange(
            HttpMethod.PUT,
            "/me/password",
            Map.of("currentPassword", PASSWORD, "newPassword", "Another-Horse-03"),
            assertionFor(userId, sessionId));
    assertThat(changed.status()).as(changed.body()).isEqualTo(200);
    String revertY = changeEmail(userId, sessionId, z, y);
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
    String revertX = changeEmail(userId, sessionId, y, x);
    String revertY = changeEmail(userId, sessionId, z, y);

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
  @DisplayName("新邮箱已被别人使用：409 errors[0].field 是 email、不登记确认信、没有待确认；改密码后待确认的改邮箱消失")
  void should_reject_taken_email_with_409_and_leave_no_pending_change() {
    RawResponse alice = register("flow.taken.alice@example.com", "u-flow-taken-alice");
    register("flow.taken.bob@example.com", "u-flow-taken-bob");
    long aliceId = userIdOf(alice);
    long aliceSession = latestSessionIdOf(aliceId);

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
    assertThat(securityMails.all(EmailChangeConfirmMailEvent.class, "flow.taken.bob@example.com"))
        .as("被占用的地址不收确认信")
        .isEmpty();

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

  /// 申请并确认一次改邮箱，返回发往旧邮箱的变更通知里的撤销令牌。
  ///
  /// @param userId 用户 ID
  /// @param sessionId 会话 ID（再认证窗口内）
  /// @param fresh 新邮箱
  /// @param old 旧邮箱
  /// @return 撤销令牌
  private String changeEmail(long userId, long sessionId, String fresh, String old) {
    RawResponse requested =
        post("/me/email/change", Map.of("newEmail", fresh), assertionFor(userId, sessionId));
    assertThat(requested.status()).as(requested.body()).isEqualTo(202);
    RawResponse confirmed =
        post(
            "/auth/email/change/confirm",
            Map.of(
                "token", tokenIn(mailEvent(EmailChangeConfirmMailEvent.class, fresh).confirmUrl())),
            noHeaders());
    assertThat(confirmed.status()).as(confirmed.body()).isEqualTo(200);
    return tokenIn(mailEvent(EmailChangedNoticeMailEvent.class, old).revertUrl());
  }
}
