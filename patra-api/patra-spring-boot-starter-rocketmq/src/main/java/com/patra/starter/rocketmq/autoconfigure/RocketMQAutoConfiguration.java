package com.patra.starter.rocketmq.autoconfigure;

import com.patra.common.error.codes.HttpStdErrors;
import com.patra.common.messaging.ChannelKey;
import com.patra.starter.core.error.spi.TraceProvider;
import com.patra.starter.rocketmq.consumer.ConsumerBootstrap;
import com.patra.starter.rocketmq.core.channel.ChannelRegistry;
import com.patra.starter.rocketmq.core.destination.DestinationBuilder;
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
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.env.Environment;
import org.springframework.core.type.filter.AssignableTypeFilter;

import java.util.ArrayList;
import java.util.List;

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
    public ChannelRegistry channelRegistry(RocketMQProperties properties) {
        // 扫描所有实现 ChannelKey 的枚举类
        List<Class<? extends Enum<? extends ChannelKey>>> channelEnums = scanChannelEnums();
        return new ChannelRegistry(channelEnums, properties.getChannel().isEnforceWhitelist());
    }

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
    public DestinationBuilder destinationBuilder(Environment environment, RocketMQProperties properties) {
        String namespace = NamespaceResolver.resolve(environment, properties.getNaming().getNamespace());
        return new DestinationBuilder(namespace);
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
                                             DestinationBuilder destinationBuilder,
                                             ChannelRegistry channelRegistry,
                                             TopicValidator topicValidator,
                                             ObjectProvider<HttpStdErrors.Group> httpErrorsProvider) {
        return new DefaultMessagePublisher(
                rocketMQTemplate,
                destinationBuilder,
                channelRegistry,
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

    /**
     * 扫描所有实现 ChannelKey 的枚举类。
     */
    @SuppressWarnings("unchecked")
    private List<Class<? extends Enum<? extends ChannelKey>>> scanChannelEnums() {
        List<Class<? extends Enum<? extends ChannelKey>>> result = new ArrayList<>();
        try {
            ClassPathScanningCandidateComponentProvider scanner = 
                    new ClassPathScanningCandidateComponentProvider(false);
            scanner.addIncludeFilter(new AssignableTypeFilter(ChannelKey.class));

            // 扫描常见包路径
            String[] basePackages = {"com.patra"};
            for (String basePackage : basePackages) {
                scanner.findCandidateComponents(basePackage).forEach(beanDef -> {
                    try {
                        Class<?> clazz = Class.forName(beanDef.getBeanClassName());
                        if (clazz.isEnum() && ChannelKey.class.isAssignableFrom(clazz)) {
                            result.add((Class<? extends Enum<? extends ChannelKey>>) clazz);
                        }
                    } catch (ClassNotFoundException e) {
                        // 忽略
                    }
                });
            }
        } catch (Exception e) {
            // 扫描失败不影响启动，只是不会自动注册
        }
        return result;
    }
}
