package com.patra.starter.rocketmq.support;

import com.patra.starter.rocketmq.config.PatraRocketMQProperties;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.env.Environment;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.util.StringUtils;

import java.util.regex.Pattern;

/**
 * 启动期校验 {@link RocketMQMessageListener} 注解中的 topic / selectorExpression / consumerGroup。
 * 不合规直接 fail-fast，避免线上污染 Topic 与消费组命名。
 */
@Slf4j
public class RocketMQListenerAnnotationValidator implements BeanPostProcessor {

    private final PatraRocketMQProperties properties;
    private final Environment environment;

    public RocketMQListenerAnnotationValidator(PatraRocketMQProperties properties, Environment environment) {
        this.properties = properties;
        this.environment = environment;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        RocketMQMessageListener ann = AnnotatedElementUtils.findMergedAnnotation(bean.getClass(), RocketMQMessageListener.class);
        if (ann != null) {
            validateTopic(ann.topic());
            validateSelector(ann.selectorExpression());
            GroupNameValidator.validate(ann.consumerGroup(), properties.getNaming());
            if (log.isDebugEnabled()) {
                log.debug("RocketMQ listener 校验通过: bean={} group={} topic={} selector={}", beanName, ann.consumerGroup(), ann.topic(), ann.selectorExpression());
            }
        }
        return bean;
    }

    private void validateTopic(String topic) {
        String namespace = com.patra.starter.rocketmq.support.EnvNamespaceResolver.resolve(environment, properties.getNaming());
        String pattern = properties.getNaming().getTopicPattern();
        if (!StringUtils.hasText(topic)) {
            throw new IllegalArgumentException("@RocketMQMessageListener.topic 不能为空");
        }
        Pattern compiled = Pattern.compile(pattern);
        if (!compiled.matcher(topic).matches()) {
            throw new IllegalArgumentException("@RocketMQMessageListener.topic '" + topic + "' 不符合命名规范 " + pattern);
        }
        if (StringUtils.hasText(namespace) && !topic.startsWith(namespace.toUpperCase() + ".")) {
            throw new IllegalArgumentException("@RocketMQMessageListener.topic '" + topic + "' 必须以 '" + namespace.toUpperCase() + " .' 开头");
        }
    }

    private void validateSelector(String selector) {
        // 支持 * 或 单标签（大写+点分段），不允许 OR 表达式，避免运维风险
        if (!StringUtils.hasText(selector) || "*".equals(selector)) {
            return; // 允许全标签
        }
        if (selector.contains("||") || selector.contains("|")) {
            throw new IllegalArgumentException("@RocketMQMessageListener.selectorExpression 暂不支持 'OR' 复合表达式：" + selector);
        }
        if (!selector.matches("^[A-Z0-9]+(\\.[A-Z0-9]+)*$")) {
            throw new IllegalArgumentException("@RocketMQMessageListener.selectorExpression 不符合命名规范（大写+点分段）：" + selector);
        }
    }
}
