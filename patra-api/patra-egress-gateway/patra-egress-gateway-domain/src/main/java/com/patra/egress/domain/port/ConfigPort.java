package com.patra.egress.domain.port;

import com.patra.egress.domain.model.vo.ResilienceConfig;

/**
 * Domain port responsible for loading resilience configuration from various sources.
 *
 * @author linqibin
 * @since 0.1.0
 */
public interface ConfigPort {
    
    /**
     * Load the system-wide default configuration applied to every outbound call.
     *
     * @return default resilience configuration
     */
    ResilienceConfig loadSystemDefaultConfig();
    
    /**
     * Load the maximum allowable configuration values enforced as guardrails.
     *
     * @return maximum resilience configuration
     */
    ResilienceConfig loadSystemMaxConfig();
}
