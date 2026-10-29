package dev.patra.identity.domain.session.service;

import dev.patra.identity.domain.session.model.aggregate.UserLoginRecord;
import dev.patra.identity.domain.session.model.enums.LoginEndReason;
import dev.patra.identity.domain.session.model.vo.ClientOrigin;
import dev.patra.identity.domain.session.model.vo.IssuedUserSession;
import dev.patra.identity.domain.session.model.vo.LoginClient;
import dev.patra.identity.domain.session.model.vo.NewUserSession;
import dev.patra.identity.domain.session.policy.SessionLifetime;
import dev.patra.identity.domain.session.policy.SessionLifetimePolicy;
import dev.patra.identity.domain.session.port.repository.UserLoginRecordRepository;
import dev.patra.identity.domain.session.port.store.SessionStorePort;
import dev.patra.tool.core.security.AccountType;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/// 给前台用户建会话：存登录记录拿 ID，用它建 Redis 会话，收尾被挤掉的记录。
///
/// 不管事务：登录和注册的处理器各自包事务，Redis 写在事务里，失败就回滚记录。
public final class SessionIssuer {

  private final UserLoginRecordRepository records;
  private final SessionStorePort sessions;
  private final SessionLifetimePolicy policy;
  private final Clock clock;

  /// 创建领域服务。
  ///
  /// @param records 登录记录仓储
  /// @param sessions 会话存储端口
  /// @param policy 会话策略
  /// @param clock 时钟
  public SessionIssuer(
      UserLoginRecordRepository records,
      SessionStorePort sessions,
      SessionLifetimePolicy policy,
      Clock clock) {
    this.records = Objects.requireNonNull(records, "records 不能为 null");
    this.sessions = Objects.requireNonNull(sessions, "sessions 不能为 null");
    this.policy = Objects.requireNonNull(policy, "policy 不能为 null");
    this.clock = Objects.requireNonNull(clock, "clock 不能为 null");
  }

  /// 建会话。
  ///
  /// 1. 按客户端类型取有效期；2. 存登录记录拿到 ID；3. 以记录 ID 为会话 ID 建 Redis 会话；
  /// 4. 被挤掉的会话对应的记录标成 `REPLACED`。
  ///
  /// @param userId 用户 ID
  /// @param client 客户端信息
  /// @param origin 请求来源，写进登录记录
  /// @return 令牌和被挤掉的会话 ID
  public IssuedUserSession issue(long userId, LoginClient client, ClientOrigin origin) {
    Objects.requireNonNull(client, "client 不能为 null");
    Objects.requireNonNull(origin, "origin 不能为 null");
    Instant now = clock.instant();
    SessionLifetime lifetime = policy.lifetimeFor(client.clientType());
    UserLoginRecord login =
        records.save(UserLoginRecord.start(userId, client, origin, now.plus(lifetime.absolute())));
    IssuedUserSession issued =
        sessions.issue(
            NewUserSession.builder()
                .userId(userId)
                .sessionId(login.getId())
                .accountType(AccountType.USER)
                .client(client)
                .now(now)
                .lifetime(lifetime)
                .maxSessionsPerUser(policy.maxSessionsPerUser())
                .build());
    for (Long replaced : issued.replacedSessionIds()) {
      records
          .findById(replaced)
          .ifPresent(
              old -> {
                old.end(LoginEndReason.REPLACED, now);
                records.save(old);
              });
    }
    return issued;
  }
}
