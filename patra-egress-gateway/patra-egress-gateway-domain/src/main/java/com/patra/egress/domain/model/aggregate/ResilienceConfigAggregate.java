package com.patra.egress.domain.model.aggregate;

import com.patra.egress.domain.model.vo.ResilienceConfig;
import com.patra.egress.domain.port.ConfigPort;

/**
 * Aggregate responsible for loading, validating, and merging resilience configuration.
 *
 * @author linqibin
 * @since 0.1.0
 */
public class ResilienceConfigAggregate {
    
    private final ResilienceConfig systemDefaultConfig;
    private final ResilienceConfig systemMaxConfig;
    
    /**
     * Private constructor invoked by {@link #loadSystemConfig(ConfigPort)}.
     *
     * @param systemDefaultConfig system-wide default configuration
     * @param systemMaxConfig     system-wide maximum configuration
     */
    private ResilienceConfigAggregate(
        ResilienceConfig systemDefaultConfig,
        ResilienceConfig systemMaxConfig
    ) {
        this.systemDefaultConfig = systemDefaultConfig;
        this.systemMaxConfig = systemMaxConfig;
    }
    
    /**
     * Load both default and maximum resilience configurations from the provided port.
     *
     * @param configPort domain port used to access configuration sources
     * @return aggregate encapsulating the resolved configurations
     */
    public static ResilienceConfigAggregate loadSystemConfig(ConfigPort configPort) {
        ResilienceConfig defaultConfig = configPort.loadSystemDefaultConfig();
        ResilienceConfig maxConfig = configPort.loadSystemMaxConfig();
        
        // Validate the resolved system-level configuration values.
        defaultConfig.validate();
        maxConfig.validate();
        
        return new ResilienceConfigAggregate(defaultConfig, maxConfig);
    }
    
    /**
     * Merge caller-provided configuration while enforcing system maximums.
     *
     * @param callerConfig configuration overrides supplied by the caller; may be {@code null}
     * @return merged configuration bounded by the system maxima
     */
    public ResilienceConfig mergeWithCallerConfig(ResilienceConfig callerConfig) {
        // Fall back to the system default when no caller override is supplied.
        if (callerConfig == null) {
            return systemDefaultConfig;
        }
        
        // Validate caller overrides before merging.
        callerConfig.validate();
        
        // Merge with the system maxima to stay within allowed guardrails.
        return callerConfig.mergeWithMax(systemMaxConfig);
    }
    
    /**
     * Validate both system-level configurations.
     *
     * @throws IllegalArgumentException when either configuration is invalid
     */
    public void validate() {
        systemDefaultConfig.validate();
        systemMaxConfig.validate();
    }
    
    /**
     * Retrieve the system default configuration.
     *
     * @return default resilience configuration
     */
    public ResilienceConfig getSystemDefaultConfig() {
        return systemDefaultConfig;
    }
    
    /**
     * Retrieve the system maximum configuration.
     *
     * @return maximum resilience configuration
     */
    public ResilienceConfig getSystemMaxConfig() {
        return systemMaxConfig;
    }
}
