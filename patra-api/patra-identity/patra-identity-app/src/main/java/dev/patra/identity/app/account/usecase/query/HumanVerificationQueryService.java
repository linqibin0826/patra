package dev.patra.identity.app.account.usecase.query;

import dev.patra.identity.domain.account.model.enums.HumanVerificationProvider;
import dev.patra.identity.domain.account.policy.HumanVerificationPolicy;
import java.util.Map;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/// 门户读取当前厂商和站点标识。当前厂商只配置在 identity，门户不另配一份，切换时门户不用重新部署。
@Component
@RequiredArgsConstructor
public class HumanVerificationQueryService {

  private final Supplier<HumanVerificationPolicy> policy;
  private final Supplier<Map<HumanVerificationProvider, String>> siteKeys;

  /// 当前厂商。
  ///
  /// @return 厂商的线上值和站点标识
  public HumanVerificationView current() {
    HumanVerificationProvider provider = policy.get().provider();
    return HumanVerificationView.of(provider.wireValue(), siteKeys.get().get(provider));
  }
}
