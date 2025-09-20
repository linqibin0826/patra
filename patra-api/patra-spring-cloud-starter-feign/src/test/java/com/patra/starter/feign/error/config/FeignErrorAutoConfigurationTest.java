package com.patra.starter.feign.error.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.patra.starter.core.error.spi.TraceProvider;
import com.patra.starter.feign.error.decoder.ProblemDetailErrorDecoder;
import com.patra.starter.feign.error.interceptor.TraceIdRequestInterceptor;
import feign.codec.ErrorDecoder;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for FeignErrorAutoConfiguration class.
 * Tests the auto-configuration of Feign error handling components.
 * 
 * @author linqibin
 * @since 0.1.0
 */
class FeignErrorAutoConfigurationTest {
    
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(FeignErrorAutoConfiguration.class));
    
    /**
     * Test auto-configuration with default properties.
     */
    @Test
    void shouldAutoConfigureWithDefaults() {
        contextRunner
            .withUserConfiguration(TestConfiguration.class)
            .run(context -> {
                assertThat(context).hasSingleBean(FeignErrorProperties.class);
                assertThat(context).hasSingleBean(ProblemDetailErrorDecoder.class);
                assertThat(context).hasSingleBean(TraceIdRequestInterceptor.class);
                
                // Verify default properties
                FeignErrorProperties properties = context.getBean(FeignErrorProperties.class);
                assertThat(properties.isTolerant()).isTrue(); // Default value
            });
    }
    
    /**
     * Test auto-configuration is disabled when patra.feign.problem.enabled=false.
     */
    @Test
    void shouldNotAutoConfigureWhenDisabled() {
        contextRunner
            .withUserConfiguration(TestConfiguration.class)
            .withPropertyValues("patra.feign.problem.enabled=false")
            .run(context -> {
                assertThat(context).doesNotHaveBean(FeignErrorProperties.class);
                assertThat(context).doesNotHaveBean(ProblemDetailErrorDecoder.class);
                assertThat(context).doesNotHaveBean(TraceIdRequestInterceptor.class);
            });
    }
    
    /**
     * Test auto-configuration with custom properties.
     */
    @Test
    void shouldConfigureWithCustomProperties() {
        contextRunner
            .withUserConfiguration(TestConfiguration.class)
            .withPropertyValues(
                "patra.feign.problem.tolerant=false"
            )
            .run(context -> {
                assertThat(context).hasSingleBean(FeignErrorProperties.class);
                
                FeignErrorProperties properties = context.getBean(FeignErrorProperties.class);
                assertThat(properties.isTolerant()).isFalse();
            });
    }
    
    /**
     * Test auto-configuration uses custom ErrorDecoder when provided.
     */
    @Test
    void shouldUseCustomErrorDecoderWhenProvided() {
        contextRunner
            .withUserConfiguration(TestConfiguration.class, CustomErrorDecoderConfiguration.class)
            .run(context -> {
                assertThat(context).hasSingleBean(ErrorDecoder.class);
                assertThat(context).doesNotHaveBean(ProblemDetailErrorDecoder.class);
                
                ErrorDecoder errorDecoder = context.getBean(ErrorDecoder.class);
                assertThat(errorDecoder).isInstanceOf(CustomErrorDecoder.class);
            });
    }
    
    /**
     * Test auto-configuration uses custom TraceIdRequestInterceptor when provided.
     */
    @Test
    void shouldUseCustomTraceInterceptorWhenProvided() {
        contextRunner
            .withUserConfiguration(TestConfiguration.class, CustomTraceInterceptorConfiguration.class)
            .run(context -> {
                assertThat(context).hasSingleBean(TraceIdRequestInterceptor.class);
                
                TraceIdRequestInterceptor interceptor = context.getBean(TraceIdRequestInterceptor.class);
                assertThat(interceptor).isInstanceOf(CustomTraceIdRequestInterceptor.class);
            });
    }
    
    /**
     * Test ProblemDetailErrorDecoder is created with correct dependencies.
     */
    @Test
    void shouldCreateProblemDetailErrorDecoderWithDependencies() {
        contextRunner
            .withUserConfiguration(TestConfiguration.class)
            .run(context -> {
                assertThat(context).hasSingleBean(ProblemDetailErrorDecoder.class);
                
                ProblemDetailErrorDecoder decoder = context.getBean(ProblemDetailErrorDecoder.class);
                assertThat(decoder).isNotNull();
            });
    }
    
    /**
     * Test TraceIdRequestInterceptor is created with correct dependencies.
     */
    @Test
    void shouldCreateTraceIdRequestInterceptorWithDependencies() {
        contextRunner
            .withUserConfiguration(TestConfiguration.class)
            .run(context -> {
                assertThat(context).hasSingleBean(TraceIdRequestInterceptor.class);
                
                TraceIdRequestInterceptor interceptor = context.getBean(TraceIdRequestInterceptor.class);
                assertThat(interceptor).isNotNull();
            });
    }
    
    /**
     * Test configuration for testing purposes.
     */
    @Configuration
    static class TestConfiguration {
        
        @Bean
        public ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
        
        @Bean
        public TraceProvider traceProvider() {
            return () -> java.util.Optional.empty();
        }
    }
    
    /**
     * Configuration with custom ErrorDecoder for testing purposes.
     */
    @Configuration
    static class CustomErrorDecoderConfiguration {
        
        @Bean
        public ErrorDecoder errorDecoder() {
            return new CustomErrorDecoder();
        }
    }
    
    /**
     * Configuration with custom TraceIdRequestInterceptor for testing purposes.
     */
    @Configuration
    static class CustomTraceInterceptorConfiguration {
        
        @Bean
        public TraceIdRequestInterceptor traceIdRequestInterceptor() {
            return new CustomTraceIdRequestInterceptor();
        }
    }
    
    /**
     * Custom ErrorDecoder for testing purposes.
     */
    static class CustomErrorDecoder implements ErrorDecoder {
        @Override
        public Exception decode(String methodKey, feign.Response response) {
            return new RuntimeException("Custom error decoder");
        }
    }
    
    /**
     * Custom TraceIdRequestInterceptor for testing purposes.
     */
    static class CustomTraceIdRequestInterceptor extends TraceIdRequestInterceptor {
        public CustomTraceIdRequestInterceptor() {
            super(null, null);
        }
    }
}
