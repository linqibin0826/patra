package com.patra.ingest.domain.model.vo.plan;

import java.time.Instant;

/**
 * Value object representing a planning window.
 *
 * <p>Uses a UTC half-open interval {@code [from, to)}. {@code null} values indicate unbounded/full
 * ranges.
 *
 * <p>Typical usage:
 *
 * <ul>
 *   <li>{@link #full()} returns an unbounded window for full refreshes.
 *   <li>{@code new PlannerWindow(from, to)} supplies a concrete time slice.
 *   <li>{@link #isFull()} checks whether the window is unbounded.
 *   <li>{@link #isEmpty()} detects invalid (non-forward) ranges.
 * </ul>
 */
public record PlannerWindow(Instant from, Instant to) {
  public PlannerWindow {
    if (from != null && to != null && !from.isBefore(to)) {
      throw new IllegalArgumentException("Window start must be earlier than end");
    }
  }

  /** Returns {@code true} when the window is empty or invalid (from >= to). */
  public boolean isEmpty() {
    return from != null && to != null && !from.isBefore(to);
  }

  /** Returns {@code true} when both bounds are {@code null} (unbounded window). */
  public boolean isFull() {
    return from == null && to == null;
  }

  /**
   * Factory for an unbounded/full window ({@code from = null}, {@code to = null}).
   *
   * @return unbounded planner window
   */
  public static PlannerWindow full() {
    return new PlannerWindow(null, null);
  }
}
