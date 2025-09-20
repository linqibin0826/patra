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
 * Web 端错误处理自动装配。
 *
 * <p>提供以下组件的条件化注册：
 * - 全局异常处理器 {@link com.patra.starter.web.error.handler.GlobalRestExceptionHandler}
 * - ProblemDetail 构造器 {@link com.patra.starter.web.error.builder.ProblemDetailBuilder}
 * - 校验错误格式化器 {@link com.patra.starter.web.error.spi.ValidationErrorsFormatter}
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
     * 默认的校验错误格式化器（带敏感信息脱敏）。
     *
     * @return 实例
     */
    @Bean
    @ConditionalOnMissingBean
    public ValidationErrorsFormatter defaultValidationErrorsFormatter() {
        log.debug("Creating default validation errors formatter");
        return new DefaultValidationErrorsFormatter();
    }
    
    /**
     * 构造 ProblemDetail 构造器。
     *
     * @param errorProperties 错误处理配置
     * @param webProperties Web 错误配置
     * @param traceProvider TraceId 提供者
     * @param coreFieldContributors 核心字段贡献者集合
     * @param webFieldContributors Web 字段贡献者集合
     * @return 构造器实例
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
     * 创建全局 REST 异常处理器。
     *
     * @param errorResolutionService 错误解析服务
     * @param problemDetailBuilder ProblemDetail 构造器
     * @param validationErrorsFormatter 校验错误格式化器
     * @return 处理器实例
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
