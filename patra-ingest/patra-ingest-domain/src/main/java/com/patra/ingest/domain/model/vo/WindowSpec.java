package com.patra.ingest.domain.model.vo;

import com.patra.ingest.domain.model.enums.SliceStrategy;
import java.time.Instant;
import java.util.Map;

/**
 * Window specification value object. Sealed hierarchy ensures compile-time exhaustiveness.
 *
 * <p>This represents the boundary specification for ingestion windows, supporting different
 * strategies for partitioning data collection:
 *
 * <ul>
 *   <li>TIME: Time-based windows (from/to timestamps)
 *   <li>ID_RANGE: ID-based windows (from/to IDs)
 *   <li>CURSOR_LANDMARK: Cursor/pagination-based windows
 *   <li>VOLUME_BUDGET: Volume-limited windows
 *   <li>SINGLE: Single window (no partitioning)
 * </ul>
 *
 * @author linqibin
 * @since 0.1.0
 */
public sealed interface WindowSpec
    permits WindowSpec.Time,
        WindowSpec.IdRange,
        WindowSpec.CursorLandmark,
        WindowSpec.VolumeBudget,
        WindowSpec.Single {

  /** Get the strategy type of this window specification. */
  SliceStrategy strategy();

  /**
   * Convert to JSON-serializable map for persistence layer.
   *
   * <p>The map structure varies by strategy type (Format B - nested JSON):
   *
   * <ul>
   *   <li>TIME:
   *       {"strategy":"TIME","window":{"from":"2024-01-01T00:00:00Z","to":"2024-12-31T23:59:59Z","boundary":{"from":"CLOSED","to":"OPEN"},"timezone":"UTC"}}
   *   <li>ID_RANGE: {"strategy":"ID_RANGE","window":{"from":1000000,"to":2000000}}
   *   <li>CURSOR_LANDMARK: {"strategy":"CURSOR_LANDMARK","window":{"from":"token1","to":"token2"}}
   *   <li>VOLUME_BUDGET: {"strategy":"VOLUME_BUDGET","limit":100000,"unit":"RECORDS"}
   *   <li>SINGLE: {"strategy":"SINGLE"}
   * </ul>
   *
   * @return JSON-serializable map containing strategy code and strategy-specific fields
   */
  Map<String, Object> toMap();

  // ============ Strategy Implementations ============

  /**
   * Time-based window specification.
   *
   * @param from start timestamp (inclusive)
   * @param to end timestamp (exclusive)
   */
  record Time(Instant from, Instant to) implements WindowSpec {
    public Time {
      if (from == null || to == null) {
        throw new IllegalArgumentException("Time window requires both from and to");
      }
      if (from.isAfter(to)) {
        throw new IllegalArgumentException("from must be before or equal to to");
      }
    }

    @Override
    public SliceStrategy strategy() {
      return SliceStrategy.TIME;
    }

    @Override
    public Map<String, Object> toMap() {
      Map<String, Object> boundaryMap =
          Map.of(
              "from", "CLOSED",
              "to", "OPEN");
      Map<String, Object> windowMap =
          Map.of(
              "from",
              from.toString(),
              "to",
              to.toString(),
              "boundary",
              boundaryMap,
              "timezone",
              "UTC");
      return Map.of("strategy", SliceStrategy.TIME.getCode(), "window", windowMap);
    }
  }

  /**
   * ID range-based window specification.
   *
   * @param from start ID (inclusive)
   * @param to end ID (inclusive)
   */
  record IdRange(Long from, Long to) implements WindowSpec {
    public IdRange {
      if (from == null || to == null) {
        throw new IllegalArgumentException("ID range requires both from and to");
      }
      if (from > to) {
        throw new IllegalArgumentException("from must be less than or equal to to");
      }
    }

    @Override
    public SliceStrategy strategy() {
      return SliceStrategy.ID_RANGE;
    }

    @Override
    public Map<String, Object> toMap() {
      Map<String, Object> windowMap =
          Map.of(
              "from", from,
              "to", to);
      return Map.of("strategy", SliceStrategy.ID_RANGE.getCode(), "window", windowMap);
    }
  }

  /**
   * Cursor/landmark-based window specification for pagination.
   *
   * @param from start cursor/token
   * @param to end cursor/token
   */
  record CursorLandmark(String from, String to) implements WindowSpec {
    public CursorLandmark {
      if (from == null || to == null || from.isBlank() || to.isBlank()) {
        throw new IllegalArgumentException("Cursor landmarks require non-blank from and to");
      }
    }

    @Override
    public SliceStrategy strategy() {
      return SliceStrategy.CURSOR_LANDMARK;
    }

    @Override
    public Map<String, Object> toMap() {
      Map<String, Object> windowMap =
          Map.of(
              "from", from,
              "to", to);
      return Map.of("strategy", SliceStrategy.CURSOR_LANDMARK.getCode(), "window", windowMap);
    }
  }

  /**
   * Volume budget-based window specification.
   *
   * @param limit maximum volume/count
   * @param unit unit of measurement (e.g., "RECORDS", "BYTES", "MB")
   */
  record VolumeBudget(Integer limit, String unit) implements WindowSpec {
    public VolumeBudget {
      if (limit == null || limit <= 0) {
        throw new IllegalArgumentException("Volume limit must be positive");
      }
      if (unit == null || unit.isBlank()) {
        throw new IllegalArgumentException("Volume unit is required");
      }
    }

    @Override
    public SliceStrategy strategy() {
      return SliceStrategy.VOLUME_BUDGET;
    }

    @Override
    public Map<String, Object> toMap() {
      return Map.of(
          "strategy", SliceStrategy.VOLUME_BUDGET.getCode(),
          "limit", limit,
          "unit", unit);
    }
  }

  /** Single window specification (no partitioning). */
  record Single() implements WindowSpec {
    @Override
    public SliceStrategy strategy() {
      return SliceStrategy.SINGLE;
    }

    @Override
    public Map<String, Object> toMap() {
      return Map.of("strategy", SliceStrategy.SINGLE.getCode());
    }
  }

  // ============ Factory Methods ============

  /** Create a time-based window specification. */
  static Time ofTime(Instant from, Instant to) {
    return new Time(from, to);
  }

  /** Create an ID range-based window specification. */
  static IdRange ofIdRange(Long from, Long to) {
    return new IdRange(from, to);
  }

  /** Create a cursor-based window specification. */
  static CursorLandmark ofCursor(String from, String to) {
    return new CursorLandmark(from, to);
  }

  /** Create a volume budget-based window specification. */
  static VolumeBudget ofVolume(Integer limit, String unit) {
    return new VolumeBudget(limit, unit);
  }

  /** Create a single window specification (no partitioning). */
  static Single ofSingle() {
    return new Single();
  }

  /**
   * Reconstruct WindowSpec from persistence map (for infrastructure layer).
   *
   * <p>Expected map format (Format B - nested JSON):
   *
   * <ul>
   *   <li>TIME:
   *       {"strategy":"TIME","window":{"from":"2024-01-01T00:00:00Z","to":"2024-12-31T23:59:59Z",...}}
   *   <li>ID_RANGE: {"strategy":"ID_RANGE","window":{"from":1000000,"to":2000000}}
   *   <li>CURSOR_LANDMARK: {"strategy":"CURSOR_LANDMARK","window":{"from":"token1","to":"token2"}}
   *   <li>VOLUME_BUDGET: {"strategy":"VOLUME_BUDGET","limit":100000,"unit":"RECORDS"}
   *   <li>SINGLE: {"strategy":"SINGLE"}
   * </ul>
   *
   * @param map JSON-deserialized map containing at least a "strategy" key
   * @return reconstructed WindowSpec instance
   * @throws IllegalArgumentException if map is null, empty, has invalid structure, or contains
   *     unknown strategy
   */
  static WindowSpec fromMap(Map<String, Object> map) {
    if (map == null || map.isEmpty()) {
      throw new IllegalArgumentException("Window spec map cannot be null or empty");
    }

    Object strategyObj = map.get("strategy");
    if (strategyObj == null) {
      throw new IllegalArgumentException("Window spec map must contain 'strategy' key");
    }
    if (!(strategyObj instanceof String)) {
      throw new IllegalArgumentException(
          "Window spec 'strategy' must be a string, got: "
              + strategyObj.getClass().getSimpleName());
    }

    String strategyCode = (String) strategyObj;
    SliceStrategy strategy =
        SliceStrategy.fromCode(strategyCode)
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        "Unknown slice strategy code: '" + strategyCode + "'"));

    return switch (strategy) {
      case TIME -> {
        Map<String, Object> windowMap = extractRequiredWindowMap(map, "TIME");
        Object fromObj = windowMap.get("from");
        Object toObj = windowMap.get("to");

        if (fromObj == null || toObj == null) {
          throw new IllegalArgumentException("TIME window requires both 'from' and 'to' fields");
        }
        if (!(fromObj instanceof String) || !(toObj instanceof String)) {
          throw new IllegalArgumentException(
              "TIME window 'from' and 'to' must be ISO-8601 timestamp strings");
        }

        yield new Time(Instant.parse((String) fromObj), Instant.parse((String) toObj));
      }
      case DATE -> {
        // DATE strategy uses the same Time window structure internally for now
        // The difference is in how the expression is generated (date-only vs datetime)
        Map<String, Object> windowMap = extractRequiredWindowMap(map, "DATE");
        Object fromObj = windowMap.get("from");
        Object toObj = windowMap.get("to");

        if (fromObj == null || toObj == null) {
          throw new IllegalArgumentException("DATE window requires both 'from' and 'to' fields");
        }
        if (!(fromObj instanceof String) || !(toObj instanceof String)) {
          throw new IllegalArgumentException(
              "DATE window 'from' and 'to' must be ISO-8601 timestamp strings");
        }

        yield new Time(Instant.parse((String) fromObj), Instant.parse((String) toObj));
      }
      case ID_RANGE -> {
        Map<String, Object> windowMap = extractRequiredWindowMap(map, "ID_RANGE");
        Object fromObj = windowMap.get("from");
        Object toObj = windowMap.get("to");

        if (fromObj == null || toObj == null) {
          throw new IllegalArgumentException(
              "ID_RANGE window requires both 'from' and 'to' fields");
        }
        if (!(fromObj instanceof Number) || !(toObj instanceof Number)) {
          throw new IllegalArgumentException(
              "ID_RANGE window 'from' and 'to' must be numeric values");
        }

        yield new IdRange(((Number) fromObj).longValue(), ((Number) toObj).longValue());
      }
      case CURSOR_LANDMARK -> {
        Map<String, Object> windowMap = extractRequiredWindowMap(map, "CURSOR_LANDMARK");
        Object fromObj = windowMap.get("from");
        Object toObj = windowMap.get("to");

        if (fromObj == null || toObj == null) {
          throw new IllegalArgumentException(
              "CURSOR_LANDMARK window requires both 'from' and 'to' fields");
        }
        if (!(fromObj instanceof String) || !(toObj instanceof String)) {
          throw new IllegalArgumentException(
              "CURSOR_LANDMARK window 'from' and 'to' must be string values");
        }

        yield new CursorLandmark((String) fromObj, (String) toObj);
      }
      case VOLUME_BUDGET -> {
        Object limitObj = map.get("limit");
        Object unitObj = map.get("unit");

        if (limitObj == null || unitObj == null) {
          throw new IllegalArgumentException(
              "VOLUME_BUDGET strategy requires both 'limit' and 'unit' fields");
        }
        if (!(limitObj instanceof Number)) {
          throw new IllegalArgumentException("VOLUME_BUDGET 'limit' must be a numeric value");
        }
        if (!(unitObj instanceof String)) {
          throw new IllegalArgumentException("VOLUME_BUDGET 'unit' must be a string value");
        }

        yield new VolumeBudget(((Number) limitObj).intValue(), (String) unitObj);
      }
      case SINGLE -> new Single();
      case HYBRID -> throw new UnsupportedOperationException("HYBRID strategy not yet implemented");
    };
  }

  /**
   * Extract and validate the 'window' map from the main map.
   *
   * @param map main window spec map
   * @param strategyName strategy name for error messages
   * @return extracted window map
   * @throws IllegalArgumentException if 'window' is missing or not a map
   */
  @SuppressWarnings("unchecked")
  private static Map<String, Object> extractRequiredWindowMap(
      Map<String, Object> map, String strategyName) {
    Object windowObj = map.get("window");
    if (windowObj == null) {
      throw new IllegalArgumentException(strategyName + " strategy requires 'window' object");
    }
    if (!(windowObj instanceof Map)) {
      throw new IllegalArgumentException(
          strategyName
              + " 'window' must be a JSON object, got: "
              + windowObj.getClass().getSimpleName());
    }
    return (Map<String, Object>) windowObj;
  }
}
