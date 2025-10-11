package com.patra.egress.domain.port;

import com.patra.egress.domain.model.vo.HttpRequest;
import com.patra.egress.domain.model.vo.HttpResponse;
import com.patra.egress.domain.model.vo.ResilienceConfig;

/**
 * Domain port that abstracts the HTTP client used for outbound calls.
 *
 * @author linqibin
 * @since 0.1.0
 */
public interface HttpClientPort {
    
    /**
     * Execute an outbound HTTP call using the provided resilience configuration.
     *
     * @param request immutable HTTP request envelope
     * @param config  resilience configuration (timeouts, retries, etc.)
     * @return immutable HTTP response envelope
     */
    HttpResponse call(HttpRequest request, ResilienceConfig config);
}
