package dev.patra.identity.config;

import dev.patra.identity.domain.account.model.enums.HumanVerificationProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.cloud.context.scope.refresh.RefreshScopeRefreshedEvent;
import org.springframework.context.event.EventListener;

/// 当前厂商是 TRUSTTURN 时提醒一次。监听 `RefreshScopeRefreshedEvent`，它在配置重新绑定之后才发出，读到的是新值。
@Slf4j
@RequiredArgsConstructor
public class HumanVerificationProviderNotice {

  private final HumanVerificationProperties properties;

  /// 启动完成和配置刷新后各检查一次。
  @EventListener({ApplicationReadyEvent.class, RefreshScopeRefreshedEvent.class})
  public void warnIfTrustTurn() {
    if (properties.getProvider() == HumanVerificationProvider.TRUST_TURN) {
      log.warn("当前人机验证厂商是 TRUSTTURN：访客的 IP 和浏览器信息会发给它，它的运营方和数据处理方式都没有公开说明");
    }
  }
}
