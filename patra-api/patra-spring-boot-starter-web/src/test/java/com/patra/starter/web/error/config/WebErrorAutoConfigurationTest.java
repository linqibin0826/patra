package com.patra.starter.web.error.config;

import com.patra.starter.core.error.config.ErrorProperties;
import com.patra.starter.core.error.service.ErrorResolutionService;
import com.patra.starter.core.error.spi.TraceProvider;
import com.patra.starter.web.error.builder.ProblemDetailBuilder;
import com.patra.starter.web.error.formatter.DefaultValidationErrorsFormatter;
import com.patra.starter.web.error.spi.ValidationErrorsFormatter;
import com.patra.starter.web.error.model.ValidationError;
import com.patra.starter.web.error.handler.GlobalRestExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for WebErrorAutoConfiguration class.
 * Tests the auto-configuration of web error handling components.
 * 
 * @author linqibin
 * @since 0.1.0
 */
class WebErrorAutoConfigurationTest {
    
    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(WebErrorAutoConfiguration.class));
    
    /**
     * Test auto-configuration with default properties.
     */
    @Test
    void shouldAutoConfigureWithDefaults() {
        contextRunner
            .withUserConfiguration(TestConfiguration.class)
            .run(context -> {
                assertThat(context).hasSingleBean(WebErrorProperties.class);
                assertThat(context).hasSingleBean(GlobalRestExceptionHandler.class);
                assertThat(context).hasSingleBean(ProblemDetailBuilder.class);
                assertThat(context).hasSingleBean(ValidationErrorsFormatter.class);
                assertThat(context).hasSingleBean(DefaultValidationErrorsFormatter.class);
                
                // Verify default properties
                WebErrorProperties properties = context.getBean(WebErrorProperties.class);
                assertThat(properties.isEnabled()).isTrue();
                assertThat(properties.getTypeBaseUrl()).isEqualTo("https://errors.example.com/");
                assertThat(properties.isIncludeStack()).isFalse();
            });
    }
    
    /**
     * Test auto-configuration is disabled when patra.web.problem.enabled=false.
     */
    @Test
    void shouldNotAutoConfigureWhenDisabled() {
        contextRunner
            .withUserConfiguration(TestConfiguration.class)
            .withPropertyValues("patra.web.problem.enabled=false")
            .run(context -> {
                assertThat(context).doesNotHaveBean(WebErrorProperties.class);
                assertThat(context).doesNotHaveBean(GlobalRestExceptionHandler.class);
                assertThat(context).doesNotHaveBean(ProblemDetailBuilder.class);
                assertThat(context).doesNotHaveBean(ValidationErrorsFormatter.class);
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
                "patra.web.problem.type-base-url=https://custom.errors.com/",
                "patra.web.problem.include-stack=true"
            )
            .run(context -> {
                assertThat(context).hasSingleBean(WebErrorProperties.class);
                
                WebErrorProperties properties = context.getBean(WebErrorProperties.class);
                assertThat(properties.getTypeBaseUrl()).isEqualTo("https://custom.errors.com/");
                assertThat(properties.isIncludeStack()).isTrue();
            });
    }
    
    /**
     * Test auto-configuration uses custom ValidationErrorsFormatter when provided.
     */
    @Test
    void shouldUseCustomValidationErrorsFormatterWhenProvided() {
        contextRunner
            .withUserConfiguration(TestConfiguration.class, CustomValidationFormatterConfiguration.class)
            .run(context -> {
                assertThat(context).hasSingleBean(ValidationErrorsFormatter.class);
                assertThat(context).doesNotHaveBean(DefaultValidationErrorsFormatter.class);
                
                ValidationErrorsFormatter formatter = context.getBean(ValidationErrorsFormatter.class);
                assertThat(formatter).isInstanceOf(CustomValidationErrorsFormatter.class);
            });
    }
    
    /**
     * Test auto-configuration uses custom GlobalRestExceptionHandler when provided.
     */
    @Test
    void shouldUseCustomGlobalExceptionHandlerWhenProvided() {
        contextRunner
            .withUserConfiguration(TestConfiguration.class, CustomExceptionHandlerConfiguration.class)
            .run(context -> {
                assertThat(context).hasSingleBean(GlobalRestExceptionHandler.class);
                
                GlobalRestExceptionHandler handler = context.getBean(GlobalRestExceptionHandler.class);
                assertThat(handler).isInstanceOf(CustomGlobalRestExceptionHandler.class);
            });
    }
    
    /**
     * Test ProblemDetailBuilder is created with correct dependencies.
     */
    @Test
    void shouldCreateProblemDetailBuilderWithDependencies() {
        contextRunner
            .withUserConfiguration(TestConfiguration.class)
            .run(context -> {
                assertThat(context).hasSingleBean(ProblemDetailBuilder.class);
                
                ProblemDetailBuilder builder = context.getBean(ProblemDetailBuilder.class);
                assertThat(builder).isNotNull();
            });
    }
    
    /**
     * Test GlobalRestExceptionHandler is created with correct dependencies.
     */
    @Test
    void shouldCreateGlobalRestExceptionHandlerWithDependencies() {
        contextRunner
            .withUserConfiguration(TestConfiguration.class)
            .run(context -> {
                assertThat(context).hasSingleBean(GlobalRestExceptionHandler.class);
                
                GlobalRestExceptionHandler handler = context.getBean(GlobalRestExceptionHandler.class);
                assertThat(handler).isNotNull();
            });
    }
    
    /**
     * Test configuration requires web environment.
     */
    @Test
    void shouldRequireWebEnvironment() {
        new org.springframework.boot.test.context.runner.ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(WebErrorAutoConfiguration.class))
            .withUserConfiguration(TestConfiguration.class)
            .run(context -> {
                // Should not auto-configure in non-web environment
                assertThat(context).doesNotHaveBean(WebErrorProperties.class);
                assertThat(context).doesNotHaveBean(GlobalRestExceptionHandler.class);
            });
    }
    
    /**
     * Test configuration for testing purposes.
     */
    @Configuration
    static class TestConfiguration {
        
        @Bean
        public ErrorProperties errorProperties() {
            ErrorProperties properties = new ErrorProperties();
            properties.setContextPrefix("TEST");
            return properties;
        }
        
        @Bean
        public ErrorResolutionService errorResolutionService() {
            return mock(ErrorResolutionService.class);
        }
        
        @Bean
        public TraceProvider traceProvider() {
            return () -> java.util.Optional.empty();
        }
    }
    
    /**
     * Configuration with custom ValidationErrorsFormatter for testing purposes.
     */
    @Configuration
    static class CustomValidationFormatterConfiguration {
        
        @Bean
        public ValidationErrorsFormatter validationErrorsFormatter() {
            return new CustomValidationErrorsFormatter();
        }
    }
    
    /**
     * Configuration with custom GlobalRestExceptionHandler for testing purposes.
     */
    @Configuration
    static class CustomExceptionHandlerConfiguration {
        
        @Bean
        public GlobalRestExceptionHandler globalRestExceptionHandler() {
            return new CustomGlobalRestExceptionHandler();
        }
    }
    
    /**
     * Custom ValidationErrorsFormatter for testing purposes.
     */
    static class CustomValidationErrorsFormatter implements ValidationErrorsFormatter {
        @Override
        public java.util.List<ValidationError> formatWithMasking(
                org.springframework.validation.BindingResult bindingResult) {
            return Collections.emptyList();
        }
    }
    
    /**
     * Custom GlobalRestExceptionHandler for testing purposes.
     */
    static class CustomGlobalRestExceptionHandler extends GlobalRestExceptionHandler {
        public CustomGlobalRestExceptionHandler() {
            super(null, null, null);
        }
    }
}