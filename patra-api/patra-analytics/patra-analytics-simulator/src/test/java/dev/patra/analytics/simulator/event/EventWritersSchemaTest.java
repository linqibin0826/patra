package dev.patra.analytics.simulator.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.networknt.schema.Error;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.dialect.Dialect;
import com.networknt.schema.dialect.Dialects;
import com.networknt.schema.keyword.AnnotationKeyword;
import dev.patra.analytics.simulator.model.ArticlePool;
import dev.patra.analytics.simulator.model.BotLabeler;
import dev.patra.analytics.simulator.model.CrawlerGenerator;
import dev.patra.analytics.simulator.model.Population;
import dev.patra.analytics.simulator.model.SessionGenerator;
import dev.patra.analytics.simulator.model.SimSession;
import dev.patra.analytics.simulator.model.TrafficModel;
import dev.patra.analytics.simulator.support.SmallArticlePoolFixture;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/// 生成的每条事件都要通过服务共用的 JSON Schema（spec §6 的第一个 TDD 锚点）。
class EventWritersSchemaTest {

  private static final String BASE_ID = "https://patra.linqibin.dev/schemas/analytics/";
  private static final JsonMapper MAPPER = JsonMapper.builder().build();
  private static final SchemaRegistry REGISTRY =
      SchemaRegistry.withDialect(
          Dialect.builder(Dialects.getDraft202012())
              .keyword(new AnnotationKeyword("x-patra-version"))
              .build(),
          builder ->
              builder.schemaIdResolvers(
                  resolvers -> resolvers.mapPrefix(BASE_ID, "classpath:analytics/schema/")));

  @Test
  @Timeout(value = 60, unit = TimeUnit.SECONDS)
  void every_generated_event_passes_enriched_and_envelope_schemas() throws Exception {
    Schema enriched = schema("enriched-event");
    Schema envelope = schema("collect-envelope");
    Schema batch = schema("collect-batch");
    EnrichedEventWriter enrichedWriter = new EnrichedEventWriter(MAPPER);
    CollectEnvelopeWriter envelopeWriter = new CollectEnvelopeWriter(MAPPER);
    SplittableRandom random = new SplittableRandom(17);
    List<ObjectNode> pending = new ArrayList<>();
    int checked = 0;
    for (SimSession session : sessions(2_000)) {
      for (SimEvent event : session.events()) {
        String line = enrichedWriter.write(event);
        assertValid(enriched, MAPPER.readTree(line), line);
        ObjectNode node = envelopeWriter.write(event, event.sentAt(), random);
        assertValid(envelope, node, node.toString());
        pending.add(node);
        if (pending.size() == 500) {
          assertValid(batch, MAPPER.readTree(envelopeWriter.batch(pending)), "batch");
          pending.clear();
        }
        checked++;
      }
    }

    assertThat(checked).isGreaterThan(40_000);
  }

  static List<SimSession> sessions(int count) {
    TrafficModel model = TrafficModel.load(null, MAPPER);
    ArticlePool pool = ArticlePool.load(smallPool(), 200_000);
    Population population =
        Population.build(
            model.toBuilder().devices(20_000).registeredUsers(2_000).build(),
            new SyntheticIds(3),
            1);
    SessionGenerator humans = new SessionGenerator(model, pool);
    CrawlerGenerator crawlers = new CrawlerGenerator(model, pool);
    SplittableRandom random = new SplittableRandom(5);
    Instant start = Instant.parse("2026-11-01T02:00:00Z");
    List<SimSession> sessions = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      Instant at = start.plusSeconds(i);
      SimSession session =
          switch (i % 50) {
            case 0 ->
                crawlers.declared(population.freshDevice(20_000 + i, true, random), at, random);
            case 1 ->
                crawlers.stealth(population.freshDevice(20_000 + i, false, random), at, random);
            case 2 ->
                humans.noscript(population.freshDevice(20_000 + i, false, random), at, random);
            default -> humans.human(population.sample(random), at, random);
          };
      sessions.add(BotLabeler.label(session));
    }
    return sessions;
  }

  private static Path smallPool() {
    return SmallArticlePoolFixture.path();
  }

  private static Schema schema(String name) {
    return REGISTRY.getSchema(SchemaLocation.of(BASE_ID + name + ".schema.json"));
  }

  private static void assertValid(Schema schema, JsonNode node, String text) {
    List<Error> errors = schema.validate(node);
    assertThat(errors).as(text).isEmpty();
  }
}
