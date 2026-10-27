package dev.patra.interaction.domain.subject.policy;

import dev.patra.interaction.domain.subject.exception.PublishRateLimitedException;
import dev.patra.interaction.domain.subject.model.enums.PublishKind;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

/// 一个作者某种发表的当日计数，与放行规则。
///
/// @param day 计数所属的自然日
/// @param dayCount 当天已发表的条数
/// @param lastPublishedAt 上次发表的时刻
public record PublishQuota(LocalDate day, int dayCount, Instant lastPublishedAt) {

  /// 「每天」的时区：北京时间的自然日（产品规则，不随部署变）。
  public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

  /// 放行一次发表：先判当天上限（已经到上限的人等多久都没用，告诉他明天再来更准），再判间隔；放行时返回计数
  /// 加一后的新值。
  ///
  /// @param current 现有计数；没有发表过时为空
  /// @param kind 发表种类
  /// @param now 现在
  /// @param zone 自然日的时区
  /// @return 放行后的计数
  /// @throws PublishRateLimitedException 到了上限或离上一条太近
  public static PublishQuota admit(
      Optional<PublishQuota> current, PublishKind kind, Instant now, ZoneId zone) {
    LocalDate today = LocalDate.ofInstant(now, zone);
    int count = current.filter(q -> q.day().equals(today)).map(PublishQuota::dayCount).orElse(0);
    if (count >= kind.dailyLimit()) {
      Instant tomorrow = today.plusDays(1).atStartOfDay(zone).toInstant();
      throw PublishRateLimitedException.dailyLimitReached(kind, Duration.between(now, tomorrow));
    }
    if (current.isPresent() && !kind.minInterval().isZero()) {
      Instant allowedAt = current.get().lastPublishedAt().plus(kind.minInterval());
      if (now.isBefore(allowedAt)) {
        throw PublishRateLimitedException.tooFrequent(kind, Duration.between(now, allowedAt));
      }
    }
    return new PublishQuota(today, count + 1, now);
  }
}
