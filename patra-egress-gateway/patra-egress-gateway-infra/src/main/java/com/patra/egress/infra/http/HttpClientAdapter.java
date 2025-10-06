package com.patra.egress.infra.http;

import com.patra.egress.domain.model.vo.HttpRequest;
import com.patra.egress.domain.model.vo.HttpResponse;
import com.patra.egress.domain.model.vo.ResilienceConfig;
import com.patra.egress.domain.port.HttpClientPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * HTTP client adapter implementation using Spring RestClient
 *
 * @author linqibin
 * @since 0.1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HttpClientAdapter implements HttpClientPort {

    private final RestClient restClient;

    @Override
    public HttpResponse call(HttpRequest request, ResilienceConfig config) {
        log.debug("[EGRESS][INFRA] HTTP call started: method={} url={}", request.method(), request.url());

        // Log request headers (with sensitive headers masked)
        if (log.isDebugEnabled() && request.headers() != null) {
            Map<String, String> maskedHeaders = SensitiveHeaderMasker.mask(request.headers());
            log.debug("[EGRESS][INFRA] Request headers: {}", maskedHeaders);
        }

        try {
            return restClient.method(mapHttpMethod(request.method()))
                    .uri(request.url())
                    .headers(headers -> {
                        if (request.headers() != null) {
                            request.headers().forEach(headers::set);
                        }
                    })
                    .body(request.body() != null ? request.body() : "")
                    .exchange((clientRequest, clientResponse) -> {
                        HttpResponse response = buildHttpResponse(clientResponse);
                        log.debug("[EGRESS][INFRA] HTTP call completed: statusCode={} url={}",
                                response.statusCode(), request.url());
                        return response;
                    });
        } catch (Exception e) {
            log.error("[EGRESS][INFRA] HTTP call failed: url={} error={}",
                    request.url(), e.getMessage(), e);
            throw new RuntimeException("HTTP call failed: " + e.getMessage(), e);
        }
    }

    /**
     * Map domain HttpMethod to Spring HttpMethod
     */
    private org.springframework.http.HttpMethod mapHttpMethod(com.patra.egress.domain.model.vo.HttpMethod method) {
        return switch (method) {
            case GET -> org.springframework.http.HttpMethod.GET;
            case POST -> org.springframework.http.HttpMethod.POST;
            case PUT -> org.springframework.http.HttpMethod.PUT;
            case DELETE -> org.springframework.http.HttpMethod.DELETE;
            case PATCH -> org.springframework.http.HttpMethod.PATCH;
            case HEAD -> org.springframework.http.HttpMethod.HEAD;
            case OPTIONS -> org.springframework.http.HttpMethod.OPTIONS;
        };
    }

    /**
     * Build HttpResponse from ClientHttpResponse
     */
    private HttpResponse buildHttpResponse(ClientHttpResponse clientResponse) throws IOException {
        int statusCode = clientResponse.getStatusCode().value();

        // Convert headers to Map<String, List<String>>
        Map<String, List<String>> headers = clientResponse.getHeaders().entrySet().stream()
                .filter(entry -> entry.getKey() != null)
                .collect(Collectors.toMap(
                        entry -> entry.getKey().toLowerCase(Locale.ROOT),
                        entry -> entry.getValue() != null ? List.copyOf(entry.getValue()) : List.of(),
                        (left, right) -> {
                            List<String> merged = new ArrayList<>(left);
                            merged.addAll(right);
                            return List.copyOf(merged);
                        },
                        LinkedHashMap::new
                ));

        // Read response body
        String body = new String(clientResponse.getBody().readAllBytes(), StandardCharsets.UTF_8);

        return new HttpResponse(statusCode, headers, body);
    }
}
