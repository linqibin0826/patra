package com.patra.starter.web.error.config;

import com.patra.starter.core.error.config.ErrorProperties;
import com.patra.starter.core.error.pipeline.ErrorResolutionPipeline;
import com.patra.starter.core.error.spi.ProblemFieldContributor;
import com.patra.starter.core.error.spi.TraceProvider;
import com.patra.starter.web.error.adapter.DefaultProblemDetailAdapter;
import com.patra.starter.web.error.adapter.ProblemDetailAdapter;
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
 * Auto-configuration for Web-layer error handling components.
 */
@Slf4j
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "patra.web.problem", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(WebErrorProperties.class)
public class WebErrorAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ValidationErrorsFormatter defaultValidationErrorsFormatter() {
        log.debug("Creating default ValidationErrorsFormatter");
        return new DefaultValidationErrorsFormatter();
    }

    @Bean
    @ConditionalOnMissingBean
    public ProblemDetailBuilder problemDetailBuilder(ErrorProperties errorProperties,
                                                     WebErrorProperties webProperties,
                                                     TraceProvider traceProvider,
                                                     List<ProblemFieldContributor> coreFieldContributors,
                                                     List<WebProblemFieldContributor> webFieldContributors) {
        log.debug("Creating ProblemDetailBuilder: coreContributors={} webContributors={}",
                coreFieldContributors.size(), webFieldContributors.size());
        return new ProblemDetailBuilder(errorProperties, webProperties, traceProvider,
                coreFieldContributors, webFieldContributors);
    }

    @Bean
    @ConditionalOnMissingBean
    public ProblemDetailAdapter problemDetailAdapter(ErrorResolutionPipeline pipeline,
                                                     ProblemDetailBuilder problemDetailBuilder) {
        log.debug("Creating default ProblemDetailAdapter");
        return new DefaultProblemDetailAdapter(pipeline, problemDetailBuilder);
    }

    @Bean
    @ConditionalOnMissingBean
    public GlobalRestExceptionHandler globalRestExceptionHandler(ProblemDetailAdapter problemDetailAdapter,
                                                                 ValidationErrorsFormatter validationErrorsFormatter) {
        log.debug("Creating global REST exception handler");
        return new GlobalRestExceptionHandler(problemDetailAdapter, validationErrorsFormatter);
    }
}
