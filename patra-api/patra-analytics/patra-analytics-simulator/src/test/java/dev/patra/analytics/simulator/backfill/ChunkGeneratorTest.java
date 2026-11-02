package dev.patra.analytics.simulator.backfill;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.analytics.simulator.event.EnrichedEventWriter;
import dev.patra.analytics.simulator.event.SyntheticIds;
import dev.patra.analytics.simulator.model.Population;
import dev.patra.analytics.simulator.model.SessionKind;
import dev.patra.analytics.simulator.model.SessionMix;
import dev.patra.analytics.simulator.model.SimSession;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class ChunkGeneratorTest {

  private static final Population POPULATION =
      Population.build(BackfillFixture.MODEL, new SyntheticIds(3), 42L);
  private static final SessionMix MIX =
      new SessionMix(BackfillFixture.MODEL, BackfillFixture.POOL, POPULATION);
  private static final BackfillPlan PLAN =
      BackfillPlan.of(BackfillFixture.params(), BackfillFixture.MODEL, MIX.meanEvents(42L));
  private static final ChunkGenerator GENERATOR =
      new ChunkGenerator(PLAN, MIX, BackfillFixture.MODEL, POPULATION.size(), 42L);

  @Test
  void same_chunk_generated_twice_is_identical() {
    LocalDate day = PLAN.days().get(1);
    EnrichedEventWriter writer = new EnrichedEventWriter(BackfillFixture.MAPPER);

    List<String> first = lines(GENERATOR.generate(day, 0), writer);
    List<String> second = lines(GENERATOR.generate(day, 0), writer);

    assertThat(first).isNotEmpty().isEqualTo(second);
  }

  @Test
  void chunk_has_planned_sessions_and_crawler_shares() {
    LocalDate day = PLAN.days().get(0);
    List<SimSession> sessions =
        IntStream.range(0, PLAN.chunks(day))
            .boxed()
            .flatMap(c -> GENERATOR.generate(day, c).sessions().stream())
            .toList();
    Map<SessionKind, Long> kinds =
        sessions.stream().collect(Collectors.groupingBy(SimSession::kind, Collectors.counting()));

    assertThat((long) sessions.size()).isEqualTo(PLAN.sessions(day));
    assertThat(kinds.get(SessionKind.DECLARED_CRAWLER) / (double) sessions.size())
        .isBetween(0.015, 0.045);
    assertThat(kinds.get(SessionKind.HUMAN) / (double) sessions.size()).isBetween(0.90, 0.97);
    assertThat(sessions)
        .filteredOn(s -> s.kind() == SessionKind.DECLARED_CRAWLER)
        .allMatch(s -> s.events().stream().allMatch(e -> "ua_list".equals(e.botReason())));
  }

  private static List<String> lines(DayChunk chunk, EnrichedEventWriter writer) {
    return chunk.sessions().stream().flatMap(s -> s.events().stream()).map(writer::write).toList();
  }
}
