package com.patra.starter.rocketmq.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * RocketMQ 配置属性。
 *
 * @author linqibin
 * @since 0.1.0
 */
@ConfigurationProperties(prefix = "patra.messaging.rocketmq")
public class RocketMQProperties {

    /**
     * 是否启用。
     */
    private boolean enabled = true;

    private final Naming naming = new Naming();
    private final Channel channel = new Channel();
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

    public Channel getChannel() {
        return channel;
    }

    public Retry getRetry() {
        return retry;
    }

    /**
     * 命名策略配置。
     */
    public static class Naming {
        /**
         * 命名空间（可选，默认从 spring.profiles.active 推导）。
         */
        private String namespace;

        /**
         * Topic 命名正则。
         */
        private String topicPattern = "^[A-Z][A-Z0-9]*(\\.[A-Z0-9]+)*$";

        /**
         * 消费组命名正则。
         */
        private String consumerGroupPattern = "^[a-z][a-z0-9\\-]*$";

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

        public String getConsumerGroupPattern() {
            return consumerGroupPattern;
        }

        public void setConsumerGroupPattern(String consumerGroupPattern) {
            this.consumerGroupPattern = consumerGroupPattern;
        }
    }

    /**
     * Channel 配置。
     */
    public static class Channel {
        /**
         * 是否强制白名单校验。
         */
        private boolean enforceWhitelist = true;

        public boolean isEnforceWhitelist() {
            return enforceWhitelist;
        }

        public void setEnforceWhitelist(boolean enforceWhitelist) {
            this.enforceWhitelist = enforceWhitelist;
        }
    }

    /**
     * 重试配置。
     */
    public static class Retry {
        /**
         * 最大重试次数。
         */
        private int maxAttempts = 3;

        public int getMaxAttempts() {
            return maxAttempts;
        }

        public void setMaxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
        }
    }
}
