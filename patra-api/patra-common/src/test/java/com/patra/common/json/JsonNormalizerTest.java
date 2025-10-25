package com.patra.common.json;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class JsonNormalizerTest {

  private final ObjectMapper om = JsonMapperHolder.getObjectMapper();

  @Test
  void normalize_basic_object_and_string_policies() {
    JsonNormalizerConfig cfg =
        JsonNormalizerConfig.builder()
            .lowercaseFields(Set.of("name"))
            .keepEmptyWhitelist(Set.of("$.keep", "meta"))
            .build();
    JsonNormalizer normalizer = JsonNormalizer.withConfig(cfg);

    Map<String, Object> input = new LinkedHashMap<>();
    input.put("name", "  Alice  "); // Should be trimmed and lowercased
    input.put("desc", "a   b\t c\n"); // Collapses repeated whitespace
    input.put("emptyStr", "  "); // -> empty string, removed by removeEmpty
    input.put("keep", ""); // Whitelisted field stays
    input.put("meta", Map.of()); // Whitelisted empty object stays

    JsonNormalizerResult r = normalizer.normalize(input);
    String json0 = r.getCanonicalJson();
    assertThat(json0)
        .contains("\"desc\":\"a b c\"")
        .contains("\"keep\":\"\"")
        .contains("\"meta\":{}")
        .contains("\"name\":\"alice\"");
    @SuppressWarnings("unchecked")
    Map<String, Object> canonical = (Map<String, Object>) r.getCanonicalValue();
    assertThat(canonical).containsKeys("name", "desc", "keep", "meta");
  }

  @Test
  void normalize_array_dedup_sort_vs_sequence_whitelist() {
    JsonNormalizerConfig cfg =
        JsonNormalizerConfig.builder()
            .sequenceFieldWhitelist(Set.of("seq")) // Keep seq array order; skip dedupe/global sort
            .coerceTime(false)
            .coerceNumber(false)
            .coerceBoolean(JsonNormalizerConfig.CoerceBoolean.NONE)
            .build();
    JsonNormalizer normalizer = JsonNormalizer.withConfig(cfg);

    Map<String, Object> input = new LinkedHashMap<>();
    input.put("seq", List.of(3, 2, 2, 1));
    input.put("mix", List.of("b", "a", 2, 1, 1, Map.of("k", 1)));

    JsonNormalizerResult r = normalizer.normalize(input);
    String canonicalJson = r.getCanonicalJson();
    // seq: preserved as-is (no dedupe)
    assertThat(canonicalJson).contains("\"seq\":[3,2,2,1]");

    // mix: deduped and sorted by type tag then serialized value -> numbers(1,2), strings(a,b),
    // object
    assertThat(canonicalJson).contains("\"mix\":[1,2,\"a\",\"b\",{\"k\":1}]");
  }

  @Test
  void forbidden_key_and_maxDepth_and_string_limit() throws Exception {
    JsonNormalizerConfig cfg =
        JsonNormalizerConfig.builder()
            .forbidKeys(Set.of("forbidden"))
            .maxDepth(1)
            .maxStringBytes(4)
            .build();
    JsonNormalizer normalizer = JsonNormalizer.withConfig(cfg);

    // Forbidden key
    Map<String, Object> input1 = Map.of("forbidden", 1);
    assertThatThrownBy(() -> normalizer.normalize(input1))
        .isInstanceOf(JsonNormalizationException.class)
        .hasMessageContaining("Forbidden key");

    // Depth overflow (root depth=1, next level exceeds limit)
    Map<String, Object> input2 = Map.of("a", Map.of("b", 1));
    assertThatThrownBy(() -> normalizer.normalize(input2))
        .isInstanceOf(JsonNormalizationException.class)
        .hasMessageContaining("Max depth exceeded");

    // String length overflow (UTF-8 bytes) — use greater depth limit to avoid depth failure
    JsonNormalizer normalizer2 =
        JsonNormalizer.withConfig(
            JsonNormalizerConfig.builder()
                .forbidKeys(Set.of("forbidden"))
                .maxDepth(3)
                .maxStringBytes(4)
                .build());
    Map<String, Object> input3 = Map.of("long", "hello");
    assertThatThrownBy(() -> normalizer2.normalize(input3))
        .isInstanceOf(JsonNormalizationException.class)
        .hasMessageContaining("String length exceeds limit");
  }

  @Test
  void coerce_boolean_number_time_and_sanitize_decimal() throws Exception {
    JsonNormalizerConfig cfg =
        JsonNormalizerConfig.builder()
            .coerceBoolean(JsonNormalizerConfig.CoerceBoolean.LOOSE)
            .coerceNumber(true)
            .coerceTime(true)
            .defaultZoneId(ZoneId.of("UTC"))
            .build();
    JsonNormalizer normalizer = JsonNormalizer.withConfig(cfg);

    JsonNode node =
        om.readTree(
            "{\n"
                + "  \"tTrue\": \"YES\",\n"
                + "  \"tFalse\": \"0\",\n"
                + "  \"ts1\": \"1700000000\",\n"
                + // epoch seconds
                "  \"ts2\": \"1700000000000\",\n"
                + // epoch milliseconds
                "  \"fmt\": \"2024-05-01T12:34:56Z\",\n"
                + "  \"num\": \"001.2300\",\n"
                + "  \"num2\": 1,\n"
                + "  \"bin\": \"AQID\"\n"
                + "}");

    JsonNormalizerResult r = normalizer.normalize(node);
    String json = r.getCanonicalJson();
    // Booleans are coerced
    assertThat(json).contains("\"tTrue\":true");
    assertThat(json).contains("\"tFalse\":false");
    // Time fields formatted to UTC with millisecond precision
    assertThat(json)
        .containsPattern("\"ts1\":\"\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}Z\"");
    assertThat(json).contains("\"fmt\":\"2024-05-01T12:34:56.000Z\"");
    // Numeric strings normalized via BigDecimal and stripped of trailing zeros
    assertThat(json).contains("\"num\":1.23");
    // Numeric 0/1 treated as booleans in LOOSE mode
    assertThat(json).contains("\"num2\":true");

    // Binary: base64 input parses to text node, so manually build a binary node to cover the branch
    JsonNormalizerResult r2 =
        normalizer.normalize(JsonNodeFactory.instance.binaryNode(new byte[] {1, 2, 3}));
    assertThat(r2.getCanonicalJson()).isEqualTo("\"AQID\"");
  }

  @Test
  void nonFinite_number_should_throw_and_epoch_range_guard() {
    JsonNormalizer normalizer = JsonNormalizer.usingDefault();
    // Non-finite floating point number
    assertThatThrownBy(() -> normalizer.normalize(JsonNodeFactory.instance.numberNode(Double.NaN)))
        .isInstanceOf(JsonNormalizationException.class)
        .hasMessageContaining("Non-finite number");

    // Oversized epoch triggers guardrail
    JsonNormalizerConfig cfg = JsonNormalizerConfig.builder().coerceTime(true).build();
    JsonNormalizer n2 = JsonNormalizer.withConfig(cfg);
    assertThatThrownBy(() -> n2.normalize(Map.of("t", "99999999999999")))
        .isInstanceOf(JsonNormalizationException.class)
        .hasMessageContaining("Epoch value out of range");
  }

  @Test
  void matches_field_and_path_should_work_with_arrays() {
    JsonNormalizerConfig cfg =
        JsonNormalizerConfig.builder()
            .lowercaseFields(Set.of("items", "items.name", "$.items[].name"))
            .build();
    JsonNormalizer normalizer = JsonNormalizer.withConfig(cfg);

    Map<String, Object> input =
        Map.of("items", List.of(Map.of("name", " A "), Map.of("name", "B ")));
    JsonNormalizerResult r = normalizer.normalize(input);
    assertThat(r.getCanonicalJson()).contains("\"name\":\"a\"").contains("\"name\":\"b\"");
  }
}
