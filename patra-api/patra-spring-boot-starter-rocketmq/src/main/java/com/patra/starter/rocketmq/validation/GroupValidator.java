package com.patra.starter.rocketmq.validation;

import java.util.regex.Pattern;

/**
 * 消费组命名校验器。
 *
 * @author linqibin
 * @since 0.1.0
 */
public class GroupValidator {

    private final Pattern groupPattern;

    public GroupValidator(String groupPatternRegex) {
        this.groupPattern = Pattern.compile(groupPatternRegex);
    }

    /**
     * 校验消费组命名。
     */
    public void validate(String consumerGroup) {
        if (consumerGroup == null || consumerGroup.isBlank()) {
            throw new IllegalArgumentException("ConsumerGroup 不能为空");
        }

        if (!groupPattern.matcher(consumerGroup).matches()) {
            throw new IllegalArgumentException(
                    "ConsumerGroup '" + consumerGroup + "' 不符合命名规范: " + groupPattern.pattern()
            );
        }
    }
}
