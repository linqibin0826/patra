package dev.patra.notification.infra.preference.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.notification.domain.preference.model.read.EmailMode;
import dev.patra.notification.domain.preference.model.read.EmailModes;
import dev.patra.notification.domain.preference.model.read.EmailSubscription;
import dev.patra.notification.domain.preference.port.read.EmailSubscriptionReadPort;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/// 给 mail 的读端口：没有行时按默认值建一行带新令牌，之后一直返回同一个令牌；并发首次调用只建一行。
class EmailSubscriptionReadAdapterIT extends PreferencePersistenceITSupport {

  @Autowired private EmailSubscriptionReadPort subscriptions;

  /// 第一次读：建一行，两类默认离站逐条，令牌合法，时间来自时钟。
  @Test
  void first_call_creates_default_row_with_valid_token() {
    EmailSubscription subscription = subscriptions.subscriptionOf(7L);

    assertThat(subscription.userId()).isEqualTo(7L);
    assertThat(subscription.reply()).isEqualTo(EmailMode.INSTANT);
    assertThat(subscription.mention()).isEqualTo(EmailMode.INSTANT);
    assertThat(subscription.unsubscribeToken()).matches("^[A-Za-z0-9_-]{43}$");
    assertThat(rows()).isEqualTo(1L);
    assertThat(
            jdbcTemplate
                .queryForObject(
                    "SELECT created_at FROM preference.email_preference WHERE user_id = 7",
                    OffsetDateTime.class)
                .toInstant())
        .isEqualTo(PreferencePersistenceITBootstrap.NOW);
  }

  /// 第二次读：同一个令牌，档位是库里当前的值。
  @Test
  void second_call_returns_same_token_and_current_modes() {
    String token = subscriptions.subscriptionOf(7L).unsubscribeToken();
    jdbcTemplate.update(
        "UPDATE preference.email_preference SET mention_mode = 'OFF' WHERE user_id = 7");

    EmailSubscription again = subscriptions.subscriptionOf(7L);

    assertThat(again.unsubscribeToken()).isEqualTo(token);
    assertThat(again.mention()).isEqualTo(EmailMode.OFF);
    assertThat(rows()).isEqualTo(1L);
  }

  /// 已有行（用户改过档位）时不动它。
  @Test
  void existing_row_is_returned_as_is() {
    insertRow(7L, "DIGEST", "OFF", TOKEN_A);

    EmailSubscription subscription = subscriptions.subscriptionOf(7L);

    assertThat(subscription.reply()).isEqualTo(EmailMode.DIGEST);
    assertThat(subscription.mention()).isEqualTo(EmailMode.OFF);
    assertThat(subscription.unsubscribeToken()).isEqualTo(TOKEN_A);
  }

  /// 8 个线程同时首次读同一个人：只建一行，大家拿到同一个令牌。
  @Test
  void should_return_same_token_to_concurrent_first_callers() throws Exception {
    int callers = 8;
    CountDownLatch start = new CountDownLatch(1);
    ExecutorService pool = Executors.newFixedThreadPool(callers);
    try {
      List<CompletableFuture<String>> futures =
          IntStream.range(0, callers)
              .mapToObj(
                  i ->
                      CompletableFuture.supplyAsync(
                          () -> {
                            await(start);
                            return subscriptions.subscriptionOf(7L).unsubscribeToken();
                          },
                          pool))
              .toList();
      start.countDown();
      List<String> tokens = futures.stream().map(CompletableFuture::join).toList();

      assertThat(tokens).hasSize(callers).containsOnly(tokens.getFirst());
      assertThat(rows()).isEqualTo(1L);
    } finally {
      pool.shutdownNow();
      assertThat(pool.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
    }
  }

  /// 等起跑信号。
  ///
  /// @param start 信号
  private static void await(CountDownLatch start) {
    try {
      if (!start.await(5, TimeUnit.SECONDS)) {
        throw new IllegalStateException("没有等到起跑信号");
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }

  /// 至少一类是每日汇总的人，按用户 ID 升序分页；不建行。
  @Test
  void digest_subscribers_lists_only_digest_and_pages() {
    insertRow(5L, "DIGEST", "OFF", "A".repeat(43));
    insertRow(6L, "INSTANT", "INSTANT", "B".repeat(43));
    insertRow(7L, "OFF", "DIGEST", "C".repeat(43));
    insertRow(8L, "DIGEST", "DIGEST", "D".repeat(43));

    assertThat(subscriptions.digestSubscribers(0L, 10))
        .extracting(EmailSubscription::userId)
        .containsExactly(5L, 7L, 8L);
    assertThat(subscriptions.digestSubscribers(5L, 1))
        .singleElement()
        .satisfies(
            s -> {
              assertThat(s.userId()).isEqualTo(7L);
              assertThat(s.mention()).isEqualTo(EmailMode.DIGEST);
              assertThat(s.unsubscribeToken()).isEqualTo("C".repeat(43));
            });
    assertThat(rows()).isEqualTo(4L);
  }

  /// 只读档位：没有行时给默认值，也不建行。
  @Test
  void find_subscription_defaults_without_inserting() {
    assertThat(subscriptions.findSubscription(7L))
        .isEqualTo(EmailModes.of(EmailMode.INSTANT, EmailMode.INSTANT));
    assertThat(rows()).isZero();

    insertRow(7L, "DIGEST", "OFF", "A".repeat(43));
    assertThat(subscriptions.findSubscription(7L))
        .isEqualTo(EmailModes.of(EmailMode.DIGEST, EmailMode.OFF));
  }
}
