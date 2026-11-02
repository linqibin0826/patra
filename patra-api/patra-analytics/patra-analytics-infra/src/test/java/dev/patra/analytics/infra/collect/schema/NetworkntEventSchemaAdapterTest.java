package dev.patra.analytics.infra.collect.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.patra.analytics.domain.collect.exception.InvalidBatchException;
import dev.patra.analytics.domain.collect.model.vo.CollectedEnvelope;
import dev.patra.analytics.domain.collect.model.vo.EnvelopeCheck;
import dev.patra.analytics.domain.collect.model.vo.EventEnrichment;
import dev.patra.analytics.domain.collect.model.vo.GeoLocation;
import dev.patra.analytics.domain.collect.model.vo.UserAgentInfo;
import dev.patra.analytics.domain.kernel.model.enums.RejectionReason;
import dev.patra.analytics.domain.kernel.model.vo.EventName;
import dev.patra.analytics.domain.kernel.model.vo.RejectedEnvelope;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class NetworkntEventSchemaAdapterTest {

  private static final JsonMapper MAPPER = JsonMapper.builder().build();
  private final NetworkntEventSchemaAdapter adapter = new NetworkntEventSchemaAdapter(MAPPER);

  static Stream<Path> validEnvelopes() {
    return examples("valid", "collect-envelope--");
  }

  static Stream<Path> invalidEnvelopes() {
    return examples("invalid", "collect-envelope--");
  }

  static Stream<Path> validBatches() {
    return examples("valid", "collect-batch--");
  }

  static Stream<Path> invalidBatches() {
    return examples("invalid", "collect-batch--");
  }

  @ParameterizedTest
  @MethodSource("validEnvelopes")
  void all_valid_envelopes_pass(Path example) {
    assertThat(adapter.check(read(example))).isInstanceOf(EnvelopeCheck.Valid.class);
  }

  @ParameterizedTest
  @MethodSource("invalidEnvelopes")
  void invalid_envelopes_get_expected_reason(Path example) {
    String name = example.getFileName().toString();
    RejectionReason expected =
        name.contains("--unknown-event")
            ? RejectionReason.UNKNOWN_EVENT
            : name.contains("--source-mismatch")
                ? RejectionReason.SOURCE_MISMATCH
                : name.contains("--version-model-2")
                    ? RejectionReason.VERSION_REJECTED
                    : RejectionReason.SCHEMA_INVALID;

    assertThat(adapter.check(read(example)))
        .isInstanceOfSatisfying(
            EnvelopeCheck.Invalid.class,
            invalid -> assertThat(invalid.reason()).isEqualTo(expected));
  }

  @Test
  void unevaluated_properties_are_rejected() {
    assertThat(adapter.check(example("invalid", "collect-envelope--extra-top-level.json")))
        .isInstanceOfSatisfying(
            EnvelopeCheck.Invalid.class,
            invalid -> assertThat(invalid.detail()).contains("username"));
  }

  @Test
  void valid_check_parses_envelope_fields() {
    EnvelopeCheck check = adapter.check(example("valid", "collect-envelope--article-viewed.json"));

    assertThat(check)
        .isInstanceOfSatisfying(
            EnvelopeCheck.Valid.class,
            valid -> {
              CollectedEnvelope event = valid.event();
              assertThat(event.eventName()).isEqualTo(EventName.ARTICLE_VIEWED);
              assertThat(event.createdAt()).isEqualTo(Instant.parse("2026-11-01T08:30:10.120Z"));
              assertThat(event.userId()).isEqualTo("884213907612315648");
              assertThat(event.clientIp()).isEqualTo("203.0.113.24");
              assertThat(event.propertiesJson()).contains("\"39871245\"");
            });
  }

  @Test
  void article_id_is_parsed_into_envelope() {
    assertThat(eventFrom("collect-envelope--article-viewed.json").articleId()).isEqualTo(39871245L);
  }

  @Test
  void envelope_without_article_id_has_null() {
    assertThat(eventFrom("collect-envelope--page-viewed.json").articleId()).isNull();
  }

  @Test
  void oversized_article_id_is_null() {
    String oversized =
        example("valid", "collect-envelope--article-viewed.json")
            .replace("\"article_id\": \"39871245\"", "\"article_id\": \"9999999999999999999\"");

    assertThat(adapter.check(oversized))
        .isInstanceOfSatisfying(
            EnvelopeCheck.Valid.class, valid -> assertThat(valid.event().articleId()).isNull());
  }

  @ParameterizedTest
  @MethodSource("validBatches")
  void split_batch_accepts_valid_batches(Path example) {
    assertThat(adapter.splitBatch(read(example))).isNotEmpty();
  }

  @ParameterizedTest
  @MethodSource("invalidBatches")
  void split_batch_rejects_invalid_shells(Path example) {
    String body = read(example);
    assertThatThrownBy(() -> adapter.splitBatch(body)).isInstanceOf(InvalidBatchException.class);
  }

  @ParameterizedTest
  @ValueSource(strings = {"{", "", "null", "[]", "\"events\""})
  void split_batch_rejects_non_object_json(String body) {
    assertThatThrownBy(() -> adapter.splitBatch(body)).isInstanceOf(InvalidBatchException.class);
  }

  @Test
  void split_batch_returns_each_event_text() {
    List<String> events = adapter.splitBatch(example("valid", "collect-batch--two.json"));

    assertThat(events).hasSize(2);
    assertThat(adapter.check(events.getFirst())).isInstanceOf(EnvelopeCheck.Valid.class);
  }

  @Test
  void enriched_json_is_valid_and_has_no_ip() {
    CollectedEnvelope event = validEvent();

    JsonNode enriched = MAPPER.readTree(adapter.enrichedJson(event, enrichment("CN", "浙江省")));

    assertThat(enriched.has("client_ip")).isFalse();
    assertThat(enriched.has("user_agent")).isFalse();
    assertThat(enriched.get("province").asString()).isEqualTo("浙江省");
    assertThat(enriched.get("is_bot").asBoolean()).isFalse();
    assertThat(enriched.get("properties").get("article_id").asString()).isEqualTo("39871245");
  }

  @Test
  void enriched_json_rejects_inconsistent_fields() {
    CollectedEnvelope event = validEvent();

    assertThatThrownBy(() -> adapter.enrichedJson(event, enrichment("US", "浙江省")))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void dlq_json_truncates_after_removing_ip() {
    String raw =
        "{\"client_ip\":\"203.0.113.24\",\"user_agent\":\"Mozilla\",\"pad\":\""
            + "x".repeat(20_000)
            + "\"}";
    RejectedEnvelope rejected =
        RejectedEnvelope.builder()
            .reason(RejectionReason.SCHEMA_INVALID)
            .detail("/pad: too long")
            .eventName(null)
            .rawJson(raw)
            .receivedAt(Instant.parse("2026-11-01T08:30:12.402Z"))
            .build();

    JsonNode dlq = MAPPER.readTree(adapter.dlqJson(rejected));

    assertThat(dlq.get("raw").asString()).hasSize(16_384).doesNotContain("client_ip", "user_agent");
    assertThat(dlq.get("raw_truncated").asBoolean()).isTrue();
    assertThat(dlq.get("reason_code").asString()).isEqualTo("schema_invalid");
    assertThat(dlq.get("event_name").isNull()).isTrue();
  }

  @Test
  void impossible_calendar_date_is_rejected_not_thrown() {
    String feb30 =
        example("valid", "collect-envelope--article-viewed.json")
            .replace("2026-11-01T08:30:10.120Z", "2026-02-30T08:30:10.120Z");

    assertThat(adapter.check(feb30))
        .isInstanceOfSatisfying(
            EnvelopeCheck.Invalid.class,
            invalid -> assertThat(invalid.reason()).isEqualTo(RejectionReason.SCHEMA_INVALID));
  }

  @Test
  void dlq_json_truncates_long_event_name() {
    RejectedEnvelope rejected =
        RejectedEnvelope.builder()
            .reason(RejectionReason.UNKNOWN_EVENT)
            .detail("event_name 不在事件字典里")
            .eventName("x".repeat(100))
            .rawJson("{}")
            .receivedAt(Instant.parse("2026-11-01T08:30:12.402Z"))
            .build();

    assertThat(MAPPER.readTree(adapter.dlqJson(rejected)).get("event_name").asString()).hasSize(64);
  }

  private CollectedEnvelope eventFrom(String file) {
    return ((EnvelopeCheck.Valid) adapter.check(example("valid", file))).event();
  }

  private CollectedEnvelope validEvent() {
    EnvelopeCheck check = adapter.check(example("valid", "collect-envelope--article-viewed.json"));
    return ((EnvelopeCheck.Valid) check).event();
  }

  private static EventEnrichment enrichment(String countryCode, String province) {
    return EventEnrichment.builder()
        .receivedAt(Instant.parse("2026-11-01T08:30:12.402Z"))
        .derivedAt(Instant.parse("2026-11-01T08:30:10.177Z"))
        .geoLocation(GeoLocation.of(countryCode, province))
        .userAgent(UserAgentInfo.of("Chrome", "Mac OS", "Desktop"))
        .botReason(null)
        .build();
  }

  private static String example(String kind, String file) {
    try (InputStream in =
        NetworkntEventSchemaAdapterTest.class
            .getClassLoader()
            .getResourceAsStream("analytics/examples/" + kind + "/" + file)) {
      if (in == null) {
        throw new IllegalStateException("样例不存在：" + file);
      }
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static Stream<Path> examples(String kind, String prefix) {
    URL dir =
        NetworkntEventSchemaAdapterTest.class
            .getClassLoader()
            .getResource("analytics/examples/" + kind);
    try {
      URI uri = dir.toURI();
      Path root;
      if ("jar".equals(uri.getScheme())) {
        FileSystem jar = jarFileSystem(uri);
        root = jar.getPath("analytics/examples/" + kind);
      } else {
        root = Path.of(uri);
      }
      try (Stream<Path> files = Files.list(root)) {
        List<Path> matched =
            files.filter(p -> p.getFileName().toString().startsWith(prefix)).sorted().toList();
        assertThat(matched).isNotEmpty();
        return matched.stream();
      }
    } catch (IOException | URISyntaxException e) {
      throw new IllegalStateException(e);
    }
  }

  private static synchronized FileSystem jarFileSystem(URI uri) throws IOException {
    try {
      return FileSystems.getFileSystem(uri);
    } catch (FileSystemNotFoundException e) {
      return FileSystems.newFileSystem(uri, Map.of());
    }
  }

  private static String read(Path path) {
    try {
      return Files.readString(path, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
