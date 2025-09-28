package com.patra.starter.feign.error.observation;

import com.patra.starter.feign.error.config.FeignErrorProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.TimeUnit;

/**
 * 基于 Micrometer 的 Feign 错误解码观测实现。
 */
@Slf4j
public class MicrometerFeignErrorObservationRecorder implements FeignErrorObservationRecorder {

    private final MeterRegistry meterRegistry;
    private final FeignErrorProperties.ObservationProperties observationProperties;

    public MicrometerFeignErrorObservationRecorder(MeterRegistry meterRegistry,
                                                   FeignErrorProperties properties) {
        this.meterRegistry = meterRegistry;
        this.observationProperties = properties.getObservation();
    }

    @Override
    public void recordProblemDetailParsing(String methodKey, int status, long durationMs, boolean success) {
        Timer.builder("papertrace.feign.error.parsing")
                .tag("method", methodKey)
                .tag("status", String.valueOf(status))
                .tag("success", Boolean.toString(success))
                .register(meterRegistry)
                .record(durationMs, TimeUnit.MILLISECONDS);

        if (observationProperties.isLogSlowParsing()
                && durationMs >= observationProperties.getSlowParsingThresholdMs()) {
            log.warn("Feign ProblemDetail 解析耗时较长: method={} status={} duration={}ms", methodKey, status, durationMs);
        }

        if (!success) {
            log.debug("Feign ProblemDetail 解析失败: method={} status={} duration={}ms", methodKey, status, durationMs);
        }
    }

    @Override
    public void recordDecodingOutcome(String methodKey, int status, boolean success, boolean tolerantMode) {
        Counter.builder("papertrace.feign.error.decoding")
                .tag("method", methodKey)
                .tag("status", String.valueOf(status))
                .tag("success", Boolean.toString(success))
                .tag("tolerant", Boolean.toString(tolerantMode))
                .register(meterRegistry)
                .increment();

        if (tolerantMode && observationProperties.isLogTolerantUsage()) {
            log.info("Feign 错误解码触发宽容模式: method={} status={}", methodKey, status);
        }
    }

    @Override
    public void recordResponseBodyRead(String methodKey, int bodySize, long durationMs, boolean truncated) {
        Timer.builder("papertrace.feign.error.body.read")
                .tag("method", methodKey)
                .tag("truncated", Boolean.toString(truncated))
                .register(meterRegistry)
                .record(durationMs, TimeUnit.MILLISECONDS);

        if (observationProperties.isLogSlowBodyReading()
                && durationMs >= observationProperties.getSlowBodyReadingThresholdMs()) {
            log.warn("Feign 响应体读取耗时较长: method={} size={} duration={}ms truncated={}",
                    methodKey, bodySize, durationMs, truncated);
        }
    }

    @Override
    public void recordTraceIdExtraction(String methodKey, boolean found, String headerName) {
        Counter.builder("papertrace.feign.error.traceid")
                .tag("method", methodKey)
                .tag("found", Boolean.toString(found))
                .tag("header", headerName == null ? "none" : headerName)
                .register(meterRegistry)
                .increment();

        if (found) {
            log.debug("Feign 响应头提取 TraceId 成功: method={} header={}", methodKey, headerName);
        } else {
            log.debug("Feign 响应头未包含 TraceId: method={}", methodKey);
        }
    }
}
