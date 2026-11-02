package dev.patra.analytics.simulator.live;

import static org.assertj.core.api.Assertions.assertThat;

import com.clickhouse.client.api.Client;
import dev.patra.analytics.simulator.backfill.ClickHouseEventSink;
import dev.patra.analytics.simulator.support.ClickHouseSimulatorFixture;
import dev.patra.analytics.simulator.truth.RunScope;
import java.io.StringWriter;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class PartsSamplerIT {

  private final Client client = ClickHouseSimulatorFixture.migratedDatabaseWithoutMerges();

  @AfterEach
  void close() {
    client.close();
  }

  @Test
  void samples_parts_and_freshness_of_this_run() {
    Instant now = Instant.now();
    String line =
        """
        {"event_id":"%s","event_name":"page_viewed","schema_version":"1-0-0","source":"client",\
        "created_at":"%s","sent_at":"%s","received_at":"%s","derived_at":"%s",\
        "device_id":"sim004AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA","session_id":"%s","user_id":null,\
        "page_path":"/","page_type":"home","referrer_host":null,"country_code":null,"province":null,\
        "browser_family":"Chrome","os_family":"Mac OS","device_class":"Desktop","is_bot":false,\
        "bot_reason":null,"is_synthetic":true,"properties":{}}"""
            .formatted(UUID.randomUUID(), now, now, now.minusSeconds(4), now, UUID.randomUUID());
    new ClickHouseEventSink(client)
        .insert(() -> List.of(line).iterator(), "it:" + UUID.randomUUID());
    PartsSampler sampler = new PartsSampler(client, new RunScope(4), Clock.systemUTC());
    StringWriter csv = new StringWriter();

    sampler.sampleInto(csv);

    String[] rows = csv.toString().split("\n");
    assertThat(rows[0]).isEqualTo(PartsSampler.HEADER);
    String[] values = rows[1].split(",");
    assertThat(Long.parseLong(values[1])).isPositive();
    assertThat(Double.parseDouble(values[7])).isBetween(3.0, 120.0);
  }
}
