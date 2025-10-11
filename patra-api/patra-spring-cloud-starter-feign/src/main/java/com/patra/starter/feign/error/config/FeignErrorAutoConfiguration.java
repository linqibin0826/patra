package com.patra.starter.feign.error.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.patra.starter.core.error.config.TracingProperties;
import com.patra.starter.core.error.spi.TraceProvider;
import com.patra.starter.feign.error.decoder.ProblemDetailErrorDecoder;
import com.patra.starter.feign.error.interceptor.TraceIdRequestInterceptor;
import com.patra.starter.feign.error.observation.FeignErrorObservationRecorder;
import com.patra.starter.feign.error.observation.MicrometerFeignErrorObservationRecorder;
import feign.codec.ErrorDecoder;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for Feign error handling: registers the error decoder, trace propagation
 * interceptor, and optional observation recorder.
 */
@Slf4j
@AutoConfiguration
@EnableConfigurationProperties(FeignErrorProperties.class)
@ConditionalOnClass(name = {
        "feign.Feign",
        "feign.codec.ErrorDecoder",
        "org.springframework.http.ProblemDetail"
})
@ConditionalOnProperty(
        prefix = "patra.feign.problem",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class FeignErrorAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public FeignErrorObservationRecorder feignErrorObservationRecorder(FeignErrorProperties properties,
                                                                       ObjectProvider<MeterRegistry> meterRegistryProvider) {
        if (!properties.getObservation().isEnabled()) {
            log.info("Feign error observation disabled; falling back to NO_OP recorder");
            return FeignErrorObservationRecorder.NO_OP;
        }
        MeterRegistry meterRegistry = meterRegistryProvider.getIfAvailable();
        if (meterRegistry == null) {
            log.warn("Micrometer MeterRegistry not available, Feign error observation degrades to NO_OP");
            return FeignErrorObservationRecorder.NO_OP;
        }
        return new MicrometerFeignErrorObservationRecorder(meterRegistry, properties);
    }

    @Bean
    @ConditionalOnMissingBean(ErrorDecoder.class)
    public ErrorDecoder problemDetailErrorDecoder(ObjectMapper objectMapper,
                                                 FeignErrorProperties properties,
                                                 FeignErrorObservationRecorder observationRecorder) {
        log.info("Configuring ProblemDetailErrorDecoder (tolerant mode enabled: {})", properties.isTolerant());
        return new ProblemDetailErrorDecoder(objectMapper, properties, observationRecorder);
    }

    @Bean
    @ConditionalOnMissingBean(TraceIdRequestInterceptor.class)
    public TraceIdRequestInterceptor traceIdRequestInterceptor(TraceProvider traceProvider,
                                                              TracingProperties tracingProperties) {
        log.info("Configuring TraceIdRequestInterceptor with headers {}", tracingProperties.getHeaderNames());
        return new TraceIdRequestInterceptor(traceProvider, tracingProperties);
    }
}
