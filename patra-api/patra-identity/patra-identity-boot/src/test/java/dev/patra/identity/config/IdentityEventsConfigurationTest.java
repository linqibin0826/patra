package dev.patra.identity.config;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.identity.api.account.event.SecurityMailEvent;
import dev.patra.identity.domain.account.model.vo.mail.OutgoingMail;
import dev.patra.identity.domain.account.policy.MailLinkPolicy;
import dev.patra.identity.infra.account.mail.SecurityMailFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.modulith.events.EventExternalizationConfiguration;
import org.springframework.modulith.events.RoutingTarget;

/// IdentityEventsConfiguration 单元测试：外送配置只选安全邮件，映射成集成事件，发到 `identity.events`。
@DisplayName("IdentityEventsConfiguration 单元测试")
class IdentityEventsConfigurationTest {

  private static final MailLinkPolicy LINKS =
      MailLinkPolicy.builder()
          .baseUrl("https://patra.example")
          .verifyEmailPath("/verify-email")
          .securitySettingsPath("/settings/security")
          .resetPasswordPath("/reset-password")
          .confirmEmailChangePath("/confirm-email-change")
          .revertEmailChangePath("/revert-email-change")
          .build();

  private final IdentityEventsConfiguration configuration = new IdentityEventsConfiguration();
  private final SecurityMailEventMapper mapper = configuration.securityMailEventMapper(LINKS);
  private final EventExternalizationConfiguration externalization =
      configuration.eventExternalizationConfiguration(mapper);

  @Test
  @DisplayName("13 封安全邮件都外送：映射成集成事件，目标 identity.events、路由键按种类")
  void should_externalize_every_security_mail() {
    for (OutgoingMail mail : SecurityMailFixture.all()) {
      assertThat(externalization.supports(mail)).as(mail.getClass().getSimpleName()).isTrue();
      Object mapped = externalization.map(mail);
      assertThat(mapped).isInstanceOf(SecurityMailEvent.class);
      RoutingTarget target = externalization.determineTarget(mail);
      assertThat(target.getTarget()).isEqualTo("identity.events");
      assertThat(target.getKey())
          .isEqualTo(SecurityMailEventMapper.routingKeyOf((SecurityMailEvent) mapped));
    }
  }

  @Test
  @DisplayName("别的对象不外送")
  void should_not_select_other_events() {
    assertThat(externalization.supports(new Object())).isFalse();
    assertThat(externalization.supports("identity")).isFalse();
  }

  @Test
  @DisplayName("交换机：持久化的 topic 交换机 identity.events")
  void should_declare_durable_topic_exchange() {
    TopicExchange exchange = configuration.identityEventsExchange();

    assertThat(exchange.getName()).isEqualTo("identity.events");
    assertThat(exchange.isDurable()).isTrue();
    assertThat(exchange.isAutoDelete()).isFalse();
  }
}
