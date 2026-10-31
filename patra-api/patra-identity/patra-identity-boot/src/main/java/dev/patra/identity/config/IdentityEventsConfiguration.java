package dev.patra.identity.config;

import dev.patra.identity.api.account.event.SecurityMailEvent;
import dev.patra.identity.domain.account.model.vo.mail.OutgoingMail;
import dev.patra.identity.domain.account.policy.MailLinkPolicy;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.modulith.events.EventExternalizationConfiguration;
import org.springframework.modulith.events.RoutingTarget;

/// identity 发出的集成事件（PAP-104）：安全邮件在业务事务里登记进 `event_publication`，提交后由事件模块
/// （`patra-tool-events`）映射成 `patra-identity-api` 的集成事件、发到 topic 交换机 `identity.events`、等
/// broker 确认后删行；没发出去的由 `eventPublicationResubmitJob` 每分钟补投。
///
/// 只选安全邮件外送。领域层不贴 `@Externalized`，映射与路由都在这里（决策 B）。
@Configuration(proxyBeanMethods = false)
public class IdentityEventsConfiguration {

  /// identity 自己声明的交换机：消费方声明队列时也会声明它，两边不用约定先后。
  ///
  /// @return 持久化的 topic 交换机 `identity.events`
  @Bean
  public TopicExchange identityEventsExchange() {
    return ExchangeBuilder.topicExchange(SecurityMailEvent.EXCHANGE).durable(true).build();
  }

  /// 安全邮件到集成事件的映射器。
  ///
  /// @param links 邮件链接规则
  /// @return 映射器
  @Bean
  public SecurityMailEventMapper securityMailEventMapper(MailLinkPolicy links) {
    return new SecurityMailEventMapper(links);
  }

  /// 外送配置：选出安全邮件，映射成集成事件，按事件种类定路由键。
  ///
  /// @param mapper 映射器
  /// @return 外送配置
  @Bean
  public EventExternalizationConfiguration eventExternalizationConfiguration(
      SecurityMailEventMapper mapper) {
    return EventExternalizationConfiguration.externalizing()
        .selectByType(OutgoingMail.class)
        .mapping(OutgoingMail.class, mapper::toIntegrationEvent)
        .routeAll(
            event ->
                RoutingTarget.forTarget(SecurityMailEvent.EXCHANGE)
                    .andKey(SecurityMailEventMapper.routingKeyOf((SecurityMailEvent) event)))
        .routeMapped()
        .build();
  }
}
