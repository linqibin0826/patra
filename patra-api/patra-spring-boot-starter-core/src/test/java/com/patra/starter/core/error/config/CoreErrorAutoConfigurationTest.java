package com.patra.starter.core.error.config;

import com.patra.starter.core.error.service.ErrorResolutionService;
import com.patra.starter.core.error.spi.StatusMappingStrategy;
import com.patra.starter.core.error.spi.TraceProvider;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for CoreErrorAutoConfiguration.
 * 
 * @author linqibin
 * @since 0.1.0
 */
class CoreErrorAutoConfigurationTest {
    
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(CoreErrorAutoConfiguration.class));
    
    /**
     * Test auto-configuration with default properties.
     */
    @Test
    void shouldAutoConfigureWithDefaults() {
        contextRunner
            .withPropertyValues("patra.error.context-prefix=TEST")
            .run(context -> {
                assertThat(context).hasSingleBean(ErrorProperties.class);
                assertThat(context).hasSingleBean(TracingProperties.class);
                assertThat(context).hasSingleBean(StatusMappingStrategy.class);
                assertThat(context).hasSingleBean(TraceProvider.class);
                assertThat(context).hasSingleBean(ErrorResolutionService.class);
                
                ErrorProperties errorProperties = context.getBean(ErrorProperties.class);
                assertThat(errorProperties.getContextPrefix()).isEqualTo("TEST");
                assertThat(errorProperties.isEnabled()).isTrue();
            });
    }
    
    /**
     * Test auto-configuration is disabled when patra.error.enabled=false.
     */
    @Test
    void shouldNotAutoConfigureWhenDisabled() {
        contextRunner
            .withPropertyValues(
                "patra.error.enabled=false",
                "patra.error.context-prefix=TEST"
            )
            .run(context -> {
                assertThat(context).doesNotHaveBean(ErrorResolutionService.class);
                assertThat(context).doesNotHaveBean(StatusMappingStrategy.class);
                assertThat(context).doesNotHaveBean(TraceProvider.class);
            });
    }
    
    /**
     * Test auto-configuration with custom beans.
     */
    @Test
    void shouldUseCustomBeansWhenProvided() {
        contextRunner
            .withPropertyValues("patra.error.context-prefix=TEST")
            .withUserConfiguration(CustomBeansConfiguration.class)
            .run(context -> {
                assertThat(context).hasSingleBean(StatusMappingStrategy.class);
                assertThat(context).hasSingleBean(TraceProvider.class);
                
                StatusMappingStrategy strategy = context.getBean(StatusMappingStrategy.class);
                assertThat(strategy).isInstanceOf(CustomStatusMappingStrategy.class);
                
                TraceProvider provider = context.getBean(TraceProvider.class);
                assertThat(provider).isInstanceOf(CustomTraceProvider.class);
            });
    }
    
    /**
     * Test tracing properties configuration.
     */
    @Test
    void shouldConfigureTracingProperties() {
        contextRunner
            .withPropertyValues(
                "patra.error.context-prefix=TEST",
                "patra.tracing.header-names[0]=custom-trace-id",
                "patra.tracing.header-names[1]=x-custom-trace"
            )
            .run(context -> {
                TracingProperties tracingProperties = context.getBean(TracingProperties.class);
                assertThat(tracingProperties.getHeaderNames())
                    .containsExactly("custom-trace-id", "x-custom-trace");
            });
    }
    
    /**
     * Test error properties configuration.
     */
    @Test
    void shouldConfigureErrorProperties() {
        contextRunner
            .withPropertyValues(
                "patra.error.context-prefix=REG",
                "patra.error.map-status.strategy=custom-strategy"
            )
            .run(context -> {
                ErrorProperties errorProperties = context.getBean(ErrorProperties.class);
                assertThat(errorProperties.getContextPrefix()).isEqualTo("REG");
                assertThat(errorProperties.getMapStatus().getStrategy()).isEqualTo("custom-strategy");
            });
    }
    
    @Configuration
    static class CustomBeansConfiguration {
        
        @Bean
        public StatusMappingStrategy customStatusMappingStrategy() {
            return new CustomStatusMappingStrategy();
        }
        
        @Bean
        public TraceProvider customTraceProvider() {
            return new CustomTraceProvider();
        }
    }
    
    static class CustomStatusMappingStrategy implements StatusMappingStrategy {
        @Override
        public int mapToHttpStatus(com.patra.common.error.codes.ErrorCodeLike errorCode, Throwable exception) {
            return 418; // I'm a teapot
        }
    }
    
    static class CustomTraceProvider implements TraceProvider {
        @Override
        public Optional<String> getCurrentTraceId() {
            return Optional.of("custom-trace-id");
        }
    }
}