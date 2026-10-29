package dev.patra.identity.app.session.usecase.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.patra.identity.domain.session.model.aggregate.UserLoginRecord;
import dev.patra.identity.domain.session.model.enums.DeviceClass;
import dev.patra.identity.domain.session.model.vo.ActiveUserSession;
import dev.patra.identity.domain.session.port.repository.UserLoginRecordRepository;
import dev.patra.identity.domain.session.port.store.SessionStorePort;
import dev.patra.tool.core.security.AccountType;
import dev.patra.tool.core.security.AuthenticationRequiredException;
import dev.patra.tool.core.security.ClientType;
import dev.patra.tool.core.security.CurrentUser;
import dev.patra.tool.core.security.CurrentUserPort;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// SessionQueryService 单元测试：活会话 ⋈ 登录记录，本机第一（PAP-99 设计第 10.2、11.1 节）。
@DisplayName("SessionQueryService 单元测试")
class SessionQueryServiceTest {

  static final Instant T0 = Instant.parse("2026-10-20T01:12:00Z");
  static final CurrentUser CURRENT = CurrentUser.of(42L, 7002L, AccountType.USER, ClientType.WEB);

  private final CurrentUserPort currentUserPort = mock(CurrentUserPort.class);
  private final SessionStorePort sessions = mock(SessionStorePort.class);
  private final UserLoginRecordRepository records = mock(UserLoginRecordRepository.class);
  private final SessionQueryService service =
      new SessionQueryService(currentUserPort, sessions, records);

  @BeforeEach
  void stubCurrentUser() {
    when(currentUserPort.require()).thenReturn(CURRENT);
  }

  @Test
  @DisplayName("本机第一；其余按最后活跃倒序，相同再按 ID 倒序；设备信息来自登录记录")
  void should_put_current_first_then_latest_active() {
    when(sessions.listActive(AccountType.USER, 42L))
        .thenReturn(
            List.of(
                ActiveUserSession.of(7001L, T0, T0.plusSeconds(600)),
                ActiveUserSession.of(7002L, T0, T0.plusSeconds(60)),
                ActiveUserSession.of(7003L, T0, T0.plusSeconds(600)),
                ActiveUserSession.of(7004L, T0, T0.plusSeconds(900))));
    when(records.findAllByIds(any()))
        .thenReturn(
            List.of(
                record(7001L, "Safari 18 · iOS", DeviceClass.MOBILE, "119.29.29.29", "中国 北京"),
                record(
                    7002L, "Chrome 141 · macOS", DeviceClass.DESKTOP, "113.92.157.29", "中国 广东 深圳"),
                record(7003L, null, DeviceClass.UNKNOWN, null, null),
                record(7004L, "Edge 141 · Windows", DeviceClass.DESKTOP, "100.70.1.1", null)));

    List<SessionView> listed = service.list();

    assertThat(listed).extracting(SessionView::id).containsExactly(7002L, 7004L, 7003L, 7001L);
    SessionView current = listed.getFirst();
    assertThat(current.current()).isTrue();
    assertThat(current.deviceName()).isEqualTo("Chrome 141 · macOS");
    assertThat(current.deviceClass()).isEqualTo(DeviceClass.DESKTOP);
    assertThat(current.ipAddress()).isEqualTo("113.92.157.29");
    assertThat(current.ipRegion()).isEqualTo("中国 广东 深圳");
    assertThat(current.privateNetwork()).isFalse();
    assertThat(current.signedInAt()).isEqualTo(T0);
    assertThat(current.lastActiveAt()).isEqualTo(T0.plusSeconds(60));
    assertThat(listed.get(1).current()).isFalse();
  }

  @Test
  @DisplayName("内网地址：privateNetwork 为真")
  void should_flag_private_network_from_stored_ip() {
    when(sessions.listActive(AccountType.USER, 42L))
        .thenReturn(List.of(ActiveUserSession.of(7002L, T0, T0)));
    when(records.findAllByIds(any()))
        .thenReturn(
            List.of(record(7002L, "Chrome 141 · macOS", DeviceClass.DESKTOP, "100.70.1.1", null)));

    assertThat(service.list().getFirst().privateNetwork()).isTrue();
  }

  @Test
  @DisplayName("会话在、记录不在：照样列出，设备字段为空、端未知")
  void should_list_session_without_record_with_empty_device_fields() {
    when(sessions.listActive(AccountType.USER, 42L))
        .thenReturn(
            List.of(ActiveUserSession.of(7002L, T0, T0), ActiveUserSession.of(7009L, T0, T0)));
    when(records.findAllByIds(any()))
        .thenReturn(List.of(record(7002L, "Chrome 141 · macOS", DeviceClass.DESKTOP, null, null)));

    SessionView orphan = service.list().get(1);

    assertThat(orphan.id()).isEqualTo(7009L);
    assertThat(orphan.deviceName()).isNull();
    assertThat(orphan.deviceClass()).isEqualTo(DeviceClass.UNKNOWN);
    assertThat(orphan.ipAddress()).isNull();
    assertThat(orphan.privateNetwork()).isFalse();
  }

  @Test
  @DisplayName("当前会话已不在：401")
  void should_reject_when_current_session_is_gone() {
    when(sessions.listActive(AccountType.USER, 42L))
        .thenReturn(List.of(ActiveUserSession.of(7001L, T0, T0)));

    assertThatThrownBy(service::list).isInstanceOf(AuthenticationRequiredException.class);
  }

  /// 一条登录记录。
  ///
  /// @param id 记录 ID
  /// @param deviceName 设备名
  /// @param deviceClass 端
  /// @param ip IP
  /// @param region 属地
  /// @return 记录
  static UserLoginRecord record(
      long id, String deviceName, DeviceClass deviceClass, String ip, String region) {
    return UserLoginRecord.restore()
        .id(id)
        .userId(42L)
        .clientType(ClientType.WEB)
        .deviceName(deviceName)
        .deviceClass(deviceClass)
        .clientIp(ip)
        .ipRegion(region)
        .expiresAt(T0.plusSeconds(86_400))
        .version(0L)
        .createdAt(T0)
        .updatedAt(T0)
        .build();
  }
}
