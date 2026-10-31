package dev.patra.identity;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.identity.api.account.event.PasswordChangedMailEvent;
import dev.patra.identity.api.account.event.PasswordResetMailEvent;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import tools.jackson.databind.JsonNode;

/// 改密码的完整流程：当前密码、新旧相同、本机换令牌保留登录、其余会话撤销、通知邮件、作废重设令牌。
@DisplayName("改密码的完整流程")
class PasswordChangeFlowIT extends IdentityFlowITSupport {

  @Test
  @DisplayName("当前密码错 401 PASSWORD_MISMATCH；新旧相同 422 newPassword/UNCHANGED")
  void should_reject_wrong_current_and_unchanged_password() {
    RawResponse registered = register("flow.pwd.reject@example.com", "u-flow-pwd-reject");
    long userId = userIdOf(registered);
    long sessionId = latestSessionIdOf(userId);

    RawResponse wrong =
        exchange(
            HttpMethod.PUT,
            "/me/password",
            Map.of("currentPassword", "wrong", "newPassword", "Another-Horse-03"),
            assertionFor(userId, sessionId));
    assertThat(wrong.status()).as(wrong.body()).isEqualTo(401);
    assertThat(json(wrong).get("traits").toString()).contains("PASSWORD_MISMATCH");

    RawResponse same =
        exchange(
            HttpMethod.PUT,
            "/me/password",
            Map.of("currentPassword", PASSWORD, "newPassword", PASSWORD),
            assertionFor(userId, sessionId));
    assertThat(same.status()).as(same.body()).isEqualTo(422);
    JsonNode error = json(same).get("errors").get(0);
    assertThat(error.get("field").asString()).isEqualTo("newPassword");
    assertThat(error.get("code").asString()).isEqualTo("UNCHANGED");
  }

  @Test
  @DisplayName("改密码：本机拿到新令牌且旧令牌失效、另一会话 401 并记 PASSWORD_CHANGED、登记「密码已修改」、新密码能登录旧的不能、改密码前申请的重设令牌作废")
  void should_change_password_keep_current_session_revoke_others_and_send_mail() {
    String email = "flow.pwd.change@example.com";
    RawResponse registered = register(email, "u-flow-pwd-change");
    long userId = userIdOf(registered);
    String tokenA = sessionTokenOf(registered);
    long sessionA = latestSessionIdOf(userId);
    RawResponse second = login(email, PASSWORD);
    assertThat(second.status()).isEqualTo(200);
    String tokenB = sessionTokenOf(second);
    long sessionB = latestSessionIdOf(userId);
    assertThat(post("/auth/password/forgot", Map.of("email", email), noHeaders()).status())
        .isEqualTo(202);
    String resetToken = tokenIn(mailEvent(PasswordResetMailEvent.class, email).resetUrl());

    RawResponse changed =
        exchange(
            HttpMethod.PUT,
            "/me/password",
            Map.of("currentPassword", PASSWORD, "newPassword", "Another-Horse-03"),
            fromShenzhenChrome(assertionFor(userId, sessionA)));

    assertThat(changed.status()).as(changed.body()).isEqualTo(200);
    String rotated = sessionTokenOf(changed);
    assertThat(rotated).isNotEqualTo(tokenA);
    assertThat(redis.hasKey(sessionKey(tokenA))).isFalse();
    assertThat(redis.hasKey(sessionKey(rotated))).isTrue();
    assertThat(redis.hasKey(sessionKey(tokenB))).isFalse();
    assertThat(endReasonOf(sessionB)).isEqualTo("PASSWORD_CHANGED");
    assertThat(endReasonOf(sessionA)).isNull();
    assertThat(get("/auth/me", assertionFor(userId, sessionA)).status()).isEqualTo(200);

    PasswordChangedMailEvent notice = mailEvent(PasswordChangedMailEvent.class, email);
    assertThat(notice.context().occurredAt()).isEqualTo(T0);
    assertThat(notice.context().device()).isEqualTo("Chrome 141 · macOS");
    assertThat(notice.context().ipAddress()).isEqualTo("113.92.157.29");
    assertThat(notice.context().ipLocation()).isEqualTo("中国 广东 深圳");

    assertThat(login(email, "Another-Horse-03").status()).isEqualTo(200);
    assertThat(login(email, PASSWORD).status()).isEqualTo(401);
    assertThat(activeTokenPurposesOf(userId)).as("改密码按用户批量作废令牌").isEmpty();
    RawResponse reset =
        post(
            "/auth/password/reset",
            Map.of("token", resetToken, "newPassword", "Third-Horse-04"),
            noHeaders());
    assertThat(reset.status()).isEqualTo(422);
  }
}
