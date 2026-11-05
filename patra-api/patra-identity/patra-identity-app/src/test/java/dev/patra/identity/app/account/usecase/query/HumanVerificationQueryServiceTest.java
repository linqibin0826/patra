package dev.patra.identity.app.account.usecase.query;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.identity.domain.account.model.enums.HumanVerificationProvider;
import dev.patra.identity.domain.account.policy.HumanVerificationPolicy;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// 门户读取当前厂商和站点标识。
@DisplayName("HumanVerificationQueryService")
class HumanVerificationQueryServiceTest {

  private final AtomicReference<HumanVerificationProvider> provider =
      new AtomicReference<>(HumanVerificationProvider.TURNSTILE);
  private final AtomicReference<Map<HumanVerificationProvider, String>> siteKeys =
      new AtomicReference<>(
          Map.of(
              HumanVerificationProvider.TURNSTILE, "turnstile-site",
              HumanVerificationProvider.TRUST_TURN, "trustturn-site"));
  private final HumanVerificationQueryService service =
      new HumanVerificationQueryService(
          () -> HumanVerificationPolicy.of(provider.get(), 3, 5, Duration.ofMinutes(15)),
          siteKeys::get);

  @Test
  @DisplayName("返回当前厂商的线上值和站点标识")
  void returns_current_provider_and_site_key() {
    assertThat(service.current())
        .isEqualTo(HumanVerificationView.of("turnstile", "turnstile-site"));
  }

  @Test
  @DisplayName("切换后返回新厂商")
  void returns_new_provider_after_switch() {
    provider.set(HumanVerificationProvider.TRUST_TURN);

    assertThat(service.current())
        .isEqualTo(HumanVerificationView.of("trustturn", "trustturn-site"));
  }

  @Test
  @DisplayName("切到缺站点标识的厂商：照常返回，站点标识为空")
  void site_key_is_null_when_not_configured() {
    siteKeys.set(Map.of());

    assertThat(service.current()).isEqualTo(HumanVerificationView.of("turnstile", null));
  }
}
