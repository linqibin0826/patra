package dev.patra.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.patra.identity.api.account.event.EmailVerificationMailEvent;
import dev.patra.identity.api.account.event.SecurityMailEvent;
import dev.patra.identity.config.SecurityMailEventMapper;
import dev.patra.identity.domain.account.model.vo.mail.OutgoingMail;
import dev.patra.identity.domain.account.port.mail.EmailDeliveryPort;
import dev.patra.identity.infra.account.mail.SecurityMailFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.modulith.events.core.EventSerializer;
import org.springframework.transaction.IllegalTransactionStateException;

/// 安全邮件经事件模块外送（PAP-104）：只能在事务里登记、登记表与消息两头都能原样还原、送达后登记行被删。
@DisplayName("安全邮件外送")
class SecurityMailOutboxIT extends IdentityFlowITSupport {

  @Autowired EmailDeliveryPort emailDelivery;
  @Autowired EventSerializer eventSerializer;
  @Autowired MessageConverter messageConverter;
  @Autowired SecurityMailEventMapper mapper;

  @Test
  @DisplayName("没有事务时登记直接失败：事务外发布会被悄悄丢掉，宁可立刻报错")
  void should_reject_send_outside_transaction() {
    OutgoingMail mail = SecurityMailFixture.all().getFirst();

    assertThatThrownBy(() -> emailDelivery.send(mail))
        .isInstanceOf(IllegalTransactionStateException.class);
    assertThat(securityMails.registryRows()).isZero();
  }

  @Test
  @DisplayName("13 种领域邮件经登记表的序列化器往返不变：补投时还原出同一封、同一个事件 ID（含设备与 IP 都缺的通知）")
  void should_round_trip_every_domain_mail_through_registry_serializer() {
    for (OutgoingMail mail : SecurityMailFixture.all()) {
      Object serialized = eventSerializer.serialize(mail);

      assertThat(eventSerializer.deserialize(serialized, mail.getClass()))
          .as(mail.getClass().getSimpleName())
          .isEqualTo(mail);
    }
  }

  @Test
  @DisplayName("13 种集成事件经消息转换器往返不变：消费端按 __TypeId__ 还原出具体类型")
  void should_round_trip_every_integration_event_through_message_converter() {
    for (OutgoingMail mail : SecurityMailFixture.all()) {
      SecurityMailEvent event = mapper.toIntegrationEvent(mail);

      Object restored =
          messageConverter.fromMessage(messageConverter.toMessage(event, new MessageProperties()));

      assertThat(restored).as(event.getClass().getSimpleName()).isEqualTo(event);
    }
  }

  @Test
  @DisplayName("注册后验证邮件事件送达，登记行随即删除（完成模式 delete，一次性令牌不久留）")
  void should_delete_registry_row_after_delivery_and_use_delete_mode() {
    String email = "flow.outbox@example.com";
    register(email, "u-flow-outbox");

    EmailVerificationMailEvent event = mailEvent(EmailVerificationMailEvent.class, email);

    assertThat(event.verifyUrl()).contains("#token=");
    securityMails.awaitRegistryDrained();
    assertThat(securityMails.registryRows()).isZero();
    assertThat(environment.getProperty("spring.modulith.events.completion-mode"))
        .isEqualTo("delete");
  }
}
