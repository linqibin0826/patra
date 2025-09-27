package com.patra.starter.rocketmq.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * RocketMQ 统一属性入口，避免业务直接操作官方 starter 的多套配置。
 */
@ConfigurationProperties(prefix = "patra.messaging.rocketmq")
public class PatraRocketMQProperties {

    /**
     * 是否启用自定义规范能力。
     */
    private boolean enabled = true;

    private final Naming naming = new Naming();

    private final Retry retry = new Retry();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Naming getNaming() {
        return naming;
    }

    public Retry getRetry() {
        return retry;
    }

    /**
     * Topic/Tag 命名策略，配合 {@link com.patra.messaging.rocketmq.support.TopicNameValidator} 使用。
     */
    public static class Naming {
        /**
         * 业务 Topic 前缀，例如 INGEST、REGISTRY 等。
         */
        private String namespace;
        /**
         * 允许的命名正则，默认：字母/数字/点号组合，要求大写开头。
         */
        private String topicPattern = "^[A-Z][A-Z0-9]*(\\.[A-Z0-9]+)*$";
        /**
         * 默认 Tag 分隔符。
         */
        private String tagDelimiter = ".";

        public String getNamespace() {
            return namespace;
        }

        public void setNamespace(String namespace) {
            this.namespace = namespace;
        }

        public String getTopicPattern() {
            return topicPattern;
        }

        public void setTopicPattern(String topicPattern) {
            this.topicPattern = topicPattern;
        }

        public String getTagDelimiter() {
            return tagDelimiter;
        }

        public void setTagDelimiter(String tagDelimiter) {
            this.tagDelimiter = tagDelimiter;
        }
    }

    /**
     * 统一的消费重试策略，供基类监听器引用。
     */
    public static class Retry {
        /**
         * 最大重试次数，默认 3 次。
         */
        private int maxAttempts = 3;
        /**
         * 每次重试的退避间隔（指数退避初值）。
         */
        private Duration backoff = Duration.ofSeconds(1);

        public int getMaxAttempts() {
            return maxAttempts;
        }

        public void setMaxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
        }

        public Duration getBackoff() {
            return backoff;
        }

        public void setBackoff(Duration backoff) {
            this.backoff = backoff;
        }
    }
}
