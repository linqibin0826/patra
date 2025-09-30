package com.patra.starter.rocketmq.autoconfigure;

import com.patra.common.error.codes.HttpStdErrors;
import com.patra.starter.core.error.spi.TraceProvider;
import com.patra.starter.rocketmq.consumer.ConsumerBootstrap;
import com.patra.starter.rocketmq.core.destination.ChannelDestinationConverter;
import com.patra.starter.rocketmq.core.message.MessageFactory;
import com.patra.starter.rocketmq.naming.NamespaceResolver;
import com.patra.starter.rocketmq.publisher.DefaultMessagePublisher;
import com.patra.starter.rocketmq.publisher.MessagePublisher;
import com.patra.starter.rocketmq.validation.GroupValidator;
import com.patra.starter.rocketmq.validation.TopicValidator;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

/**
 * RocketMQ 自动配置。
 *
 * @author linqibin
 * @since 0.1.0
 */
@AutoConfiguration
@EnableConfigurationProperties(RocketMQProperties.class)
@ConditionalOnClass(RocketMQTemplate.class)
@ConditionalOnProperty(prefix = "patra.messaging.rocketmq", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RocketMQAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public TopicValidator topicValidator(Environment environment, RocketMQProperties properties) {
        String namespace = NamespaceResolver.resolve(environment, properties.getNaming().getNamespace());
        return new TopicValidator(properties.getNaming().getTopicPattern(), namespace);
    }

    @Bean
    @ConditionalOnMissingBean
    public GroupValidator groupValidator(RocketMQProperties properties) {
        return new GroupValidator(properties.getNaming().getConsumerGroupPattern());
    }

    @Bean
    @ConditionalOnMissingBean
    public ChannelDestinationConverter channelDestinationConverter(Environment environment, RocketMQProperties properties) {
        String namespace = NamespaceResolver.resolve(environment, properties.getNaming().getNamespace());
        return new ChannelDestinationConverter(namespace);
    }

    @Bean
    @ConditionalOnMissingBean
    public MessageFactory messageFactory(ObjectProvider<TraceProvider> traceProvider) {
        TraceProvider provider = traceProvider.getIfAvailable();
        return provider != null ? new MessageFactory(provider) : new MessageFactory();
    }

    @Bean
    @ConditionalOnMissingBean
    public MessagePublisher messagePublisher(RocketMQTemplate rocketMQTemplate,
                                             ChannelDestinationConverter channelDestinationConverter,
                                             TopicValidator topicValidator,
                                             ObjectProvider<HttpStdErrors.Group> httpErrorsProvider) {
        return new DefaultMessagePublisher(
                rocketMQTemplate,
                channelDestinationConverter,
                topicValidator,
                httpErrorsProvider
        );
    }

    @Bean
    @ConditionalOnMissingBean
    public ConsumerBootstrap consumerBootstrap(Environment environment,
                                               RocketMQProperties properties,
                                               TopicValidator topicValidator,
                                               GroupValidator groupValidator) {
        return new ConsumerBootstrap(environment, properties, topicValidator, groupValidator);
    }
}
