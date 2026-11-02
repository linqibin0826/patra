package dev.patra.analytics.simulator.live;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.clickhouse.client.api.Client;
import com.clickhouse.client.api.query.GenericRecord;
import dev.patra.analytics.simulator.truth.RunScope;
import java.io.StringWriter;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class PartsSamplerTest {

  @Test
  void writes_row_with_empty_merges_when_part_log_is_unreadable() {
    GenericRecord row = mock(GenericRecord.class);
    when(row.getLong("events_parts")).thenReturn(12L);
    when(row.getLong("max_parts")).thenReturn(5L);
    when(row.getLong("freshness_ms")).thenReturn(800L);
    Client client = mock(Client.class);
    when(client.queryAll(anyString()))
        .thenAnswer(
            call -> {
              if (call.<String>getArgument(0).contains("system.merges")
                  || call.<String>getArgument(0).contains("system.part_log")) {
                throw new IllegalStateException("Not enough privileges");
              }
              return List.<GenericRecord>of(row);
            });
    PartsSampler sampler =
        new PartsSampler(
            client,
            new RunScope(4),
            Clock.fixed(Instant.parse("2026-11-02T11:00:00Z"), ZoneOffset.UTC),
            Duration.ofSeconds(30));
    StringWriter csv = new StringWriter();

    sampler.sampleInto(csv);

    assertThat(csv.toString().split("\n"))
        .containsExactly(PartsSampler.HEADER, "2026-11-02T11:00:00Z,12,5,0,0,0,,0.8");
  }
}
