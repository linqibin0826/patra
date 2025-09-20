package com.patra.starter.web.error.config;

import com.patra.starter.core.error.config.ErrorProperties;
import com.patra.starter.core.error.service.ErrorResolutionService;
import com.patra.starter.core.error.spi.ProblemFieldContributor;
import com.patra.starter.core.error.spi.TraceProvider;
import com.patra.starter.web.error.builder.ProblemDetailBuilder;

import com.patra.starter.web.error.formatter.DefaultValidationErrorsFormatter;
import com.patra.starter.web.error.handler.GlobalRestExceptionHandler;
import com.patra.starter.web.error.spi.ValidationErrorsFormatter;
import com.patra.starter.web.error.spi.WebProblemFieldContributor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.util.List;

/**
 * Auto-configuration for web-specific error handling components.
 * Provides beans for global exception handling, ProblemDetail building, and validation error formatting.
 * 
 * @author linqibin
 * @since 0.1.0
 */
@Slf4j
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "patra.web.problem", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(WebErrorProperties.class)
public class WebErrorAutoConfiguration {
    
    /**
     * Creates the default validation errors formatter with sensitive data masking.
     * 
     * @return validation errors formatter instance, never null
     */
    @Bean
    @ConditionalOnMissingBean
    public ValidationErrorsFormatter defaultValidationErrorsFormatter() {
        log.debug("Creating default validation errors formatter");
        return new DefaultValidationErrorsFormatter();
    }
    
    /**
     * Creates the ProblemDetail builder with all required dependencies.
     * 
     * @param errorProperties error configuration properties, must not be null
     * @param webProperties web error configuration properties, must not be null
     * @param traceProvider trace provider for correlation IDs, must not be null
     * @param coreFieldContributors list of core field contributors, can be empty
     * @param webFieldContributors list of web field contributors, can be empty
     * @return ProblemDetail builder instance, never null
     */
    @Bean
    @ConditionalOnMissingBean
    public ProblemDetailBuilder problemDetailBuilder(
            ErrorProperties errorProperties,
            WebErrorProperties webProperties,
            TraceProvider traceProvider,
            List<ProblemFieldContributor> coreFieldContributors,
            List<WebProblemFieldContributor> webFieldContributors) {
        
        log.debug("Creating ProblemDetail builder: coreContributors={}, webContributors={}", 
                coreFieldContributors.size(), webFieldContributors.size());
        
        return new ProblemDetailBuilder(
            errorProperties, 
            webProperties, 
            traceProvider, 
            coreFieldContributors, 
            webFieldContributors
        );
    }
    
    /**
     * Creates the global REST exception handler.
     * 
     * @param errorResolutionService error resolution service, must not be null
     * @param problemDetailBuilder ProblemDetail builder, must not be null
     * @param validationErrorsFormatter validation errors formatter, must not be null
     * @return global exception handler instance, never null
     */
    @Bean
    @ConditionalOnMissingBean
    public GlobalRestExceptionHandler globalRestExceptionHandler(
            ErrorResolutionService errorResolutionService,
            ProblemDetailBuilder problemDetailBuilder,
            ValidationErrorsFormatter validationErrorsFormatter) {
        
        log.debug("Creating global REST exception handler");
        return new GlobalRestExceptionHandler(
            errorResolutionService, 
            problemDetailBuilder, 
            validationErrorsFormatter
        );
    }
    

}