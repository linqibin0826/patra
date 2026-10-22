package dev.patra.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import tools.jackson.databind.JsonNode;

/// 再认证的完整流程：窗口、403、`/auth/reauth` 换令牌保留会话 ID、与登录共用失败限制。
@DisplayName("再认证的完整流程")
class ReauthenticationFlowIT extends IdentityFlowITSupport {

  @Test
  @DisplayName("刚注册时敏感操作直接通过；10 分钟后 403 REAUTH_REQUIRED；再认证换新令牌、旧令牌失效、会话 ID 不变；之后恢复")
  void should_allow_sensitive_action_right_after_register_then_require_reauth_after_ten_minutes() {
    RawResponse registered = register("flow.reauth@example.com", "u-flow-reauth");
    long userId = userIdOf(registered);
    String oldToken = sessionTokenOf(registered);
    long sessionId = latestSessionIdOf(userId);

    RawResponse first =
        post(
            "/me/email/change",
            Map.of("newEmail", "flow.reauth.new@example.com"),
            assertionFor(userId, sessionId));
    assertThat(first.status()).as(first.body()).isEqualTo(202);
    assertThat(
            exchange(HttpMethod.DELETE, "/me/email/change", null, assertionFor(userId, sessionId))
                .status())
        .isEqualTo(204);

    clock.set(T0.plus(Duration.ofMinutes(10)));
    RawResponse expired =
        post(
            "/me/email/change",
            Map.of("newEmail", "flow.reauth.new@example.com"),
            assertionFor(userId, sessionId));
    assertThat(expired.status()).as(expired.body()).isEqualTo(403);
    JsonNode problem = json(expired);
    assertThat(problem.get("code").asString()).isEqualTo("IDN-0403");
    assertThat(problem.get("traits").toString()).contains("REAUTH_REQUIRED");

    RawResponse reauth =
        post("/auth/reauth", Map.of("password", PASSWORD), assertionFor(userId, sessionId));
    assertThat(reauth.status()).as(reauth.body()).isEqualTo(200);
    String newToken = sessionTokenOf(reauth);
    assertThat(newToken).isNotEqualTo(oldToken).startsWith("patra_user_");
    assertThat(json(reauth).get("user").get("userId").asString()).isEqualTo(Long.toString(userId));
    assertThat(redis.hasKey(sessionKey(oldToken))).isFalse();
    assertThat(redis.hasKey(sessionKey(newToken))).isTrue();
    assertThat(redis.opsForHash().get(sessionKey(newToken), "session_id"))
        .isEqualTo(Long.toString(sessionId));
    assertThat(redis.opsForHash().get(sessionKey(newToken), "auth_at"))
        .isEqualTo(Long.toString(T0.plus(Duration.ofMinutes(10)).toEpochMilli()));
    assertThat(latestSessionIdOf(userId)).as("没有新的登录记录").isEqualTo(sessionId);

    RawResponse again =
        post(
            "/me/email/change",
            Map.of("newEmail", "flow.reauth.new@example.com"),
            assertionFor(userId, sessionId));
    assertThat(again.status()).as(again.body()).isEqualTo(202);
  }

  @Test
  @DisplayName("再认证的密码错误计入登录失败限制：连错到上限后登录也 429；解锁后正确密码 200")
  void should_share_login_failure_limit_with_reauth() {
    String email = "flow.reauth.lock@example.com";
    RawResponse registered = register(email, "u-flow-reauth-lock");
    long userId = userIdOf(registered);
    long sessionId = latestSessionIdOf(userId);

    for (int i = 0; i < 4; i++) {
      RawResponse wrong =
          post("/auth/reauth", Map.of("password", "wrong-" + i), assertionFor(userId, sessionId));
      assertThat(wrong.status()).as("第 %d 次", i + 1).isEqualTo(401);
      assertThat(json(wrong).get("traits").toString()).contains("PASSWORD_MISMATCH");
    }
    RawResponse fifth =
        post("/auth/reauth", Map.of("password", "wrong-5"), assertionFor(userId, sessionId));
    assertThat(fifth.status()).as(fifth.body()).isEqualTo(429);
    assertThat(fifth.header(HttpHeaders.RETRY_AFTER)).isNotNull();

    RawResponse loginLocked = login(email, PASSWORD);
    assertThat(loginLocked.status()).as("登录共用同一把锁").isEqualTo(429);

    // 锁的过期走 Redis 的真实时间，拨测试时钟拨不动它：直接删掉锁与计数模拟锁到期
    redis.delete(loginThrottleKeys(email));
    RawResponse ok =
        post("/auth/reauth", Map.of("password", PASSWORD), assertionFor(userId, sessionId));
    assertThat(ok.status()).as(ok.body()).isEqualTo(200);
  }
}
