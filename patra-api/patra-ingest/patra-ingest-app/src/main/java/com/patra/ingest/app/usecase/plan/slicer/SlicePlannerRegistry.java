package com.patra.ingest.app.usecase.plan.slicer;

import com.patra.ingest.domain.model.enums.SliceStrategy;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Slice strategy registry (Application layer registry).
 *
 * <p>Responsibility: collect and index {@link SlicePlanner} implementations by {@link
 * SliceStrategy}. Provides O(1) lookup during planning to avoid if-else/switch explosion.
 *
 * <p>Design & constraints:
 *
 * <ul>
 *   <li>Batch-register via constructor with all Spring-injected {@link SlicePlanner} beans;
 *       read-only afterwards.
 *   <li>Ignore nulls; on conflicts, later registration overwrites earlier (supports gray
 *       replacement).
 *   <li>Uses {@link EnumMap} for constant-time access and low memory.
 *   <li>Thread-safe: no concurrent writes at runtime; reads only.
 *   <li>Extensible: add a strategy by adding a Spring bean implementation.
 *   <li>Failure mode: returns null when strategy not found (caller decides fallback/error).
 * </ul>
 */
@Component
public class SlicePlannerRegistry {

  /** Strategy → implementation mapping; read-only at runtime. */
  private final Map<SliceStrategy, SlicePlanner> registry = new EnumMap<>(SliceStrategy.class);

  /** Constructor that batch-registers all discovered {@link SlicePlanner} beans. */
  public SlicePlannerRegistry(List<SlicePlanner> planners) {
    if (planners == null) {
      return;
    }
    for (SlicePlanner planner : planners) {
      if (planner == null || planner.code() == null) {
        continue;
      }
      registry.put(planner.code(), planner);
    }
  }

  /** Returns the planner for the given strategy or null when missing. */
  public SlicePlanner get(SliceStrategy strategy) {
    if (strategy == null) {
      return null;
    }
    return registry.get(strategy);
  }
}
