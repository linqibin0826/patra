package dev.patra.catalog.domain.fulltext.policy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

/// 全文抓取的重试与再查时间（工程 spec 第 3.4 节）。
///
/// 次数都传"本次计入之后"的次数（第一次失败传 1）。由 boot 的 `FullTextConfiguration` 以 `@Bean` 注册。
///
/// @author linqibin
/// @since 0.11.0
public class FullTextRetrySchedule {

  private static final Duration[] FETCH_BACKOFF = {
    Duration.ofHours(1), Duration.ofHours(4), Duration.ofDays(1), Duration.ofDays(3)
  };

  private static final Duration[] UNAVAILABLE_RECHECK = {
    Duration.ofDays(1),
    Duration.ofDays(3),
    Duration.ofDays(7),
    Duration.ofDays(30),
    Duration.ofDays(90)
  };

  private final Clock clock;

  /// 创建时间表。
  ///
  /// @param clock 时钟
  public FullTextRetrySchedule(Clock clock) {
    this.clock = clock;
  }

  /// 抓取失败后的下次重试时间：1 小时、4 小时、1 天，之后每 3 天。
  ///
  /// @param attemptCount 计入本次后的连续失败次数
  /// @return 下次重试时间
  public Instant afterFetchFailure(int attemptCount) {
    return clock.instant().plus(step(FETCH_BACKOFF, attemptCount));
  }

  /// 暂不可用后的下次再查时间。
  ///
  /// PMC 放开日期在今天之后时取那天 00:00 UTC（精确、不浪费请求）；否则 1、3、7、30 天，之后每 90 天。
  ///
  /// @param attemptCount 计入本次后的连续不可用次数
  /// @param pmcReleaseDate PMC 放开日期，可空
  /// @return 下次再查时间
  public Instant afterUnavailable(int attemptCount, LocalDate pmcReleaseDate) {
    LocalDate today = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
    if (pmcReleaseDate != null && pmcReleaseDate.isAfter(today)) {
      return pmcReleaseDate.atStartOfDay(ZoneOffset.UTC).toInstant();
    }
    return clock.instant().plus(step(UNAVAILABLE_RECHECK, attemptCount));
  }

  /// 第 n 次取第 n 档，超出后一直取最后一档。
  private static Duration step(Duration[] steps, int attemptCount) {
    int index = Math.min(Math.max(attemptCount, 1), steps.length) - 1;
    return steps[index];
  }
}
