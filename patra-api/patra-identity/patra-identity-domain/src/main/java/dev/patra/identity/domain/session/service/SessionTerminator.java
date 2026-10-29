package dev.patra.identity.domain.session.service;

import dev.patra.identity.domain.session.model.aggregate.UserLoginRecord;
import dev.patra.identity.domain.session.model.enums.LoginEndReason;
import dev.patra.identity.domain.session.port.repository.UserLoginRecordRepository;
import dev.patra.identity.domain.session.port.store.SessionStorePort;
import dev.patra.tool.core.security.AccountType;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/// 撤销前台用户的会话，并按原因结束对应的登录记录（PAP-99 设计第 9.3 节）。
///
/// 先删 Redis（撤销的实质），再改记录；Redis 失败时异常原样传出，记录不动。不管事务：
/// 调用方在自己的事务里调，和 {@link SessionIssuer} 一样。记录不存在的跳过，已结束的保留第一次的原因。
public final class SessionTerminator {

  private final SessionStorePort sessions;
  private final UserLoginRecordRepository records;

  /// 创建领域服务。
  ///
  /// @param sessions 会话存储端口
  /// @param records 登录记录仓储
  public SessionTerminator(SessionStorePort sessions, UserLoginRecordRepository records) {
    this.sessions = Objects.requireNonNull(sessions, "sessions 不能为 null");
    this.records = Objects.requireNonNull(records, "records 不能为 null");
  }

  /// 撤销一条会话。
  ///
  /// @param userId 用户 ID
  /// @param sessionId 会话 ID
  /// @param reason 结束原因
  /// @param now 当前时间
  /// @return 会话真的被撤销时为 `true`；已经不在或不属于这个用户时为 `false`
  public boolean terminate(long userId, long sessionId, LoginEndReason reason, Instant now) {
    boolean revoked = sessions.revoke(AccountType.USER, userId, sessionId);
    if (revoked) {
      end(List.of(sessionId), reason, now);
    }
    return revoked;
  }

  /// 撤销一个用户的全部会话。
  ///
  /// @param userId 用户 ID
  /// @param reason 结束原因
  /// @param now 当前时间
  /// @return 真的被撤销的会话 ID
  public List<Long> terminateAll(long userId, LoginEndReason reason, Instant now) {
    List<Long> revoked = sessions.revokeAll(AccountType.USER, userId);
    end(revoked, reason, now);
    return revoked;
  }

  /// 撤销一个用户除指定一条之外的全部会话。
  ///
  /// @param userId 用户 ID
  /// @param keepSessionId 要保留的会话 ID
  /// @param reason 结束原因
  /// @param now 当前时间
  /// @return 真的被撤销的会话 ID
  public List<Long> terminateAllExcept(
      long userId, long keepSessionId, LoginEndReason reason, Instant now) {
    List<Long> revoked = sessions.revokeAllExcept(AccountType.USER, userId, keepSessionId);
    end(revoked, reason, now);
    return revoked;
  }

  /// 结束这些会话的登录记录：一次批量取出，逐条结束并保存。
  ///
  /// @param sessionIds 会话 ID
  /// @param reason 结束原因
  /// @param now 当前时间
  private void end(List<Long> sessionIds, LoginEndReason reason, Instant now) {
    Objects.requireNonNull(reason, "reason 不能为 null");
    if (sessionIds.isEmpty()) {
      return;
    }
    for (UserLoginRecord login : records.findAllByIds(sessionIds)) {
      login.end(reason, now);
      records.save(login);
    }
  }
}
