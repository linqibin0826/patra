package dev.patra.identity;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.identity.api.account.event.PasswordResetDoneMailEvent;
import dev.patra.identity.api.account.event.PasswordResetMailEvent;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import tools.jackson.databind.JsonNode;

/// 找回密码的完整流程：防枚举的 202、重设后的撤销与清零、令牌过期、提交限流。
@DisplayName("找回密码的完整流程")
class PasswordResetFlowIT extends IdentityFlowITSupport {

  @Test
  @DisplayName("已注册与未注册的邮箱都 202 且结构一致、只给已注册的登记重设邮件、耗时同一量级；同邮箱每天第 6 次 429")
  void should_answer_202_for_registered_and_unknown_emails_alike_and_only_mail_registered() {
    String registered = "flow.forgot.known@example.com";
    String unknown = "flow.forgot.unknown@example.com";
    register(registered, "u-flow-forgot-known");

    List<Long> knownNanos = new ArrayList<>();
    List<Long> unknownNanos = new ArrayList<>();
    for (int i = 0; i < 5; i++) {
      clock.set(T0.plusSeconds(61L * i));
      knownNanos.add(timed(registered, 202));
      unknownNanos.add(timed(unknown, 202));
    }
    JsonNode known = json(post("/auth/password/forgot", Map.of("email", registered), noHeaders()));
    assertThat(known.get("code").asString()).isEqualTo("IDN-0429");
    assertThat(known.get("retryAfterSeconds").asInt()).isPositive();

    assertThat(securityMails.all(PasswordResetMailEvent.class, registered)).hasSize(5);
    assertThat(securityMails.all(PasswordResetMailEvent.class, unknown)).isEmpty();
    double ratio = (double) median(knownNanos) / median(unknownNanos);
    assertThat(ratio).as("已注册 / 未注册 的耗时中位数之比").isLessThan(10.0);
  }

  @Test
  @DisplayName(
      "重设：全部会话 401 并记 PASSWORD_RESET、全部令牌作废（含待确认的改邮箱）、失败计数清零、email_verified_at 已写、登记「密码已重设」、同一令牌再提交 422")
  void should_reset_password_revoke_everything_clear_failures_and_verify_email() {
    String email = "flow.reset@example.com";
    RawResponse registered = register(email, "u-flow-reset");
    long userId = userIdOf(registered);
    String tokenA = sessionTokenOf(registered);
    long sessionA = latestSessionIdOf(userId);
    String tokenB = sessionTokenOf(login(email, PASSWORD));
    long sessionB = latestSessionIdOf(userId);
    assertThat(
            post(
                    "/me/email/change",
                    Map.of("newEmail", "flow.reset.new@example.com"),
                    assertionFor(userId, sessionA))
                .status())
        .isEqualTo(202);
    assertThat(login(email, "wrong-1").status()).isEqualTo(401);
    assertThat(login(email, "wrong-2").status()).isEqualTo(401);
    assertThat(redis.hasKey(loginThrottleKeys(email).get(0))).isTrue();
    assertThat(post("/auth/password/forgot", Map.of("email", email), noHeaders()).status())
        .isEqualTo(202);
    String resetToken = tokenIn(mailEvent(PasswordResetMailEvent.class, email).resetUrl());

    RawResponse reset =
        post(
            "/auth/password/reset",
            Map.of("token", resetToken, "newPassword", "Another-Horse-03"),
            fromShenzhenChrome(noHeaders()));

    assertThat(reset.status()).as(reset.body()).isEqualTo(200);
    assertThat(json(reset).get("email").asString()).isEqualTo(email);
    assertThat(reset.body()).doesNotContain("sessionToken");
    assertThat(redis.hasKey(sessionKey(tokenA))).isFalse();
    assertThat(redis.hasKey(sessionKey(tokenB))).isFalse();
    assertThat(endReasonOf(sessionA)).isEqualTo("PASSWORD_RESET");
    assertThat(endReasonOf(sessionB)).isEqualTo("PASSWORD_RESET");
    assertThat(activeTokenPurposesOf(userId)).isEmpty();
    assertThat(get("/me/email/change", assertionFor(userId, sessionA)).status())
        .as("待确认的改邮箱随重设作废")
        .isEqualTo(204);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT email_verified_at FROM idn_user WHERE id = ?", Object.class, userId))
        .isNotNull();
    assertThat(redis.hasKey(loginThrottleKeys(email).get(0))).as("失败计数清零").isFalse();
    PasswordResetDoneMailEvent done = mailEvent(PasswordResetDoneMailEvent.class, email);
    assertThat(done.context().device()).isEqualTo("Chrome 141 · macOS");
    assertThat(done.context().ipAddress()).isEqualTo("113.92.157.29");
    assertThat(done.context().ipLocation()).isEqualTo("中国 广东 深圳");
    assertThat(login(email, "Another-Horse-03").status()).isEqualTo(200);
    assertThat(login(email, PASSWORD).status()).isEqualTo(401);
    RawResponse again =
        post(
            "/auth/password/reset",
            Map.of("token", resetToken, "newPassword", "Third-Horse-04"),
            noHeaders());
    assertThat(again.status()).isEqualTo(422);
  }

  @Test
  @DisplayName("令牌一小时后 422；提交每 IP 每分钟第 11 次 429 带 Retry-After")
  void should_expire_reset_token_after_one_hour_and_throttle_submits() {
    String email = "flow.reset.expire@example.com";
    register(email, "u-flow-reset-expire");
    assertThat(post("/auth/password/forgot", Map.of("email", email), noHeaders()).status())
        .isEqualTo(202);
    PasswordResetMailEvent resetMail = mailEvent(PasswordResetMailEvent.class, email);
    assertThat(resetMail.linkExpiresAt()).isEqualTo(T0.plus(Duration.ofMinutes(60)));
    String resetToken = tokenIn(resetMail.resetUrl());
    clock.set(T0.plus(Duration.ofMinutes(61)));

    RawResponse expired =
        post(
            "/auth/password/reset",
            Map.of("token", resetToken, "newPassword", "Another-Horse-03"),
            noHeaders());
    assertThat(expired.status()).isEqualTo(422);
    assertThat(json(expired).get("errors").get(0).get("code").asString()).isEqualTo("INVALID");

    for (int i = 0; i < 9; i++) {
      RawResponse garbage =
          post(
              "/auth/password/reset",
              Map.of("token", "G".repeat(43), "newPassword", "Another-Horse-03"),
              noHeaders());
      assertThat(garbage.status()).as("第 %d 次提交", i + 2).isEqualTo(422);
    }
    RawResponse eleventh =
        post(
            "/auth/password/reset",
            Map.of("token", "G".repeat(43), "newPassword", "Another-Horse-03"),
            noHeaders());
    assertThat(eleventh.status()).isEqualTo(429);
    assertThat(eleventh.header(HttpHeaders.RETRY_AFTER)).isNotNull();
  }

  /// 发一次申请并计时。
  ///
  /// @param email 邮箱
  /// @param expectedStatus 期望的状态码
  /// @return 耗时纳秒
  private long timed(String email, int expectedStatus) {
    long started = System.nanoTime();
    RawResponse response = post("/auth/password/forgot", Map.of("email", email), noHeaders());
    long elapsed = System.nanoTime() - started;
    assertThat(response.status()).as(response.body()).isEqualTo(expectedStatus);
    JsonNode body = json(response);
    assertThat(body.get("email").asString()).isEqualTo(email);
    assertThat(body.get("nextRequestAt").asString())
        .isEqualTo(clock.instant().plusSeconds(60).toString());
    assertThat(body.size()).isEqualTo(2);
    return elapsed;
  }

  /// 中位数。
  ///
  /// @param values 样本
  /// @return 中位数
  private static long median(List<Long> values) {
    List<Long> sorted = values.stream().sorted().toList();
    return sorted.get(sorted.size() / 2);
  }
}
