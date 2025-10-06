package com.patra.egress.app.usecase.externalcall;

import com.patra.egress.app.util.HeaderWhitelistFilter;
import com.patra.egress.app.util.ResponseHashCalculator;
import com.patra.egress.domain.model.vo.*;

import java.util.List;
import java.util.Map;

/**
 * Builder for constructing ResponseEnvelope from HttpResponse
 *
 * @author linqibin
 * @since 0.1.0
 */
public class ResponseEnvelopeBuilder {

    /**
     * Build ResponseEnvelope from HTTP response and resilience status
     *
     * @param rawResponse HTTP response from external service
     * @param rateLimitStatus rate limit status (gateway-level)
     * @param retryAdvice retry advice
     * @param headerWhitelist response header whitelist
     * @return response envelope
     */
    public ResponseEnvelope build(
            HttpResponse rawResponse,
            RateLimitStatus rateLimitStatus,
            RetryAdvice retryAdvice,
            List<String> headerWhitelist
    ) {
        if (rawResponse == null) {
            throw new IllegalArgumentException("Raw response cannot be null");
        }

        // Determine success/failure based on HTTP status code (2xx = success)
        boolean success = rawResponse.isSuccess();

        // Filter response headers by whitelist
        Map<String, String> filteredHeaders = HeaderWhitelistFilter.filter(
                rawResponse.headers(),
                headerWhitelist
        );

        // Calculate response body hash
        String bodyHash = ResponseHashCalculator.calculateHash(rawResponse.body());

        // Extract external rate limit info from response headers
        ExternalRateLimitInfo externalRateLimitInfo = ExternalRateLimitInfo.fromHeaders(rawResponse.headers());

        // Create combined rate limit status
        RateLimitStatus combinedRateLimitStatus = new RateLimitStatus(
                rateLimitStatus.limit(),
                rateLimitStatus.remaining(),
                rateLimitStatus.resetAfter(),
                externalRateLimitInfo
        );

        // Generate retry advice from response
        RetryAdvice generatedRetryAdvice = retryAdvice != null
                ? retryAdvice
                : RetryAdvice.notRetryable();

        return new ResponseEnvelope(
                success,
                rawResponse.statusCode(),
                filteredHeaders,
                rawResponse.body(),
                bodyHash,
                combinedRateLimitStatus,
                generatedRetryAdvice,
                "META_PLUS_BODY"
        );
    }
}
