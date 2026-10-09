package dev.linqibin.patra.identity.app.usecase.logout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.linqibin.patra.common.security.AccountType;
import dev.linqibin.patra.common.security.ClientType;
import dev.linqibin.patra.common.security.CurrentUser;
import dev.linqibin.patra.common.security.CurrentUserPort;
import dev.linqibin.patra.identity.domain.model.aggregate.UserLoginRecord;
import dev.linqibin.patra.identity.domain.model.enums.LoginEndReason;
import dev.linqibin.patra.identity.domain.port.repository.UserLoginRecordRepository;
import dev.linqibin.patra.identity.domain.port.session.SessionStorePort;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.support.TransactionOperations;

/// LogoutUserHandler 单元测试。
@DisplayName("LogoutUserHandler 单元测试")
class LogoutUserHandlerTest {

  private static final Instant NOW = Instant.parse("2026-10-09T08:00:00Z");
  private static final CurrentUser USER =
      CurrentUser.of(42L, 7001L, AccountType.USER, ClientType.WEB);

  private final CurrentUserPort currentUserPort = mock(CurrentUserPort.class);
  private final SessionStorePort sessions = mock(SessionStorePort.class);
  private final UserLoginRecordRepository records = mock(UserLoginRecordRepository.class);
  private final LogoutUserHandler handler =
      new LogoutUserHandler(
          currentUserPort,
          sessions,
          records,
          Clock.fixed(NOW, ZoneOffset.UTC),
          TransactionOperations.withoutTransaction());

  @Test
  @DisplayName("没有当前用户：什么都不做，正常返回")
  void should_do_nothing_without_current_user() {
    when(currentUserPort.current()).thenReturn(Optional.empty());

    handler.handle(LogoutUserCommand.of());

    verifyNoInteractions(sessions, records);
  }

  @Test
  @DisplayName("有当前用户：先删会话，再把登录记录标成 LOGOUT")
  void should_revoke_session_and_end_record() {
    when(currentUserPort.current()).thenReturn(Optional.of(USER));
    when(sessions.revoke(AccountType.USER, 42L, 7001L)).thenReturn(true);
    UserLoginRecord login =
        UserLoginRecord.restore(
            7001L, 42L, ClientType.WEB, null, NOW.plusSeconds(1), null, null, 0L, NOW, NOW);
    when(records.findById(7001L)).thenReturn(Optional.of(login));

    handler.handle(LogoutUserCommand.of());

    assertThat(login.getEndReason()).isEqualTo(LoginEndReason.LOGOUT);
    assertThat(login.getEndedAt()).isEqualTo(NOW);
    verify(records).save(login);
  }

  @Test
  @DisplayName("会话已经没了（过期、封禁、被挤掉）：不动记录")
  void should_leave_record_when_session_is_already_gone() {
    when(currentUserPort.current()).thenReturn(Optional.of(USER));
    when(sessions.revoke(AccountType.USER, 42L, 7001L)).thenReturn(false);

    handler.handle(LogoutUserCommand.of());

    verifyNoInteractions(records);
  }

  @Test
  @DisplayName("会话删了但记录找不到：记日志放过，不报错")
  void should_tolerate_missing_record() {
    when(currentUserPort.current()).thenReturn(Optional.of(USER));
    when(sessions.revoke(AccountType.USER, 42L, 7001L)).thenReturn(true);
    when(records.findById(7001L)).thenReturn(Optional.empty());

    handler.handle(LogoutUserCommand.of());

    verify(records, never()).save(any());
  }

  @Test
  @DisplayName("会话删掉了但结束记录时数据库出错：只记日志，仍正常返回（用户已经登出）")
  void should_return_normally_when_ending_record_fails() {
    when(currentUserPort.current()).thenReturn(Optional.of(USER));
    when(sessions.revoke(AccountType.USER, 42L, 7001L)).thenReturn(true);
    UserLoginRecord login =
        UserLoginRecord.restore(
            7001L, 42L, ClientType.WEB, null, NOW.plusSeconds(1), null, null, 0L, NOW, NOW);
    when(records.findById(7001L)).thenReturn(Optional.of(login));
    when(records.save(any())).thenThrow(new DataAccessResourceFailureException("db down"));

    assertThatCode(() -> handler.handle(LogoutUserCommand.of())).doesNotThrowAnyException();
    verify(sessions).revoke(AccountType.USER, 42L, 7001L);
  }
}
