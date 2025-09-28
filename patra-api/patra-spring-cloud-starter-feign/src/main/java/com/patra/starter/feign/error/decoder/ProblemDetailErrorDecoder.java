package com.patra.starter.feign.error.decoder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.patra.common.error.problem.ErrorKeys;
import com.patra.starter.feign.error.config.FeignErrorProperties;
import com.patra.starter.feign.error.exception.RemoteCallException;
import com.patra.starter.feign.error.observation.FeignErrorObservationRecorder;
import feign.FeignException;
import feign.Response;
import feign.codec.ErrorDecoder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ProblemDetail;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.concurrent.TimeUnit;

/**
 * 基于 {@link ProblemDetail} 的 Feign 错误解码器。
 */
@Slf4j
public class ProblemDetailErrorDecoder implements ErrorDecoder {

    private final ObjectMapper objectMapper;
    private final FeignErrorProperties properties;
    private final FeignErrorObservationRecorder observationRecorder;

    public ProblemDetailErrorDecoder(ObjectMapper objectMapper,
                                     FeignErrorProperties properties,
                                     FeignErrorObservationRecorder observationRecorder) {
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.observationRecorder = observationRecorder;
    }

    @Override
    public Exception decode(String methodKey, Response response) {
        boolean decodingSuccess = false;
        boolean tolerantModeUsed = false;
        BodyBuffer bodyBuffer = null;

        try {
            String contentType = getContentType(response);
            boolean isProblemDetail = isProblemDetailResponse(contentType);
            log.debug("解码远端错误: method={} status={} contentType={}", methodKey, response.status(), contentType);

            if (isProblemDetail) {
                bodyBuffer = readResponseBody(methodKey, response);
                ParsingResult parsingResult = parseProblemDetail(bodyBuffer);
                observationRecorder.recordProblemDetailParsing(methodKey, response.status(),
                        parsingResult.durationMs(), parsingResult.success());

                if (parsingResult.success() && parsingResult.problemDetail() != null) {
                    ProblemDetail problemDetail = parsingResult.problemDetail();
                    decodingSuccess = true;

                    TraceExtraction traceExtraction = extractTraceId(response);
                    observationRecorder.recordTraceIdExtraction(methodKey,
                            traceExtraction.traceId() != null, traceExtraction.headerName());
                    if (traceExtraction.traceId() != null
                            && (problemDetail.getProperties() == null
                            || problemDetail.getProperties().get(ErrorKeys.TRACE_ID) == null)) {
                        problemDetail.setProperty(ErrorKeys.TRACE_ID, traceExtraction.traceId());
                    }

                    return new RemoteCallException(problemDetail, methodKey);
                }
            }

            if (properties.isTolerant()) {
                tolerantModeUsed = true;
                if (bodyBuffer == null) {
                    bodyBuffer = readResponseBody(methodKey, response);
                }
                return handleTolerantMode(methodKey, response, bodyBuffer);
            }

            log.debug("严格模式：回退为 FeignException，method={}", methodKey);
            return FeignException.errorStatus(methodKey, response);

        } catch (Exception ex) {
            log.warn("解码远端错误失败: method={} status={} error={}",
                    methodKey, response.status(), ex.getMessage());

            if (properties.isTolerant()) {
                tolerantModeUsed = true;
                try {
                    if (bodyBuffer == null) {
                        bodyBuffer = readResponseBody(methodKey, response);
                    }
                } catch (IOException ioException) {
                    log.debug("宽容模式下读取响应体失败: method={} error={}", methodKey, ioException.getMessage());
                }
                return handleTolerantMode(methodKey, response, bodyBuffer);
            }

            return FeignException.errorStatus(methodKey, response);
        } finally {
            observationRecorder.recordDecodingOutcome(methodKey, response.status(), decodingSuccess, tolerantModeUsed);
        }
    }

    private RemoteCallException handleTolerantMode(String methodKey, Response response, BodyBuffer bodyBuffer) {
        TraceExtraction traceExtraction = extractTraceId(response);
        observationRecorder.recordTraceIdExtraction(methodKey,
                traceExtraction.traceId() != null, traceExtraction.headerName());

        String message = buildFallbackMessage(response, bodyBuffer);
        return new RemoteCallException(response.status(), message, methodKey, traceExtraction.traceId());
    }

    private ParsingResult parseProblemDetail(BodyBuffer bodyBuffer) {
        if (bodyBuffer == null || bodyBuffer.content() == null || bodyBuffer.content().isBlank()) {
            return new ParsingResult(null, 0L, false);
        }

        long start = System.nanoTime();
        try {
            ProblemDetail problemDetail = objectMapper.readValue(bodyBuffer.content(), ProblemDetail.class);
            long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
            log.debug("成功解析 ProblemDetail，status={}", problemDetail.getStatus());
            return new ParsingResult(problemDetail, durationMs, true);
        } catch (Exception ex) {
            long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
            log.debug("ProblemDetail 解析失败: {}", ex.getMessage());
            return new ParsingResult(null, durationMs, false);
        }
    }

    private BodyBuffer readResponseBody(String methodKey, Response response) throws IOException {
        if (response.body() == null) {
            return BodyBuffer.empty();
        }

        long start = System.nanoTime();
        int maxSize = properties.getMaxErrorBodySize();
        byte[] bytes;
        try (InputStream inputStream = response.body().asInputStream()) {
            bytes = inputStream.readNBytes(maxSize + 1);
        }
        long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

        boolean truncated = bytes.length > maxSize;
        int effectiveLength = Math.min(bytes.length, maxSize);
        String content = new String(bytes, 0, effectiveLength, StandardCharsets.UTF_8);

        observationRecorder.recordResponseBodyRead(methodKey, effectiveLength, durationMs, truncated);
        return new BodyBuffer(content, effectiveLength, truncated);
    }

    private String buildFallbackMessage(Response response, BodyBuffer bodyBuffer) {
        String reason = response.reason();
        if (reason != null && !reason.isBlank()) {
            return reason;
        }

        if (bodyBuffer != null && bodyBuffer.content() != null && !bodyBuffer.content().isBlank()) {
            String content = bodyBuffer.content();
            if (content.length() > 200) {
                content = content.substring(0, 200) + "...";
            }
            return "HTTP " + response.status() + ": " + content;
        }

        return "HTTP " + response.status();
    }

    private TraceExtraction extractTraceId(Response response) {
        String[] headers = {"traceId", "X-B3-TraceId", "traceparent", "X-Trace-Id"};
        for (String header : headers) {
            Collection<String> values = response.headers().get(header);
            if (values != null && !values.isEmpty()) {
                String traceId = values.iterator().next();
                if (traceId != null && !traceId.trim().isEmpty()) {
                    return new TraceExtraction(traceId.trim(), header);
                }
            }
        }
        return new TraceExtraction(null, null);
    }

    private String getContentType(Response response) {
        Collection<String> contentTypes = response.headers().get("content-type");
        if (contentTypes == null || contentTypes.isEmpty()) {
            contentTypes = response.headers().get("Content-Type");
        }
        if (contentTypes != null && !contentTypes.isEmpty()) {
            return contentTypes.iterator().next();
        }
        return null;
    }

    private boolean isProblemDetailResponse(String contentType) {
        return contentType != null && contentType.toLowerCase().contains("application/problem+json");
    }

    private record BodyBuffer(String content, int length, boolean truncated) {
        static BodyBuffer empty() { return new BodyBuffer(null, 0, false); }
    }

    private record ParsingResult(ProblemDetail problemDetail, long durationMs, boolean success) { }

    private record TraceExtraction(String traceId, String headerName) { }
}
