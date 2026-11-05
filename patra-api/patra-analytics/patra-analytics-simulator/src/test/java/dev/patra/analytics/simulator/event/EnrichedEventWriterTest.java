package dev.patra.analytics.simulator.event;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.analytics.simulator.model.SimSession;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class EnrichedEventWriterTest {

  private static final JsonMapper MAPPER = JsonMapper.builder().build();
  private static final List<SimSession> SESSIONS = EventWritersSchemaTest.sessions(300);
  private final EnrichedEventWriter writer = new EnrichedEventWriter(MAPPER);

  @Test
  void derived_at_is_created_plus_20_millis_and_synthetic() {
    SimEvent event = SESSIONS.get(5).events().getFirst();

    JsonNode node = MAPPER.readTree(writer.write(event));

    assertThat(node.get("derived_at").asString())
        .isEqualTo(Timestamps.format(event.createdAt().plus(Duration.ofMillis(20))));
    assertThat(node.get("received_at").asString())
        .isEqualTo(Timestamps.format(event.sentAt().plus(Duration.ofMillis(20))));
    assertThat(EnrichedEventWriter.derivedAt(event))
        .isEqualTo(event.createdAt().plus(Duration.ofMillis(20)));
    assertThat(node.get("is_synthetic").asBoolean()).isTrue();
    assertThat(node.has("client_ip")).isFalse();
    assertThat(node.has("user_agent")).isFalse();
  }

  @Test
  void visitor_user_id_is_json_null_and_member_is_decimal_string() {
    for (SimSession session : SESSIONS) {
      JsonNode node = MAPPER.readTree(writer.write(session.events().getFirst()));
      if (session.person().userId() == null) {
        assertThat(node.get("user_id").isNull()).isTrue();
      } else {
        assertThat(node.get("user_id").asString()).isEqualTo(session.person().userId().toString());
      }
    }
  }

  @Test
  void bot_flag_follows_bot_reason() {
    SimEvent crawler = SESSIONS.get(0).events().getFirst();
    SimEvent human = SESSIONS.get(3).events().getFirst();

    JsonNode crawlerNode = MAPPER.readTree(writer.write(crawler));
    JsonNode humanNode = MAPPER.readTree(writer.write(human));

    assertThat(crawlerNode.get("is_bot").asBoolean()).isTrue();
    assertThat(crawlerNode.get("bot_reason").asString()).isEqualTo("ua_list");
    assertThat(humanNode.get("is_bot").asBoolean()).isFalse();
    assertThat(humanNode.get("bot_reason").isNull()).isTrue();
  }

  @Test
  void same_event_serializes_identically() {
    SimEvent event = SESSIONS.get(7).events().get(2);

    assertThat(writer.write(event)).isEqualTo(new EnrichedEventWriter(MAPPER).write(event));
  }

  @Test
  void envelope_carries_ip_of_person_region_and_ua() {
    SimEvent event = SESSIONS.get(4).events().getFirst();
    Instant sent = Instant.parse("2026-11-02T00:00:00.123Z");

    var node = new CollectEnvelopeWriter(MAPPER).write(event, sent, new SplittableRandom(1));

    assertThat(event.person().region().ips()).contains(node.get("client_ip").asString());
    assertThat(node.get("user_agent").asString()).isEqualTo(event.person().ua().userAgent());
    assertThat(node.get("sent_at").asString()).isEqualTo("2026-11-02T00:00:00.123Z");
  }
}
