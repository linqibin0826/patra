package dev.patra.identity.infra.account.mail;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import dev.patra.identity.domain.account.model.vo.mail.OutgoingMail;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

/// OutboxEmailDeliveryAdapter 单元测试：邮件本身就是登记进事件发布登记表的领域事件。
///
/// 「没有事务就拒绝」靠 `@Transactional(propagation = MANDATORY)` 的代理，在 boot 的 IT 里验证。
@DisplayName("OutboxEmailDeliveryAdapter 单元测试")
class OutboxEmailDeliveryAdapterTest {

  private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
  private final OutboxEmailDeliveryAdapter adapter = new OutboxEmailDeliveryAdapter(events);

  @Test
  @DisplayName("把邮件原样作为领域事件发布，不另包一层")
  void should_publish_the_mail_itself_as_domain_event() {
    OutgoingMail mail = SecurityMailFixture.all().getFirst();

    adapter.send(mail);

    verify(events).publishEvent(mail);
    verifyNoMoreInteractions(events);
  }
}
