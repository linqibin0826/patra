package dev.linqibin.patra.starter.security.audit;

import dev.linqibin.patra.common.security.CurrentUser;
import dev.linqibin.patra.common.security.CurrentUserPort;
import dev.linqibin.starter.jpa.audit.CurrentAuditorProvider;
import java.util.Optional;

/// 用当前用户的 ID 作 JPA 审计的操作人。
///
/// 匿名请求、不带身份的后台线程没有当前用户，审计列留空。
public class CurrentUserAuditorProvider implements CurrentAuditorProvider {

  private final CurrentUserPort currentUserPort;

  /// 创建提供者。
  ///
  /// @param currentUserPort 取当前用户的端口
  public CurrentUserAuditorProvider(CurrentUserPort currentUserPort) {
    this.currentUserPort = currentUserPort;
  }

  /// 返回当前用户的 ID。
  ///
  /// @return 当前用户的 ID；没有当前用户时返回空
  @Override
  public Optional<Long> currentAuditorId() {
    return currentUserPort.current().map(CurrentUser::userId);
  }
}
