package dev.patra.tool.events.support;

import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.boot.amqp.autoconfigure.RabbitTemplateCustomizer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/// 模拟「broker 卡住、确认一直不回」：发送模板等的确认永远等不到，必然等满确认超时。
///
/// 发送时把登记到信道上的关联数据换成同 ID 的新对象：broker 的 ack / nack 完成的是新对象，模板等的原对象一直不完成。
/// 不靠「超时设得极短、ack 来不及」的时间赛跑。`@TestConfiguration` 不被组件扫描带进别的上下文，用的测试类自己
/// `@Import`。
@TestConfiguration(proxyBeanMethods = false)
public class EventsITUnreachableConfirmConfig {

  /// 换掉登记到信道上的关联数据。
  ///
  /// @return 模板定制
  @Bean
  RabbitTemplateCustomizer unreachableConfirmCustomizer() {
    return template ->
        template.setCorrelationDataPostProcessor(
            (message, correlation) ->
                correlation == null ? null : new CorrelationData(correlation.getId()));
  }
}
