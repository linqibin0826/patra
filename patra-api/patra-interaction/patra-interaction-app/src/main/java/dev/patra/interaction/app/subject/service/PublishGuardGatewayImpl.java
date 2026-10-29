package dev.patra.interaction.app.subject.service;

import dev.patra.interaction.domain.subject.exception.PublishIneligibleException;
import dev.patra.interaction.domain.subject.model.enums.AccountState;
import dev.patra.interaction.domain.subject.model.enums.PublishKind;
import dev.patra.interaction.domain.subject.model.read.UserCard;
import dev.patra.interaction.domain.subject.policy.PublishQuota;
import dev.patra.interaction.domain.subject.port.gateway.PublishGuardGateway;
import dev.patra.interaction.domain.subject.port.repository.PublishQuotaRepository;
import dev.patra.interaction.domain.subject.port.user.UserDirectoryPort;
import java.time.Clock;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/// 发表守卫：资格经 identity 批量摘要回查（访客是自己），频率按 `subject.publish_quota` 在作者锁里判。
@Component
@RequiredArgsConstructor
public class PublishGuardGatewayImpl implements PublishGuardGateway {

  private final UserDirectoryPort users;
  private final PublishQuotaRepository quotas;
  private final Clock clock;

  /// 回查自己的名片：账号不是 `ACTIVE`（含 identity 省略）→ 账号状态异常；邮箱未验证 → 邮箱未验证。
  ///
  /// @param userId 作者
  /// @return 自己的名片
  @Override
  public UserCard requireEligible(long userId) {
    UserCard self = users.findCards(List.of(userId), userId).get(userId);
    if (self == null || self.state() != AccountState.ACTIVE) {
      throw PublishIneligibleException.accountRestricted();
    }
    if (!self.emailVerified()) {
      throw PublishIneligibleException.emailUnverified();
    }
    return self;
  }

  /// 拿作者锁，读计数，按北京时间自然日判当天上限与间隔，放行时写回。
  ///
  /// @param userId 作者
  /// @param kind 发表种类
  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void admit(long userId, PublishKind kind) {
    quotas.lock(userId);
    PublishQuota next =
        PublishQuota.admit(quotas.find(userId, kind), kind, clock.instant(), PublishQuota.ZONE);
    quotas.save(userId, kind, next);
  }
}
