package dev.patra.interaction.domain.post.policy;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

/// 「今天」是北京时间的日期，与部署时区无关。
class ExperienceRulesTest {

  /// UTC 16:00 是北京时间 0 点。
  @Test
  void should_use_beijing_date() {
    assertThat(
            ExperienceRules.today(
                Clock.fixed(Instant.parse("2026-10-26T16:00:00Z"), ZoneOffset.UTC)))
        .isEqualTo(LocalDate.parse("2026-10-27"));
    assertThat(
            ExperienceRules.today(
                Clock.fixed(Instant.parse("2026-10-26T15:59:59Z"), ZoneOffset.UTC)))
        .isEqualTo(LocalDate.parse("2026-10-26"));
  }
}
