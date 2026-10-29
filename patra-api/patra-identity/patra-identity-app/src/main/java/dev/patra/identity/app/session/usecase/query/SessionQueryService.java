package dev.patra.identity.app.session.usecase.query;

import dev.patra.identity.domain.session.model.aggregate.UserLoginRecord;
import dev.patra.identity.domain.session.model.enums.DeviceClass;
import dev.patra.identity.domain.session.model.vo.ActiveUserSession;
import dev.patra.identity.domain.session.model.vo.IpAddressScope;
import dev.patra.identity.domain.session.port.repository.UserLoginRecordRepository;
import dev.patra.identity.domain.session.port.store.SessionStorePort;
import dev.patra.tool.core.security.AccountType;
import dev.patra.tool.core.security.AuthenticationRequiredException;
import dev.patra.tool.core.security.CurrentUser;
import dev.patra.tool.core.security.CurrentUserPort;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/// 当前用户的登录设备列表（`GET /me/sessions`，PAP-99 设计第 10.2、11.1 节）。只读，不经 CommandBus，没有事务。
///
/// 列表 = Redis 里活着的会话（会话 ID、登录时间、最后活跃）⋈ 登录记录（设备名、端、IP、属地）。
/// 本机第一，其余按最后活跃倒序、相同再按 ID 倒序。当前会话已不在（断言签出后被踢、过期）时 401。
@Slf4j
@Service
@RequiredArgsConstructor
public class SessionQueryService {

  private final CurrentUserPort currentUserPort;
  private final SessionStorePort sessions;
  private final UserLoginRecordRepository records;

  /// 列出当前用户的登录设备。
  ///
  /// @return 列表
  /// @throws AuthenticationRequiredException 当前会话已不在
  public List<SessionView> list() {
    CurrentUser current = currentUserPort.require();
    List<ActiveUserSession> active = sessions.listActive(AccountType.USER, current.userId());
    if (active.stream().noneMatch(session -> session.sessionId() == current.sessionId())) {
      throw new AuthenticationRequiredException();
    }
    Map<Long, UserLoginRecord> byId =
        records.findAllByIds(active.stream().map(ActiveUserSession::sessionId).toList()).stream()
            .collect(Collectors.toMap(UserLoginRecord::getId, Function.identity()));
    Comparator<SessionView> order =
        Comparator.comparing(SessionView::current)
            .thenComparing(SessionView::lastActiveAt)
            .thenComparingLong(SessionView::id)
            .reversed();
    return active.stream()
        .map(session -> view(session, byId.get(session.sessionId()), current.sessionId()))
        .sorted(order)
        .toList();
  }

  /// 一项：会话的时间来自 Redis，设备信息来自登录记录；记录缺失时设备字段为空。
  ///
  /// @param session 活会话
  /// @param record 登录记录，可以为 `null`
  /// @param currentSessionId 本机的会话 ID
  /// @return 列表项
  private static SessionView view(
      ActiveUserSession session, UserLoginRecord record, long currentSessionId) {
    SessionView.SessionViewBuilder view =
        SessionView.builder()
            .id(session.sessionId())
            .current(session.sessionId() == currentSessionId)
            .signedInAt(session.createdAt())
            .lastActiveAt(session.lastActiveAt())
            .deviceClass(DeviceClass.UNKNOWN);
    if (record == null) {
      log.warn("会话没有对应的登录记录: sessionId={}", session.sessionId());
      return view.build();
    }
    return view.deviceName(record.getDeviceName())
        .deviceClass(record.getDeviceClass())
        .ipAddress(record.getClientIp())
        .ipRegion(record.getIpRegion())
        .privateNetwork(IpAddressScope.isPrivate(record.getClientIp()))
        .build();
  }
}
