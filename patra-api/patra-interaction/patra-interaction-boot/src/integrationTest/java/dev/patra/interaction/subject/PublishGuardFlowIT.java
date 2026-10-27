package dev.patra.interaction.subject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.patra.interaction.domain.subject.exception.PublishIneligibleException;
import dev.patra.interaction.domain.subject.exception.PublishRateLimitedException;
import dev.patra.interaction.domain.subject.exception.SubjectErrorTrait;
import dev.patra.interaction.domain.subject.model.enums.PublishKind;
import dev.patra.interaction.domain.subject.policy.PublishQuota;
import dev.patra.interaction.domain.subject.port.gateway.PublishGuardGateway;
import dev.patra.interaction.support.InteractionFlowITSupport;
import dev.patra.tool.core.error.DomainException;
import dev.patra.tool.web.error.engine.ErrorResolutionEngine;
import dev.patra.tool.web.error.model.ErrorResolution;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

/// 发表守卫的完整流程（PAP-120 的 AC）：邮箱未验证、账号状态异常、间隔不足、超每日上限各自拒绝，错误码与原因码明确；
/// 同一作者在上限边上并发发表只放行一个。identity 由 WireMock 扮演，计数是真实 PG。
class PublishGuardFlowIT extends InteractionFlowITSupport {

  @Autowired private PublishGuardGateway guard;
  @Autowired private TransactionTemplate transactions;
  @Autowired private ErrorResolutionEngine engine;
  @Autowired private Clock clock;

  /// AC：邮箱未验证、账号状态异常（封禁、冷静期、已注销、identity 不认识）都是 409，原因码分开。
  @Test
  void should_reject_unverified_and_restricted_accounts_with_409() {
    IDENTITY_DATA.put(1L, "ACTIVE", false, "PUBLIC");
    IDENTITY_DATA.put(2L, "BANNED", true, "PUBLIC");
    IDENTITY_DATA.put(3L, "PENDING_DELETION", true, "PUBLIC");
    IDENTITY_DATA.put(4L, "DELETED", true, "PUBLIC");
    IDENTITY_DATA.put(6L, "ACTIVE", true, "SELF");

    assertRejected(() -> guard.requireEligible(1L), 409, SubjectErrorTrait.EMAIL_UNVERIFIED);
    for (long user : List.of(2L, 3L, 4L, 5L)) {
      assertRejected(() -> guard.requireEligible(user), 409, SubjectErrorTrait.ACCOUNT_RESTRICTED);
    }
    assertThat(guard.requireEligible(6L).userId()).isEqualTo(6L);
  }

  /// AC：10 秒内第二条评论被拒，429，带还要等多久。
  @Test
  void should_reject_second_comment_within_ten_seconds_with_429() {
    admitInTransaction(1L, PublishKind.COMMENT);

    DomainException ex =
        assertRejected(
            () -> admitInTransaction(1L, PublishKind.COMMENT), 429, SubjectErrorTrait.TOO_FREQUENT);

    assertThat(ex).isInstanceOf(PublishRateLimitedException.class);
    assertThat(((PublishRateLimitedException) ex).getRetryAfterSeconds()).isBetween(1L, 10L);
  }

  /// AC：当天第 101 条评论、第 11 条投稿经验被拒，429。
  @Test
  void should_reject_over_daily_limits() {
    seedQuota(1L, PublishKind.COMMENT, 100);
    seedQuota(2L, PublishKind.POST, 10);

    assertRejected(
        () -> admitInTransaction(1L, PublishKind.COMMENT),
        429,
        SubjectErrorTrait.DAILY_LIMIT_REACHED);
    assertRejected(
        () -> admitInTransaction(2L, PublishKind.POST), 429, SubjectErrorTrait.DAILY_LIMIT_REACHED);
    assertThat(count("SELECT day_count FROM subject.publish_quota WHERE user_id = 1"))
        .isEqualTo(100L);
  }

  /// 同一作者在上限边上并发写两条经验：作者锁让它们排队，只放行一个，库里正好到上限。
  @Test
  void should_admit_only_one_of_two_concurrent_posts_at_the_limit() throws Exception {
    seedQuota(3L, PublishKind.POST, 9);
    CountDownLatch start = new CountDownLatch(1);
    List<Future<String>> outcomes = new ArrayList<>();
    try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
      for (int i = 0; i < 2; i++) {
        outcomes.add(
            pool.submit(
                () -> {
                  start.await();
                  try {
                    admitInTransaction(3L, PublishKind.POST);
                    return "admitted";
                  } catch (PublishRateLimitedException ex) {
                    return ex.getErrorTraits().contains(SubjectErrorTrait.DAILY_LIMIT_REACHED)
                        ? "daily-limit"
                        : "other";
                  }
                }));
      }
      start.countDown();
      List<String> results = new ArrayList<>();
      for (Future<String> outcome : outcomes) {
        results.add(outcome.get());
      }
      assertThat(results).containsExactlyInAnyOrder("admitted", "daily-limit");
    }
    assertThat(count("SELECT day_count FROM subject.publish_quota WHERE user_id = 3"))
        .isEqualTo(10L);
  }

  /// 计数必须和内容写在同一事务里：事务外调用直接拒绝。
  @Test
  void should_require_callers_transaction_for_admit() {
    assertThatThrownBy(() -> guard.admit(1L, PublishKind.COMMENT))
        .isInstanceOf(IllegalTransactionStateException.class);
    assertThat(count("SELECT count(*) FROM subject.publish_quota")).isZero();
  }

  /// 在事务里放行一次。
  ///
  /// @param userId 作者
  /// @param kind 种类
  private void admitInTransaction(long userId, PublishKind kind) {
    transactions.executeWithoutResult(status -> guard.admit(userId, kind));
  }

  /// 预写今天（北京时间）的计数，上次发表在一小时前。
  ///
  /// @param userId 作者
  /// @param kind 种类
  /// @param dayCount 当天条数
  private void seedQuota(long userId, PublishKind kind, int dayCount) {
    Instant now = clock.instant();
    jdbcTemplate.update(
        "INSERT INTO subject.publish_quota (user_id, kind, quota_day, day_count, last_published_at)"
            + " VALUES (?, ?, ?, ?, ?)",
        userId,
        kind.name(),
        LocalDate.ofInstant(now, PublishQuota.ZONE),
        dayCount,
        Timestamp.from(now.minusSeconds(3600)));
  }

  /// 断言被拒：异常带指定原因码，错误引擎把它映射成指定状态码与 `INTERACTION-0xxx` 错误码。
  ///
  /// @param call 调用
  /// @param status 期望的状态码
  /// @param reason 期望的原因码
  /// @return 异常
  private DomainException assertRejected(Runnable call, int status, SubjectErrorTrait reason) {
    DomainException ex = null;
    try {
      call.run();
    } catch (PublishIneligibleException | PublishRateLimitedException caught) {
      ex = caught;
    }
    assertThat(ex).as("应被拒绝").isNotNull();
    assertThat(ex.getErrorTraits()).contains(reason);
    ErrorResolution resolution = engine.resolve(ex);
    assertThat(resolution.httpStatus()).isEqualTo(status);
    assertThat(resolution.errorCode().code()).isEqualTo("INTERACTION-0" + status);
    return ex;
  }
}
