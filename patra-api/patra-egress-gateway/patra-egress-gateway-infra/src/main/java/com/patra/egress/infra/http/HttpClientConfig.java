package com.patra.egress.infra.http;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * HTTP client configuration for external service calls
 *
 * @author linqibin
 * @since 0.1.0
 */
@Configuration
public class HttpClientConfig {

    /**
     * Create RestClient bean with default timeout configuration.
     * Timeout will be overridden per request based on ResilienceConfig.
     *
     * @param builder RestClient builder
     * @return configured RestClient instance
     */
    @Bean
    public RestClient restClient(RestClient.Builder builder) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory();
        // Default timeout, will be overridden per request
        factory.setReadTimeout(Duration.ofSeconds(30));

        return builder
                .requestFactory(factory)
                .build();
    }
}
