package com.patra.starter.expr.compiler.boot;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for expression compiler safety modes and behavior switches.
 *
 * <p>Prefix: {@code expr}
 *
 * <p>Key settings:
 *
 * <ul>
 *   <li>{@code strict} - Enable STRICT mode for fail-fast behavior (default: false)
 *   <li>{@code multi.repeatEnabled} - Allow MULTI std_keys to emit repeated parameters (default:
 *       false)
 * </ul>
 *
 * <p>STRICT mode behavior:
 *
 * <ul>
 *   <li>When {@code true}: Missing functions/transforms or unsupported NOT → compilation error
 *   <li>When {@code false}: Missing functions/transforms or unsupported NOT → warning and degrade
 *       gracefully
 * </ul>
 *
 * <p>MULTI repeat behavior:
 *
 * <ul>
 *   <li>When {@code false} (default): MULTI std_keys must use join transforms (LIST_JOIN,
 *       FILTER_JOIN)
 *   <li>When {@code true}: MULTI std_keys may emit repeated provider parameters (requires adapter
 *       serialization support)
 * </ul>
 *
 * <p>See: docs/expr/02-architecture.md §2.8, docs/expr/03-compiler-bridge-internals.md §3.4.2,
 * §3.6, §3.8
 *
 * @since 1.0.0
 */
@ConfigurationProperties(prefix = "expr")
public class ExprModeProperties {

  private boolean strict = false;
  private final Multi multi = new Multi();

  /**
   * Enable STRICT mode for deterministic fail-fast behavior.
   *
   * <p>Recommended: {@code false} for dev/staging, {@code true} for production.
   *
   * @return true if STRICT mode is enabled
   */
  public boolean isStrict() {
    return strict;
  }

  public void setStrict(boolean strict) {
    this.strict = strict;
  }

  public Multi getMulti() {
    return multi;
  }

  /**
   * MULTI std_key behavior configuration.
   *
   * <p>Controls whether MULTI std_keys can emit repeated provider parameters or must use join
   * transforms.
   */
  public static class Multi {
    private boolean repeatEnabled = false;

    /**
     * Enable repeated parameter emission for MULTI std_keys.
     *
     * <p>Keep disabled (false) until adapter serialization for repeated parameters is formally
     * documented.
     *
     * @return true if repeat strategy is enabled
     */
    public boolean isRepeatEnabled() {
      return repeatEnabled;
    }

    public void setRepeatEnabled(boolean repeatEnabled) {
      this.repeatEnabled = repeatEnabled;
    }
  }
}
