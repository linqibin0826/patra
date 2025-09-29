package com.patra.ingest.infra.messaging.support;

import com.patra.starter.rocketmq.channels.PatraMessagingChannels;
import org.springframework.stereotype.Component;

/**
 * Ingest 模块声明的标准消息通道（channel）。
 * <p>通过注解仅声明，无需 YAML 配置；RocketMQ Starter 将在启动时收集并用于运行期校验。</p>
 */
@Component
@PatraMessagingChannels({
        "ingest.task.ready"
})
public class IngestMessagingChannels { }

