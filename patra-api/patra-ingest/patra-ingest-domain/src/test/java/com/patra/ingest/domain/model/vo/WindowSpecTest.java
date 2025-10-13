package com.patra.ingest.domain.model.vo;

import static org.assertj.core.api.Assertions.*;

import com.patra.ingest.domain.model.enums.SliceStrategy;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Unit tests for {@link WindowSpec} and its implementations.
 *
 * <p>Tests cover Format B serialization/deserialization, boundary conditions, and exception
 * scenarios for all strategy types.
 *
 * @author linqibin
 * @since 0.1.0
 */
@DisplayName("WindowSpec Tests")
class WindowSpecTest {

  // ============ Time Strategy Tests ============

  @Test
  @DisplayName("should_serialize_time_window_to_format_b_map_when_valid_instants_provided")
  void timeToMap_normalCase() {
    // given
    Instant from = Instant.parse("2024-01-01T00:00:00Z");
    Instant to = Instant.parse("2024-12-31T23:59:59Z");
    WindowSpec.Time spec = new WindowSpec.Time(from, to);

    // when
    Map<String, Object> map = spec.toMap();

    // then
    assertThat(map).containsEntry("strategy", "TIME").containsKey("window");

    @SuppressWarnings("unchecked")
    Map<String, Object> windowMap = (Map<String, Object>) map.get("window");
    assertThat(windowMap)
        .containsEntry("from", "2024-01-01T00:00:00Z")
        .containsEntry("to", "2024-12-31T23:59:59Z")
        .containsEntry("timezone", "UTC")
        .containsKey("boundary");

    @SuppressWarnings("unchecked")
    Map<String, Object> boundaryMap = (Map<String, Object>) windowMap.get("boundary");
    assertThat(boundaryMap).containsEntry("from", "CLOSED").containsEntry("to", "OPEN");
  }

  @Test
  @DisplayName("should_serialize_time_window_when_boundary_instants_provided")
  void timeToMap_boundaryInstants() {
    // given - test Instant.MIN and Instant.MAX
    WindowSpec.Time spec1 = new WindowSpec.Time(Instant.MIN, Instant.MAX);
    WindowSpec.Time spec2 = new WindowSpec.Time(Instant.now(), Instant.now());

    // when
    Map<String, Object> map1 = spec1.toMap();
    Map<String, Object> map2 = spec2.toMap();

    // then
    assertThat(map1).containsEntry("strategy", "TIME");
    assertThat(map2).containsEntry("strategy", "TIME");
  }

  @Test
  @DisplayName("should_reject_time_window_when_from_is_after_to")
  void timeConstruction_fromAfterTo() {
    // given
    Instant from = Instant.parse("2024-12-31T23:59:59Z");
    Instant to = Instant.parse("2024-01-01T00:00:00Z");

    // when/then
    assertThatThrownBy(() -> new WindowSpec.Time(from, to))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("from must be before or equal to to");
  }

  @Test
  @DisplayName("should_reject_time_window_when_from_or_to_is_null")
  void timeConstruction_nullValues() {
    // when/then
    assertThatThrownBy(() -> new WindowSpec.Time(null, Instant.now()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("requires both from and to");

    assertThatThrownBy(() -> new WindowSpec.Time(Instant.now(), null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("requires both from and to");
  }

  @Test
  @DisplayName("should_deserialize_time_window_from_format_b_map_when_valid_structure")
  void timeFromMap_normalCase() {
    // given
    Map<String, Object> map =
        Map.of(
            "strategy",
            "TIME",
            "window",
            Map.of(
                "from", "2024-01-01T00:00:00Z",
                "to", "2024-12-31T23:59:59Z",
                "boundary", Map.of("from", "CLOSED", "to", "OPEN"),
                "timezone", "UTC"));

    // when
    WindowSpec spec = WindowSpec.fromMap(map);

    // then
    assertThat(spec).isInstanceOf(WindowSpec.Time.class);
    WindowSpec.Time timeSpec = (WindowSpec.Time) spec;
    assertThat(timeSpec.from()).isEqualTo(Instant.parse("2024-01-01T00:00:00Z"));
    assertThat(timeSpec.to()).isEqualTo(Instant.parse("2024-12-31T23:59:59Z"));
    assertThat(timeSpec.strategy()).isEqualTo(SliceStrategy.TIME);
  }

  @Test
  @DisplayName("should_complete_roundtrip_serialization_for_time_window")
  void timeRoundtrip() {
    // given
    Instant from = Instant.parse("2024-06-15T12:30:45Z");
    Instant to = Instant.parse("2024-06-15T18:45:30Z");
    WindowSpec.Time original = new WindowSpec.Time(from, to);

    // when
    Map<String, Object> map = original.toMap();
    WindowSpec reconstructed = WindowSpec.fromMap(map);

    // then
    assertThat(reconstructed).isEqualTo(original);
  }

  // ============ IdRange Strategy Tests ============

  @Test
  @DisplayName("should_serialize_id_range_window_to_format_b_map_when_valid_ids_provided")
  void idRangeToMap_normalCase() {
    // given
    WindowSpec.IdRange spec = new WindowSpec.IdRange(1000000L, 2000000L);

    // when
    Map<String, Object> map = spec.toMap();

    // then
    assertThat(map).containsEntry("strategy", "ID_RANGE").containsKey("window");

    @SuppressWarnings("unchecked")
    Map<String, Object> windowMap = (Map<String, Object>) map.get("window");
    assertThat(windowMap).containsEntry("from", 1000000L).containsEntry("to", 2000000L);
  }

  @Test
  @DisplayName("should_serialize_id_range_when_boundary_values_provided")
  void idRangeToMap_boundaryValues() {
    // given - test Long.MIN_VALUE, Long.MAX_VALUE, zero, negative
    WindowSpec.IdRange spec1 = new WindowSpec.IdRange(Long.MIN_VALUE, Long.MAX_VALUE);
    WindowSpec.IdRange spec2 = new WindowSpec.IdRange(0L, 0L);
    WindowSpec.IdRange spec3 = new WindowSpec.IdRange(-1000L, 1000L);

    // when
    Map<String, Object> map1 = spec1.toMap();
    Map<String, Object> map2 = spec2.toMap();
    Map<String, Object> map3 = spec3.toMap();

    // then
    assertThat(map1).containsEntry("strategy", "ID_RANGE");
    assertThat(map2).containsEntry("strategy", "ID_RANGE");
    assertThat(map3).containsEntry("strategy", "ID_RANGE");
  }

  @Test
  @DisplayName("should_reject_id_range_when_from_is_greater_than_to")
  void idRangeConstruction_fromGreaterThanTo() {
    // when/then
    assertThatThrownBy(() -> new WindowSpec.IdRange(2000000L, 1000000L))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("from must be less than or equal to to");
  }

  @Test
  @DisplayName("should_reject_id_range_when_from_or_to_is_null")
  void idRangeConstruction_nullValues() {
    // when/then
    assertThatThrownBy(() -> new WindowSpec.IdRange(null, 100L))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("requires both from and to");

    assertThatThrownBy(() -> new WindowSpec.IdRange(100L, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("requires both from and to");
  }

  @Test
  @DisplayName("should_deserialize_id_range_from_format_b_map_when_valid_structure")
  void idRangeFromMap_normalCase() {
    // given
    Map<String, Object> map =
        Map.of(
            "strategy",
            "ID_RANGE",
            "window",
            Map.of(
                "from", 1000000,
                "to", 2000000));

    // when
    WindowSpec spec = WindowSpec.fromMap(map);

    // then
    assertThat(spec).isInstanceOf(WindowSpec.IdRange.class);
    WindowSpec.IdRange idRangeSpec = (WindowSpec.IdRange) spec;
    assertThat(idRangeSpec.from()).isEqualTo(1000000L);
    assertThat(idRangeSpec.to()).isEqualTo(2000000L);
    assertThat(idRangeSpec.strategy()).isEqualTo(SliceStrategy.ID_RANGE);
  }

  @Test
  @DisplayName("should_complete_roundtrip_serialization_for_id_range_window")
  void idRangeRoundtrip() {
    // given
    WindowSpec.IdRange original = new WindowSpec.IdRange(12345L, 67890L);

    // when
    Map<String, Object> map = original.toMap();
    WindowSpec reconstructed = WindowSpec.fromMap(map);

    // then
    assertThat(reconstructed).isEqualTo(original);
  }

  // ============ CursorLandmark Strategy Tests ============

  @Test
  @DisplayName("should_serialize_cursor_window_to_format_b_map_when_valid_cursors_provided")
  void cursorToMap_normalCase() {
    // given
    WindowSpec.CursorLandmark spec =
        new WindowSpec.CursorLandmark("cursor_token_1", "cursor_token_2");

    // when
    Map<String, Object> map = spec.toMap();

    // then
    assertThat(map).containsEntry("strategy", "CURSOR_LANDMARK").containsKey("window");

    @SuppressWarnings("unchecked")
    Map<String, Object> windowMap = (Map<String, Object>) map.get("window");
    assertThat(windowMap)
        .containsEntry("from", "cursor_token_1")
        .containsEntry("to", "cursor_token_2");
  }

  @Test
  @DisplayName("should_serialize_cursor_window_when_special_characters_provided")
  void cursorToMap_specialCharacters() {
    // given - test special characters, long strings, same cursors
    WindowSpec.CursorLandmark spec1 = new WindowSpec.CursorLandmark("!@#$%^&*()", "abc123_-+=[]{}");
    WindowSpec.CursorLandmark spec2 =
        new WindowSpec.CursorLandmark("a".repeat(1000), "b".repeat(1000));
    WindowSpec.CursorLandmark spec3 = new WindowSpec.CursorLandmark("same", "same");

    // when
    Map<String, Object> map1 = spec1.toMap();
    Map<String, Object> map2 = spec2.toMap();
    Map<String, Object> map3 = spec3.toMap();

    // then
    assertThat(map1).containsEntry("strategy", "CURSOR_LANDMARK");
    assertThat(map2).containsEntry("strategy", "CURSOR_LANDMARK");
    assertThat(map3).containsEntry("strategy", "CURSOR_LANDMARK");
  }

  @Test
  @DisplayName("should_reject_cursor_window_when_from_or_to_is_blank")
  void cursorConstruction_blankValues() {
    // when/then
    assertThatThrownBy(() -> new WindowSpec.CursorLandmark("", "token"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("require non-blank from and to");

    assertThatThrownBy(() -> new WindowSpec.CursorLandmark("token", "   "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("require non-blank from and to");

    assertThatThrownBy(() -> new WindowSpec.CursorLandmark(null, "token"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("require non-blank from and to");
  }

  @Test
  @DisplayName("should_deserialize_cursor_window_from_format_b_map_when_valid_structure")
  void cursorFromMap_normalCase() {
    // given
    Map<String, Object> map =
        Map.of(
            "strategy",
            "CURSOR_LANDMARK",
            "window",
            Map.of(
                "from", "start_token",
                "to", "end_token"));

    // when
    WindowSpec spec = WindowSpec.fromMap(map);

    // then
    assertThat(spec).isInstanceOf(WindowSpec.CursorLandmark.class);
    WindowSpec.CursorLandmark cursorSpec = (WindowSpec.CursorLandmark) spec;
    assertThat(cursorSpec.from()).isEqualTo("start_token");
    assertThat(cursorSpec.to()).isEqualTo("end_token");
    assertThat(cursorSpec.strategy()).isEqualTo(SliceStrategy.CURSOR_LANDMARK);
  }

  @Test
  @DisplayName("should_complete_roundtrip_serialization_for_cursor_window")
  void cursorRoundtrip() {
    // given
    WindowSpec.CursorLandmark original =
        new WindowSpec.CursorLandmark("page_1_token", "page_100_token");

    // when
    Map<String, Object> map = original.toMap();
    WindowSpec reconstructed = WindowSpec.fromMap(map);

    // then
    assertThat(reconstructed).isEqualTo(original);
  }

  // ============ VolumeBudget Strategy Tests ============

  @Test
  @DisplayName("should_serialize_volume_budget_to_flat_format_when_valid_limit_provided")
  void volumeBudgetToMap_normalCase() {
    // given
    WindowSpec.VolumeBudget spec = new WindowSpec.VolumeBudget(100000, "RECORDS");

    // when
    Map<String, Object> map = spec.toMap();

    // then
    assertThat(map)
        .containsEntry("strategy", "VOLUME_BUDGET")
        .containsEntry("limit", 100000)
        .containsEntry("unit", "RECORDS");
    assertThat(map).doesNotContainKey("window"); // flat format
  }

  @ParameterizedTest
  @ValueSource(strings = {"RECORDS", "BYTES", "PAGES", "ITEMS", "MB"})
  @DisplayName("should_serialize_volume_budget_when_different_units_provided")
  void volumeBudgetToMap_differentUnits(String unit) {
    // given
    WindowSpec.VolumeBudget spec = new WindowSpec.VolumeBudget(50000, unit);

    // when
    Map<String, Object> map = spec.toMap();

    // then
    assertThat(map).containsEntry("strategy", "VOLUME_BUDGET").containsEntry("unit", unit);
  }

  @Test
  @DisplayName("should_serialize_volume_budget_when_boundary_limits_provided")
  void volumeBudgetToMap_boundaryLimits() {
    // given
    WindowSpec.VolumeBudget spec1 = new WindowSpec.VolumeBudget(1, "RECORDS");
    WindowSpec.VolumeBudget spec2 = new WindowSpec.VolumeBudget(Integer.MAX_VALUE, "BYTES");

    // when
    Map<String, Object> map1 = spec1.toMap();
    Map<String, Object> map2 = spec2.toMap();

    // then
    assertThat(map1).containsEntry("limit", 1);
    assertThat(map2).containsEntry("limit", Integer.MAX_VALUE);
  }

  @Test
  @DisplayName("should_reject_volume_budget_when_limit_is_zero_or_negative")
  void volumeBudgetConstruction_invalidLimit() {
    // when/then
    assertThatThrownBy(() -> new WindowSpec.VolumeBudget(0, "RECORDS"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Volume limit must be positive");

    assertThatThrownBy(() -> new WindowSpec.VolumeBudget(-100, "RECORDS"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Volume limit must be positive");

    assertThatThrownBy(() -> new WindowSpec.VolumeBudget(null, "RECORDS"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Volume limit must be positive");
  }

  @Test
  @DisplayName("should_reject_volume_budget_when_unit_is_blank_or_null")
  void volumeBudgetConstruction_invalidUnit() {
    // when/then
    assertThatThrownBy(() -> new WindowSpec.VolumeBudget(1000, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Volume unit is required");

    assertThatThrownBy(() -> new WindowSpec.VolumeBudget(1000, ""))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Volume unit is required");

    assertThatThrownBy(() -> new WindowSpec.VolumeBudget(1000, "   "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Volume unit is required");
  }

  @Test
  @DisplayName("should_deserialize_volume_budget_from_flat_format_when_valid_structure")
  void volumeBudgetFromMap_normalCase() {
    // given
    Map<String, Object> map =
        Map.of(
            "strategy", "VOLUME_BUDGET",
            "limit", 100000,
            "unit", "RECORDS");

    // when
    WindowSpec spec = WindowSpec.fromMap(map);

    // then
    assertThat(spec).isInstanceOf(WindowSpec.VolumeBudget.class);
    WindowSpec.VolumeBudget volumeSpec = (WindowSpec.VolumeBudget) spec;
    assertThat(volumeSpec.limit()).isEqualTo(100000);
    assertThat(volumeSpec.unit()).isEqualTo("RECORDS");
    assertThat(volumeSpec.strategy()).isEqualTo(SliceStrategy.VOLUME_BUDGET);
  }

  @Test
  @DisplayName("should_complete_roundtrip_serialization_for_volume_budget")
  void volumeBudgetRoundtrip() {
    // given
    WindowSpec.VolumeBudget original = new WindowSpec.VolumeBudget(50000, "PAGES");

    // when
    Map<String, Object> map = original.toMap();
    WindowSpec reconstructed = WindowSpec.fromMap(map);

    // then
    assertThat(reconstructed).isEqualTo(original);
  }

  // ============ Single Strategy Tests ============

  @Test
  @DisplayName("should_serialize_single_strategy_to_minimal_map")
  void singleToMap() {
    // given
    WindowSpec.Single spec = new WindowSpec.Single();

    // when
    Map<String, Object> map = spec.toMap();

    // then
    assertThat(map).containsEntry("strategy", "SINGLE").hasSize(1);
  }

  @Test
  @DisplayName("should_deserialize_single_strategy_from_minimal_map")
  void singleFromMap() {
    // given
    Map<String, Object> map = Map.of("strategy", "SINGLE");

    // when
    WindowSpec spec = WindowSpec.fromMap(map);

    // then
    assertThat(spec).isInstanceOf(WindowSpec.Single.class);
    assertThat(spec.strategy()).isEqualTo(SliceStrategy.SINGLE);
  }

  @Test
  @DisplayName("should_complete_roundtrip_serialization_for_single_strategy")
  void singleRoundtrip() {
    // given
    WindowSpec.Single original = new WindowSpec.Single();

    // when
    Map<String, Object> map = original.toMap();
    WindowSpec reconstructed = WindowSpec.fromMap(map);

    // then
    assertThat(reconstructed).isEqualTo(original);
  }

  // ============ fromMap() Exception Tests ============

  @Test
  @DisplayName("should_reject_fromMap_when_map_is_null")
  void fromMap_nullMap() {
    // when/then
    assertThatThrownBy(() -> WindowSpec.fromMap(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Window spec map cannot be null or empty");
  }

  @Test
  @DisplayName("should_reject_fromMap_when_map_is_empty")
  void fromMap_emptyMap() {
    // when/then
    assertThatThrownBy(() -> WindowSpec.fromMap(Map.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Window spec map cannot be null or empty");
  }

  @Test
  @DisplayName("should_reject_fromMap_when_strategy_key_is_missing")
  void fromMap_missingStrategy() {
    // given
    Map<String, Object> map = Map.of("window", Map.of("from", "2024-01-01", "to", "2024-12-31"));

    // when/then
    assertThatThrownBy(() -> WindowSpec.fromMap(map))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must contain 'strategy' key");
  }

  @Test
  @DisplayName("should_reject_fromMap_when_strategy_value_is_null")
  void fromMap_nullStrategy() {
    // given
    Map<String, Object> map = new HashMap<>();
    map.put("strategy", null);

    // when/then
    assertThatThrownBy(() -> WindowSpec.fromMap(map))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must contain 'strategy' key");
  }

  @Test
  @DisplayName("should_reject_fromMap_when_strategy_is_not_string")
  void fromMap_nonStringStrategy() {
    // given
    Map<String, Object> map = Map.of("strategy", 12345);

    // when/then
    assertThatThrownBy(() -> WindowSpec.fromMap(map))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("'strategy' must be a string");
  }

  @Test
  @DisplayName("should_reject_fromMap_when_strategy_code_is_unknown")
  void fromMap_unknownStrategyCode() {
    // given
    Map<String, Object> map = Map.of("strategy", "UNKNOWN_STRATEGY");

    // when/then
    assertThatThrownBy(() -> WindowSpec.fromMap(map))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Unknown slice strategy code: 'UNKNOWN_STRATEGY'");
  }

  @Test
  @DisplayName("should_reject_fromMap_when_time_window_object_is_missing")
  void fromMap_time_missingWindow() {
    // given
    Map<String, Object> map = Map.of("strategy", "TIME");

    // when/then
    assertThatThrownBy(() -> WindowSpec.fromMap(map))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("TIME strategy requires 'window' object");
  }

  @Test
  @DisplayName("should_reject_fromMap_when_time_window_is_not_a_map")
  void fromMap_time_windowNotMap() {
    // given
    Map<String, Object> map =
        Map.of(
            "strategy", "TIME",
            "window", "invalid_window_string");

    // when/then
    assertThatThrownBy(() -> WindowSpec.fromMap(map))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("TIME 'window' must be a JSON object");
  }

  @Test
  @DisplayName("should_reject_fromMap_when_time_window_from_is_missing")
  void fromMap_time_missingFrom() {
    // given
    Map<String, Object> map =
        Map.of("strategy", "TIME", "window", Map.of("to", "2024-12-31T23:59:59Z"));

    // when/then
    assertThatThrownBy(() -> WindowSpec.fromMap(map))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("TIME window requires both 'from' and 'to' fields");
  }

  @Test
  @DisplayName("should_reject_fromMap_when_time_window_from_is_not_string")
  void fromMap_time_fromNotString() {
    // given
    Map<String, Object> map =
        Map.of("strategy", "TIME", "window", Map.of("from", 12345, "to", "2024-12-31T23:59:59Z"));

    // when/then
    assertThatThrownBy(() -> WindowSpec.fromMap(map))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("TIME window 'from' and 'to' must be ISO-8601 timestamp strings");
  }

  @Test
  @DisplayName("should_reject_fromMap_when_time_window_from_is_invalid_iso8601")
  void fromMap_time_invalidIso8601() {
    // given
    Map<String, Object> map =
        Map.of(
            "strategy",
            "TIME",
            "window",
            Map.of(
                "from", "not-a-valid-date",
                "to", "2024-12-31T23:59:59Z"));

    // when/then
    assertThatThrownBy(() -> WindowSpec.fromMap(map)).isInstanceOf(DateTimeParseException.class);
  }

  @Test
  @DisplayName("should_reject_fromMap_when_id_range_window_from_is_not_number")
  void fromMap_idRange_fromNotNumber() {
    // given
    Map<String, Object> map =
        Map.of("strategy", "ID_RANGE", "window", Map.of("from", "not_a_number", "to", 2000000));

    // when/then
    assertThatThrownBy(() -> WindowSpec.fromMap(map))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("ID_RANGE window 'from' and 'to' must be numeric values");
  }

  @Test
  @DisplayName("should_reject_fromMap_when_cursor_window_to_is_not_string")
  void fromMap_cursor_toNotString() {
    // given
    Map<String, Object> map =
        Map.of("strategy", "CURSOR_LANDMARK", "window", Map.of("from", "start_token", "to", 12345));

    // when/then
    assertThatThrownBy(() -> WindowSpec.fromMap(map))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("CURSOR_LANDMARK window 'from' and 'to' must be string values");
  }

  @Test
  @DisplayName("should_reject_fromMap_when_volume_budget_limit_is_missing")
  void fromMap_volumeBudget_missingLimit() {
    // given
    Map<String, Object> map =
        Map.of(
            "strategy", "VOLUME_BUDGET",
            "unit", "RECORDS");

    // when/then
    assertThatThrownBy(() -> WindowSpec.fromMap(map))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("VOLUME_BUDGET strategy requires both 'limit' and 'unit' fields");
  }

  @Test
  @DisplayName("should_reject_fromMap_when_volume_budget_limit_is_not_number")
  void fromMap_volumeBudget_limitNotNumber() {
    // given
    Map<String, Object> map =
        Map.of(
            "strategy", "VOLUME_BUDGET",
            "limit", "not_a_number",
            "unit", "RECORDS");

    // when/then
    assertThatThrownBy(() -> WindowSpec.fromMap(map))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("VOLUME_BUDGET 'limit' must be a numeric value");
  }

  @Test
  @DisplayName("should_reject_fromMap_when_volume_budget_unit_is_not_string")
  void fromMap_volumeBudget_unitNotString() {
    // given
    Map<String, Object> map =
        Map.of(
            "strategy", "VOLUME_BUDGET",
            "limit", 100000,
            "unit", 12345);

    // when/then
    assertThatThrownBy(() -> WindowSpec.fromMap(map))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("VOLUME_BUDGET 'unit' must be a string value");
  }

  // ============ Factory Method Tests ============

  @Test
  @DisplayName("should_create_instances_via_factory_methods")
  void factoryMethods() {
    // given/when
    WindowSpec timeSpec = WindowSpec.ofTime(Instant.now(), Instant.now().plusSeconds(3600));
    WindowSpec idRangeSpec = WindowSpec.ofIdRange(1L, 1000L);
    WindowSpec cursorSpec = WindowSpec.ofCursor("start", "end");
    WindowSpec volumeSpec = WindowSpec.ofVolume(5000, "RECORDS");
    WindowSpec singleSpec = WindowSpec.ofSingle();

    // then
    assertThat(timeSpec).isInstanceOf(WindowSpec.Time.class);
    assertThat(idRangeSpec).isInstanceOf(WindowSpec.IdRange.class);
    assertThat(cursorSpec).isInstanceOf(WindowSpec.CursorLandmark.class);
    assertThat(volumeSpec).isInstanceOf(WindowSpec.VolumeBudget.class);
    assertThat(singleSpec).isInstanceOf(WindowSpec.Single.class);
  }

  // ============ Equals/HashCode Consistency Tests ============

  @Test
  @DisplayName("should_maintain_equals_and_hashcode_consistency")
  void equalsHashCodeConsistency() {
    // given
    Instant from = Instant.parse("2024-01-01T00:00:00Z");
    Instant to = Instant.parse("2024-12-31T23:59:59Z");
    WindowSpec.Time spec1 = new WindowSpec.Time(from, to);
    WindowSpec.Time spec2 = new WindowSpec.Time(from, to);
    WindowSpec.Time spec3 = new WindowSpec.Time(from.plusSeconds(1), to);

    // then
    assertThat(spec1).isEqualTo(spec2);
    assertThat(spec1.hashCode()).isEqualTo(spec2.hashCode());
    assertThat(spec1).isNotEqualTo(spec3);
  }

  // ============ toExecutionWindow() Compatibility Tests ============

  @Test
  @DisplayName("should_convert_time_spec_to_execution_window")
  void toExecutionWindow_timeStrategy() {
    // given
    Instant from = Instant.parse("2024-01-01T00:00:00Z");
    Instant to = Instant.parse("2024-12-31T23:59:59Z");
    WindowSpec.Time spec = new WindowSpec.Time(from, to);

    // when
    ExecutionWindow window = spec.toExecutionWindow();

    // then
    assertThat(window.windowFrom()).isEqualTo(from);
    assertThat(window.windowTo()).isEqualTo(to);
  }

  @Test
  @DisplayName("should_return_empty_execution_window_for_non_time_strategies")
  void toExecutionWindow_nonTimeStrategies() {
    // given
    WindowSpec.IdRange idRangeSpec = new WindowSpec.IdRange(1L, 1000L);
    WindowSpec.CursorLandmark cursorSpec = new WindowSpec.CursorLandmark("start", "end");
    WindowSpec.VolumeBudget volumeSpec = new WindowSpec.VolumeBudget(5000, "RECORDS");
    WindowSpec.Single singleSpec = new WindowSpec.Single();

    // when
    ExecutionWindow idRangeWindow = idRangeSpec.toExecutionWindow();
    ExecutionWindow cursorWindow = cursorSpec.toExecutionWindow();
    ExecutionWindow volumeWindow = volumeSpec.toExecutionWindow();
    ExecutionWindow singleWindow = singleSpec.toExecutionWindow();

    // then
    assertThat(idRangeWindow).isEqualTo(ExecutionWindow.empty());
    assertThat(cursorWindow).isEqualTo(ExecutionWindow.empty());
    assertThat(volumeWindow).isEqualTo(ExecutionWindow.empty());
    assertThat(singleWindow).isEqualTo(ExecutionWindow.empty());
  }
}
