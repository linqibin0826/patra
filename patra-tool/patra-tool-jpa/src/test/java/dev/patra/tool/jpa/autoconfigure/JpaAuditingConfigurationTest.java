package dev.patra.tool.jpa.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.tool.jpa.audit.CurrentAuditorProvider;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.data.domain.AuditorAware;

/// JpaAuditingConfiguration 单元测试：默认审计人怎么接入 CurrentAuditorProvider。
@DisplayName("JpaAuditingConfiguration 单元测试")
class JpaAuditingConfigurationTest {

  private final JpaAuditingConfiguration config = new JpaAuditingConfiguration();
  private final DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();

  @Test
  @DisplayName("容器里没有操作人提供者时，审计人为空")
  void should_return_empty_auditor_when_no_provider() {
    AuditorAware<Long> auditorAware =
        config.auditorAware(beanFactory.getBeanProvider(CurrentAuditorProvider.class));

    assertThat(auditorAware.getCurrentAuditor()).isEmpty();
  }

  @Test
  @DisplayName("容器里有操作人提供者时，每次都向它要当前操作人")
  void should_ask_provider_on_every_call_when_provider_present() {
    AtomicReference<Optional<Long>> currentAuditor = new AtomicReference<>(Optional.of(42L));
    CurrentAuditorProvider provider = currentAuditor::get;
    beanFactory.registerSingleton("currentAuditorProvider", provider);

    AuditorAware<Long> auditorAware =
        config.auditorAware(beanFactory.getBeanProvider(CurrentAuditorProvider.class));

    assertThat(auditorAware.getCurrentAuditor()).contains(42L);
    currentAuditor.set(Optional.empty());
    assertThat(auditorAware.getCurrentAuditor()).isEmpty();
  }
}
