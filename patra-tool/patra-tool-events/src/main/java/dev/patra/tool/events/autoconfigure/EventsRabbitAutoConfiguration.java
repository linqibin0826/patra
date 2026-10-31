package dev.patra.tool.events.autoconfigure;

import dev.patra.tool.events.publisher.ConfirmingRabbitMessagingTemplate;
import org.springframework.amqp.rabbit.core.RabbitMessagingTemplate;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.amqp.autoconfigure.RabbitAutoConfiguration;
import org.springframework.boot.amqp.autoconfigure.RabbitTemplateCustomizer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import tools.jackson.databind.json.JsonMapper;

/// RabbitMQ 收发的装配：收发共用的 JSON 转换器、`mandatory`、等确认的发送模板。
///
/// 排在 Boot 的 `RabbitAutoConfiguration` 之前：Boot 见到已有 `RabbitMessagingTemplate` 就不再建，
/// Modulith 的 AMQP 外送器拿到的 `RabbitMessageOperations` 就是 {@link ConfirmingRabbitMessagingTemplate}。
@AutoConfiguration(before = RabbitAutoConfiguration.class)
@ConditionalOnClass(RabbitMessagingTemplate.class)
@EnableConfigurationProperties(EventsProperties.class)
public final class EventsRabbitAutoConfiguration {

  /// 收发共用的 JSON 转换器，用应用里的 `JsonMapper`；Boot 把它同时装到 `RabbitTemplate` 和监听容器上。
  ///
  /// 监听方法参数是具体类型时按参数类型反序列化；一个监听类用多个 `@RabbitHandler` 时按消息头里的类型名，
  /// 这时只认 `patra.events.consumer.trusted-packages` 里的包。
  ///
  /// @param jsonMapper 应用的 JSON 映射器
  /// @param properties 模块配置
  /// @return 转换器
  @Bean
  @ConditionalOnMissingBean(MessageConverter.class)
  public JacksonJsonMessageConverter eventsMessageConverter(
      JsonMapper jsonMapper, EventsProperties properties) {
    return new JacksonJsonMessageConverter(
        jsonMapper, properties.consumer().trustedPackages().toArray(String[]::new));
  }

  /// 所有发送带 `mandatory`：没有队列可投时 broker 退回消息，等确认的模板据此判失败。不走属性，服务不能关掉。
  ///
  /// @return 模板定制
  @Bean
  public RabbitTemplateCustomizer eventsMandatoryCustomizer() {
    return template -> template.setMandatory(true);
  }

  /// 等确认的发送模板，替换 Boot 的 `RabbitMessagingTemplate`。
  ///
  /// @param rabbitTemplate Boot 建的 `RabbitTemplate`
  /// @param properties 模块配置
  /// @return 发送模板
  @Bean
  public ConfirmingRabbitMessagingTemplate confirmingRabbitMessagingTemplate(
      RabbitTemplate rabbitTemplate, EventsProperties properties) {
    return new ConfirmingRabbitMessagingTemplate(
        rabbitTemplate, properties.publisher().confirmTimeout());
  }
}
