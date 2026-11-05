package dev.patra.analytics.app.collect.usecase.collect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.patra.analytics.domain.collect.event.ArticleRead;
import dev.patra.analytics.domain.collect.event.ArticlesReadEvent;
import dev.patra.analytics.domain.collect.exception.EventPublishUnavailableException;
import dev.patra.analytics.domain.collect.model.enums.BotReason;
import dev.patra.analytics.domain.collect.model.vo.CollectedEnvelope;
import dev.patra.analytics.domain.collect.model.vo.EnvelopeCheck;
import dev.patra.analytics.domain.collect.model.vo.EventEnrichment;
import dev.patra.analytics.domain.collect.model.vo.GeoLocation;
import dev.patra.analytics.domain.collect.model.vo.OutboundMessage;
import dev.patra.analytics.domain.collect.model.vo.UserAgentInfo;
import dev.patra.analytics.domain.collect.port.geolocation.GeolocationPort;
import dev.patra.analytics.domain.collect.port.publish.EventPublishPort;
import dev.patra.analytics.domain.collect.port.rate.DeviceRatePort;
import dev.patra.analytics.domain.collect.port.robot.RobotListPort;
import dev.patra.analytics.domain.collect.port.schema.EventSchemaPort;
import dev.patra.analytics.domain.collect.port.useragent.UserAgentPort;
import dev.patra.analytics.domain.kernel.model.enums.EventStream;
import dev.patra.analytics.domain.kernel.model.enums.RejectionReason;
import dev.patra.analytics.domain.kernel.model.vo.EventName;
import dev.patra.analytics.domain.kernel.model.vo.EventSource;
import dev.patra.analytics.domain.kernel.model.vo.RejectedEnvelope;
import dev.patra.analytics.domain.kernel.port.dlq.DlqRecordPort;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class CollectEventsHandlerTest {

  private static final Instant NOW = Instant.parse("2026-11-01T08:30:12.402Z");

  private final FakeSchema schema = new FakeSchema();
  private final FakeRate rate = new FakeRate();
  private final FakePublisher publisher = new FakePublisher();
  private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
  private final List<Object> published = new ArrayList<>();
  private final ApplicationEventPublisher events = published::add;
  private boolean robotListed;
  private String deviceClass = "Desktop";

  private final UserAgentPort userAgents =
      userAgent -> UserAgentInfo.of("Chrome", "Mac OS", deviceClass);
  private final RobotListPort robots = userAgent -> robotListed;
  private final GeolocationPort geolocation = ip -> GeoLocation.of("CN", "浙江省");

  private CollectEventsHandler handler() {
    return new CollectEventsHandler(
        schema,
        schema,
        userAgents,
        robots,
        rate,
        geolocation,
        publisher,
        Clock.fixed(NOW, ZoneOffset.UTC),
        meters,
        events);
  }

  @Test
  void valid_and_invalid_events_are_routed() {
    schema.valid("a", envelope("e1", "d1", EventName.ARTICLE_VIEWED));
    schema.valid("b", envelope("e2", "d2", EventName.PAGE_VIEWED));
    schema.invalid("c", RejectionReason.SOURCE_MISMATCH);

    CollectEventsResult result = handler().handle(CollectEventsCommand.of("body"));

    assertThat(result).isEqualTo(CollectEventsResult.of(2, 1));
    assertThat(publisher.batches).hasSize(1);
    List<OutboundMessage> sent = publisher.batches.getFirst();
    assertThat(sent)
        .filteredOn(m -> m.stream() == EventStream.RAW)
        .extracting(OutboundMessage::key)
        .containsExactly("d1", "d2");
    assertThat(sent)
        .filteredOn(m -> m.stream() == EventStream.DLQ)
        .extracting(OutboundMessage::payload)
        .containsExactly("dlq:source_mismatch");
    assertThat(meters.get("analytics.collect.events").tag("outcome", "rejected").counter().count())
        .isEqualTo(1.0);
  }

  @Test
  void received_at_is_shared_across_batch() {
    schema.valid("a", envelope("e1", "d1", EventName.ARTICLE_VIEWED));
    schema.valid("b", envelope("e2", "d2", EventName.PAGE_VIEWED));

    handler().handle(CollectEventsCommand.of("body"));

    assertThat(schema.enrichments).extracting(EventEnrichment::receivedAt).containsOnly(NOW);
  }

  @Test
  void robots_listed_skips_rate_rule() {
    robotListed = true;
    schema.valid("a", envelope("e1", "d1", EventName.ARTICLE_VIEWED));

    handler().handle(CollectEventsCommand.of("body"));

    assertThat(schema.enrichments.getFirst().botReason()).isEqualTo(BotReason.UA_LIST);
    assertThat(rate.calls).isEmpty();
    assertThat(
            meters.get("analytics.collect.bot.events").tag("reason", "ua_list").counter().count())
        .isEqualTo(1.0);
  }

  @Test
  void rate_over_limit_marks_rate() {
    rate.next = OptionalInt.of(31);
    schema.valid("a", envelope("e1", "d1", EventName.ARTICLE_VIEWED));

    handler().handle(CollectEventsCommand.of("body"));

    assertThat(schema.enrichments.getFirst().botReason()).isEqualTo(BotReason.RATE);
  }

  @Test
  void rate_at_limit_is_not_bot() {
    rate.next = OptionalInt.of(30);
    schema.valid("a", envelope("e1", "d1", EventName.ARTICLE_VIEWED));

    handler().handle(CollectEventsCommand.of("body"));

    assertThat(schema.enrichments.getFirst().bot()).isFalse();
  }

  @Test
  void rate_unavailable_counts_skip_metric() {
    rate.next = OptionalInt.empty();
    schema.valid("a", envelope("e1", "d1", EventName.ARTICLE_VIEWED));

    handler().handle(CollectEventsCommand.of("body"));

    assertThat(schema.enrichments.getFirst().bot()).isFalse();
    assertThat(meters.get("analytics.collect.rate.rule.skipped").counter().count()).isEqualTo(1.0);
  }

  @Test
  void only_article_viewed_is_counted() {
    schema.valid("a", envelope("e1", "d1", EventName.PAGE_VIEWED));
    schema.valid("b", envelope("e2", "d1", EventName.ARTICLE_VIEWED));

    handler().handle(CollectEventsCommand.of("body"));

    assertThat(rate.calls).containsExactly("d1:e1:false", "d1:e2:true");
  }

  @Test
  void geolocation_and_user_agent_fill_enrichment() {
    deviceClass = "Phone";
    schema.valid("a", envelope("e1", "d1", EventName.ARTICLE_VIEWED));

    handler().handle(CollectEventsCommand.of("body"));

    EventEnrichment enrichment = schema.enrichments.getFirst();
    assertThat(enrichment.geoLocation()).isEqualTo(GeoLocation.of("CN", "浙江省"));
    assertThat(enrichment.userAgent().deviceClass()).isEqualTo("Phone");
    assertThat(enrichment.derivedAt()).isEqualTo(NOW.minusMillis(2_225));
  }

  @Test
  void publish_failure_propagates() {
    publisher.fail = true;
    schema.valid("a", envelope("e1", "d1", EventName.ARTICLE_VIEWED));
    CollectEventsHandler handler = handler();
    CollectEventsCommand command = CollectEventsCommand.of("body");

    assertThatThrownBy(() -> handler.handle(command))
        .isInstanceOf(EventPublishUnavailableException.class);
  }

  @Test
  void enriched_output_violation_goes_to_dlq() {
    schema.valid("a", envelope("e1", "d1", EventName.ARTICLE_VIEWED));
    schema.brokenOutput.add("e1");

    CollectEventsResult result = handler().handle(CollectEventsCommand.of("body"));

    assertThat(result).isEqualTo(CollectEventsResult.of(0, 1));
    assertThat(publisher.batches.getFirst())
        .extracting(OutboundMessage::payload)
        .containsExactly("dlq:schema_invalid");
  }

  @Test
  void unknown_event_name_is_not_used_as_metric_tag() {
    schema.checks.put(
        "a", new EnvelopeCheck.Invalid(RejectionReason.UNKNOWN_EVENT, "detail", "random_xyz_123"));

    handler().handle(CollectEventsCommand.of("body"));

    assertThat(meters.find("analytics.collect.events").tag("event_name", "random_xyz_123").meters())
        .isEmpty();
    assertThat(
            meters.get("analytics.collect.events").tag("event_name", "unknown").counter().count())
        .isEqualTo(1.0);
  }

  @Test
  void rate_failure_skips_rule_for_rest_of_batch() {
    rate.next = OptionalInt.empty();
    schema.valid("a", envelope("e1", "d1", EventName.ARTICLE_VIEWED));
    schema.valid("b", envelope("e2", "d2", EventName.ARTICLE_VIEWED));

    handler().handle(CollectEventsCommand.of("body"));

    assertThat(rate.calls).hasSize(1);
    assertThat(meters.get("analytics.collect.rate.rule.skipped").counter().count()).isEqualTo(2.0);
  }

  @Test
  void publishes_reads_after_kafka_success() {
    schema.valid("a", read("e1", EventName.ARTICLE_VIEWED, "884213907612315648", 39871245L));
    schema.valid("b", envelope("e2", "d2", EventName.PAGE_VIEWED));

    handler().handle(CollectEventsCommand.of("body"));

    assertThat(publisher.batches).hasSize(1);
    assertThat(published)
        .containsExactly(
            ArticlesReadEvent.of(
                List.of(
                    ArticleRead.of(
                        884213907612315648L,
                        39871245L,
                        Instant.parse("2026-11-01T08:30:10.177Z"),
                        false))));
  }

  @Test
  void no_event_when_kafka_fails() {
    publisher.fail = true;
    schema.valid("a", read("e1", EventName.ARTICLE_VIEWED, "7", 9L));
    CollectEventsHandler handler = handler();
    CollectEventsCommand command = CollectEventsCommand.of("body");

    assertThatThrownBy(() -> handler.handle(command))
        .isInstanceOf(EventPublishUnavailableException.class);
    assertThat(published).isEmpty();
  }

  @Test
  void no_event_without_reads() {
    schema.valid("a", envelope("e1", "d1", EventName.ARTICLE_VIEWED));
    schema.valid("b", read("e2", EventName.ARTICLE_COLLECTED, "7", 9L));

    handler().handle(CollectEventsCommand.of("body"));

    assertThat(published).isEmpty();
  }

  @Test
  void rejected_events_are_not_reads() {
    schema.valid("a", read("e1", EventName.ARTICLE_VIEWED, "7", 9L));
    schema.brokenOutput.add("e1");

    handler().handle(CollectEventsCommand.of("body"));

    assertThat(published).isEmpty();
  }

  private static CollectedEnvelope read(
      String eventId, EventName name, String userId, long articleId) {
    return envelope(eventId, "d1", name, userId, articleId);
  }

  private static CollectedEnvelope envelope(String eventId, String deviceId, EventName name) {
    return envelope(eventId, deviceId, name, null, null);
  }

  private static CollectedEnvelope envelope(
      String eventId, String deviceId, EventName name, String userId, Long articleId) {
    return CollectedEnvelope.builder()
        .eventId(eventId)
        .eventName(name)
        .schemaVersion("1-0-0")
        .source(name.source() == EventSource.SERVER ? EventSource.SERVER : EventSource.CLIENT)
        .createdAt(Instant.parse("2026-11-01T08:30:10.120Z"))
        .sentAt(Instant.parse("2026-11-01T08:30:12.345Z"))
        .deviceId(deviceId)
        .sessionId("0193e19f-0d2a-7b11-8c3e-5a6f7b8c9d0e")
        .userId(userId)
        .pagePath("/")
        .pageType("home")
        .referrerHost(null)
        .clientIp("203.0.113.24")
        .userAgent("Mozilla/5.0")
        .synthetic(false)
        .propertiesJson("{}")
        .articleId(articleId)
        .build();
  }

  /// 按拆批顺序返回预设的校验结果。
  private static final class FakeSchema implements EventSchemaPort, DlqRecordPort {
    private final Map<String, EnvelopeCheck> checks = new LinkedHashMap<>();
    private final List<EventEnrichment> enrichments = new ArrayList<>();
    private final List<String> brokenOutput = new ArrayList<>();

    void valid(String json, CollectedEnvelope envelope) {
      checks.put(json, new EnvelopeCheck.Valid(envelope));
    }

    void invalid(String json, RejectionReason reason) {
      checks.put(json, new EnvelopeCheck.Invalid(reason, "detail", "article_viewed"));
    }

    @Override
    public List<String> splitBatch(String body) {
      return List.copyOf(checks.keySet());
    }

    @Override
    public EnvelopeCheck check(String eventJson) {
      return checks.get(eventJson);
    }

    @Override
    public String enrichedJson(CollectedEnvelope event, EventEnrichment enrichment) {
      if (brokenOutput.contains(event.eventId())) {
        throw new IllegalStateException("broken");
      }
      enrichments.add(enrichment);
      return "raw:" + event.eventId();
    }

    @Override
    public String dlqJson(RejectedEnvelope rejected) {
      return "dlq:" + rejected.reason().wire();
    }
  }

  /// 记录调用并返回预设计数。
  private static final class FakeRate implements DeviceRatePort {
    private final List<String> calls = new ArrayList<>();
    private OptionalInt next = OptionalInt.of(1);

    @Override
    public OptionalInt observe(String deviceId, String eventId, boolean counted, Instant at) {
      calls.add(deviceId + ":" + eventId + ":" + counted);
      return next;
    }
  }

  /// 记录每批写出的消息。
  private static final class FakePublisher implements EventPublishPort {
    private final List<List<OutboundMessage>> batches = new ArrayList<>();
    private boolean fail;

    @Override
    public void publish(List<OutboundMessage> messages) {
      if (fail) {
        throw new EventPublishUnavailableException("down");
      }
      batches.add(List.copyOf(messages));
    }
  }
}
