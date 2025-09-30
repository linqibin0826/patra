package com.patra.starter.rocketmq.validation;

import java.util.regex.Pattern;

/**
 * Topic 命名校验器。
 *
 * @author linqibin
 * @since 0.1.0
 */
public class TopicValidator {

    private final Pattern topicPattern;
    private final String namespace;

    public TopicValidator(String topicPatternRegex, String namespace) {
        this.topicPattern = Pattern.compile(topicPatternRegex);
        this.namespace = namespace != null ? namespace.toUpperCase() : "";
    }

    /**
     * 校验 Topic 命名。
     */
    public void validate(String topic) {
        if (topic == null || topic.isBlank()) {
            throw new IllegalArgumentException("Topic 不能为空");
        }

        if (!topicPattern.matcher(topic).matches()) {
            throw new IllegalArgumentException(
                    "Topic '" + topic + "' 不符合命名规范: " + topicPattern.pattern()
            );
        }

        if (!namespace.isEmpty() && !topic.startsWith(namespace + ".")) {
            throw new IllegalArgumentException(
                    "Topic '" + topic + "' 必须以命名空间 '" + namespace + ".' 开头"
            );
        }
    }
}
