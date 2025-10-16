package com.patra.starter.expr.compiler.boot;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the expression compiler.
 *
 * <p>Prefix: {@code patra.expr.compiler}
 *
 * <p>Key settings:
 *
 * <ul>
 *   <li>{@code queryParamBridge.enabled} - Enable query bridging via std_key=query (default: true)
 *   <li>{@code maxQueryLength} - Maximum query length in characters (default: 0 = disabled)
 *   <li>{@code warnParamCount} - Soft limit for parameter count warnings (default: 0 = disabled)
 *   <li>{@code maxParamCount} - Hard limit for parameter count errors (default: 0 = disabled)
 * </ul>
 *
 * <p>See: docs/expr/03-compiler-bridge-internals.md §3.6, §3.9
 *
 * @since 1.0.0
 */
@ConfigurationProperties(prefix = "patra.expr.compiler")
public class CompilerProperties {

  private boolean enabled = true;
  private final RegistryApi registryApi = new RegistryApi();
  private final QueryParamBridge queryParamBridge = new QueryParamBridge();
  private int maxQueryLength = 0; // 0 = disabled
  private int warnParamCount = 0; // 0 = disabled
  private int maxParamCount = 0; // 0 = disabled

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public RegistryApi getRegistryApi() {
    return registryApi;
  }

  public QueryParamBridge getQueryParamBridge() {
    return queryParamBridge;
  }

  /**
   * Maximum query length in characters. 0 means disabled (no limit).
   *
   * @return max query length
   */
  public int getMaxQueryLength() {
    return maxQueryLength;
  }

  public void setMaxQueryLength(int maxQueryLength) {
    this.maxQueryLength = maxQueryLength;
  }

  /**
   * Soft limit for parameter count that triggers W-PARAM-COUNT-LIMIT warning. 0 means disabled.
   *
   * @return warn param count threshold
   */
  public int getWarnParamCount() {
    return warnParamCount;
  }

  public void setWarnParamCount(int warnParamCount) {
    this.warnParamCount = warnParamCount;
  }

  /**
   * Hard limit for parameter count that triggers E-PARAM-COUNT-LIMIT error. 0 means disabled.
   *
   * @return max param count threshold
   */
  public int getMaxParamCount() {
    return maxParamCount;
  }

  public void setMaxParamCount(int maxParamCount) {
    this.maxParamCount = maxParamCount;
  }

  public static class RegistryApi {
    private boolean enabled = true;
    private String operationDefault = "SEARCH";

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }

    public String getOperationDefault() {
      return operationDefault;
    }

    public void setOperationDefault(String operationDefault) {
      this.operationDefault = operationDefault;
    }
  }

  /**
   * Query parameter bridging configuration.
   *
   * <p>Controls whether the aggregated boolean query is bridged into provider params via
   * std_key=query mapping.
   *
   * <p>See: docs/expr/03-compiler-bridge-internals.md §3.2, §3.6
   */
  public static class QueryParamBridge {
    private boolean enabled = true;

    /**
     * Enable/disable query bridging. When enabled, the compiler bridges std_key=query to the
     * provider parameter name.
     *
     * @return true if query bridging is enabled
     */
    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }
  }
}
