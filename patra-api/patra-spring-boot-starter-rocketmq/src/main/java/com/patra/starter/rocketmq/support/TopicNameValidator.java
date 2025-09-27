package com.patra.starter.rocketmq.support;

import com.patra.starter.rocketmq.config.PatraRocketMQProperties;
import org.springframework.util.StringUtils;

import java.util.regex.Pattern;

/**
 * Topic 命名校验器，确保所有消息遵循统一前缀与正则约束。
 */
public final class TopicNameValidator {

    private TopicNameValidator() {
    }

    public static void validate(String destination, PatraRocketMQProperties.Naming naming) {
        if (!StringUtils.hasText(destination)) {
            throw new IllegalArgumentException("RocketMQ destination 不能为空");
        }
        String topic = destination;
        int idx = destination.indexOf(':');
        if (idx > -1) {
            topic = destination.substring(0, idx);
        }
        Pattern pattern = Pattern.compile(naming.getTopicPattern());
        if (!pattern.matcher(topic).matches()) {
            throw new IllegalArgumentException("Topic " + topic + " 不符合命名规范 " + naming.getTopicPattern());
        }
    }
}
