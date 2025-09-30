package com.patra.starter.rocketmq.config;

import com.patra.common.error.codes.HttpStdErrors;
import com.patra.starter.rocketmq.channels.ChannelCatalog;
import com.patra.starter.rocketmq.channels.ChannelRegistry;
import com.patra.starter.rocketmq.config.PatraChannelProperties;
import com.patra.starter.rocketmq.publisher.PatraMessagePublisher;
import com.patra.starter.rocketmq.publisher.RocketMQMessagePublisher;
import com.patra.starter.rocketmq.consumer.ConsumesRegistrar;
import com.patra.starter.rocketmq.support.PatraMessageFactory;
import com.patra.starter.rocketmq.support.RocketMQListenerAnnotationValidator;
import com.patra.starter.core.error.spi.TraceProvider;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 自定义 RocketMQ 自动配置，复用官方 starter 能力并注入自研规范组件。
 */
@AutoConfiguration
@EnableConfigurationProperties({PatraRocketMQProperties.class, PatraChannelProperties.class})
@ConditionalOnClass(RocketMQTemplate.class)
@ConditionalOnProperty(prefix = "patra.messaging.rocketmq", name = "enabled", havingValue = "true", matchIfMissing = true)
public class PatraRocketMQAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(PatraMessagePublisher.class)
    public PatraMessagePublisher patraMessagePublisher(RocketMQTemplate rocketMQTemplate,
                                                       PatraRocketMQProperties properties,
                                                       org.springframework.core.env.Environment environment,
                                                       org.springframework.beans.factory.ObjectProvider<HttpStdErrors.Group> httpErrorsProvider,
                                                       ChannelRegistry channelRegistry) {
        return new RocketMQMessagePublisher(rocketMQTemplate, properties, environment, httpErrorsProvider, channelRegistry);
    }

    @Bean
    @ConditionalOnMissingBean
    public ChannelRegistry channelRegistry(org.springframework.beans.factory.ObjectProvider<java.util.Collection<ChannelCatalog>> catalogsProvider,
                                           org.springframework.beans.factory.ObjectProvider<HttpStdErrors.Group> httpErrorsProvider,
                                           PatraChannelProperties channelProperties) {
        return new ChannelRegistry(catalogsProvider, httpErrorsProvider, channelProperties);
    }

    /**
     * 基于 @Consumes 注解的消费者注册器（运行时注册）。
     */
    @Bean
    @ConditionalOnMissingBean
    public ConsumesRegistrar consumesRegistrar(org.springframework.core.env.Environment environment,
                                               org.springframework.beans.factory.ObjectProvider<PatraRocketMQProperties> props) {
        return new ConsumesRegistrar(environment, props);
    }

    /**
     * 消费端注解启动期校验器：topic / selector / consumerGroup。
     */
    @Bean
    @ConditionalOnMissingBean
    public RocketMQListenerAnnotationValidator rocketMQListenerAnnotationValidator(PatraRocketMQProperties properties,
                                                                                   org.springframework.core.env.Environment environment) {
        return new RocketMQListenerAnnotationValidator(properties, environment);
    }

    /**
     * 消息工厂：从 TraceProvider 注入 traceId。
     * 若未引入 core-starter（无 TraceProvider），则注入一个无依赖的工厂实现。
     */
    @Bean
    @ConditionalOnMissingBean
    public PatraMessageFactory patraMessageFactory(org.springframework.beans.factory.ObjectProvider<TraceProvider> traceProvider) {
        TraceProvider provider = traceProvider.getIfAvailable();
        return provider != null ? new PatraMessageFactory(provider) : new PatraMessageFactory();
    }
}
