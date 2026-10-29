package dev.patra.identity.app.session.usecase.query;

import dev.patra.identity.domain.session.port.store.SessionStorePort;
import dev.patra.tool.core.security.CurrentUser;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/// 按令牌查会话（`POST /_internal/sessions/introspect`，PAP-106）：网关每个带令牌的请求都调到这里。
///
/// 在请求路径上，不经 CommandBus，没有事务；续期（60 秒节流）由会话存储在同一段脚本里完成。
/// 令牌是凭据，不打日志。
@Service
@RequiredArgsConstructor
public class SessionIntrospectionService {

  private final SessionStorePort sessions;

  /// 按令牌查会话并节流续期。
  ///
  /// @param token 令牌原文
  /// @return 当前用户；令牌格式不对、会话不在或已过绝对期时为空
  public Optional<CurrentUser> introspect(String token) {
    return sessions.findAndTouch(token);
  }
}
