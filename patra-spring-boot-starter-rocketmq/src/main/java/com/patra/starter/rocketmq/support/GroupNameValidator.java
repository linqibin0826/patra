package com.patra.starter.rocketmq.support;

import com.patra.starter.rocketmq.config.PatraRocketMQProperties;
import org.springframework.util.StringUtils;

import java.util.regex.Pattern;

/**
 * Consumer Group 命名校验器，确保消费组遵循统一规范，避免运维侧混乱。
 */
public final class GroupNameValidator {

    private GroupNameValidator() {
    }

    /**
     * 校验消费组名称。
     * @param consumerGroup 消费组
     * @param naming 命名配置
     */
    public static void validate(String consumerGroup, PatraRocketMQProperties.Naming naming) {
        if (!StringUtils.hasText(consumerGroup)) {
            throw new IllegalArgumentException("ConsumerGroup 不能为空");
        }
        Pattern pattern = Pattern.compile(naming.getConsumerGroupPattern());
        if (!pattern.matcher(consumerGroup).matches()) {
            throw new IllegalArgumentException("ConsumerGroup '" + consumerGroup + "' 不符合命名规范 " + naming.getConsumerGroupPattern());
        }
    }
}

