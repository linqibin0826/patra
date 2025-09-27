package com.patra.starter.rocketmq.config;

import com.patra.starter.rocketmq.publisher.PatraMessagePublisher;
import com.patra.starter.rocketmq.publisher.RocketMQMessagePublisher;
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
@EnableConfigurationProperties(PatraRocketMQProperties.class)
@ConditionalOnClass(RocketMQTemplate.class)
@ConditionalOnProperty(prefix = "patra.messaging.rocketmq", name = "enabled", havingValue = "true", matchIfMissing = true)
public class PatraRocketMQAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(PatraMessagePublisher.class)
    public PatraMessagePublisher patraMessagePublisher(RocketMQTemplate rocketMQTemplate,
                                                       PatraRocketMQProperties properties) {
        return new RocketMQMessagePublisher(rocketMQTemplate, properties);
    }
}
