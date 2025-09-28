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
 * Web 端错误处理自动装配。
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
        log.debug("创建默认 ValidationErrorsFormatter");
        return new DefaultValidationErrorsFormatter();
    }

    @Bean
    @ConditionalOnMissingBean
    public ProblemDetailBuilder problemDetailBuilder(ErrorProperties errorProperties,
                                                     WebErrorProperties webProperties,
                                                     TraceProvider traceProvider,
                                                     List<ProblemFieldContributor> coreFieldContributors,
                                                     List<WebProblemFieldContributor> webFieldContributors) {
        log.debug("创建 ProblemDetailBuilder：coreContributors={} webContributors={}",
                coreFieldContributors.size(), webFieldContributors.size());
        return new ProblemDetailBuilder(errorProperties, webProperties, traceProvider,
                coreFieldContributors, webFieldContributors);
    }

    @Bean
    @ConditionalOnMissingBean
    public ProblemDetailAdapter problemDetailAdapter(ErrorResolutionPipeline pipeline,
                                                     ProblemDetailBuilder problemDetailBuilder) {
        log.debug("创建默认 ProblemDetailAdapter");
        return new DefaultProblemDetailAdapter(pipeline, problemDetailBuilder);
    }

    @Bean
    @ConditionalOnMissingBean
    public GlobalRestExceptionHandler globalRestExceptionHandler(ProblemDetailAdapter problemDetailAdapter,
                                                                 ValidationErrorsFormatter validationErrorsFormatter) {
        log.debug("创建全局 REST 异常处理器");
        return new GlobalRestExceptionHandler(problemDetailAdapter, validationErrorsFormatter);
    }
}
