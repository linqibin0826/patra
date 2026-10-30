package dev.patra.tool.events.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import dev.patra.tool.events.publisher.ConfirmingRabbitMessagingTemplate;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitMessageOperations;
import org.springframework.amqp.rabbit.core.RabbitMessagingTemplate;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.amqp.autoconfigure.RabbitAutoConfiguration;
import org.springframework.boot.amqp.autoconfigure.RabbitTemplateCustomizer;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

@DisplayName("EventsRabbitAutoConfiguration 契约")
class EventsRabbitAutoConfigurationTest {

  /// 测试用的消息载荷，包名即本测试类的包。
  ///
  /// @param name 名称
  record Payload(String name) {}

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  EventsRabbitAutoConfiguration.class,
                  RabbitAutoConfiguration.class,
                  JacksonAutoConfiguration.class))
          .withPropertyValues(
              "spring.rabbitmq.publisher-confirm-type=correlated",
              "spring.rabbitmq.publisher-returns=true");

  @Test
  @DisplayName("用等确认的模板替换 Boot 的 RabbitMessagingTemplate")
  void should_replace_boot_messaging_template() {
    runner.run(
        context -> {
          assertThat(context).hasSingleBean(RabbitMessagingTemplate.class);
          assertThat(context.getBean(RabbitMessageOperations.class))
              .isInstanceOf(ConfirmingRabbitMessagingTemplate.class);
        });
  }

  @Test
  @DisplayName("RabbitTemplate 一律带 mandatory")
  void should_make_rabbit_template_mandatory() {
    runner.run(
        context -> {
          RabbitTemplate template = mock(RabbitTemplate.class);
          context
              .getBean("eventsMandatoryCustomizer", RabbitTemplateCustomizer.class)
              .customize(template);
          verify(template).setMandatory(true);
        });
  }

  @Test
  @DisplayName("JSON 转换器按类型头反序列化配置里信任的包")
  void should_convert_json_of_trusted_packages_by_type_header() {
    runner
        .withPropertyValues(
            "patra.events.consumer.trusted-packages=dev.patra.tool.events.autoconfigure")
        .run(
            context -> {
              MessageConverter converter = context.getBean(MessageConverter.class);
              Message message = converter.toMessage(new Payload("a"), new MessageProperties());
              assertThat(converter.fromMessage(message)).isEqualTo(new Payload("a"));
            });
  }

  @Test
  @DisplayName("没配信任的包时不按类型头反序列化 Patra 的类型")
  void should_refuse_untrusted_type_header() {
    runner.run(
        context -> {
          MessageConverter converter = context.getBean(MessageConverter.class);
          Message message = converter.toMessage(new Payload("a"), new MessageProperties());
          assertThatThrownBy(() -> converter.fromMessage(message))
              .isInstanceOf(IllegalArgumentException.class)
              .hasMessageContaining("not in the trusted packages");
        });
  }

  @Test
  @DisplayName("连接没开确认时启动失败，报错写明配置键")
  void should_fail_to_start_without_publisher_confirms() {
    new ApplicationContextRunner()
        .withConfiguration(
            AutoConfigurations.of(
                EventsRabbitAutoConfiguration.class,
                RabbitAutoConfiguration.class,
                JacksonAutoConfiguration.class))
        .run(
            context ->
                assertThat(context.getStartupFailure())
                    .rootCause()
                    .hasMessageContaining("spring.rabbitmq.publisher-confirm-type"));
  }

  @Test
  @DisplayName("模块配置的默认值")
  void should_bind_properties_defaults() {
    runner.run(
        context -> {
          EventsProperties properties = context.getBean(EventsProperties.class);
          assertThat(properties.publisher().confirmTimeout()).isEqualTo(Duration.ofSeconds(5));
          assertThat(properties.resubmit().minAge()).isEqualTo(Duration.ofMinutes(1));
          assertThat(properties.consumer().trustedPackages()).isEmpty();
        });
  }
}
