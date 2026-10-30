package dev.patra.notification.app.preference.usecase.change;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.patra.notification.app.preference.usecase.query.EmailPreferenceView;
import dev.patra.notification.domain.preference.exception.InvalidPreferenceRequestException;
import dev.patra.notification.domain.preference.model.aggregate.EmailPreference;
import dev.patra.notification.domain.preference.model.read.EmailMode;
import dev.patra.notification.domain.preference.model.vo.UnsubscribeToken;
import dev.patra.notification.domain.preference.port.repository.EmailPreferenceRepository;
import dev.patra.tool.core.security.AccountType;
import dev.patra.tool.core.security.AuthenticationRequiredException;
import dev.patra.tool.core.security.ClientType;
import dev.patra.tool.core.security.CurrentUser;
import dev.patra.tool.core.security.CurrentUserPort;
import dev.patra.tool.test.transaction.RecordingTransactions;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/// 改一类的档位（spec 第 12 节）：需登录；参数不合法不碰库；事务里「没有就插默认 → 锁行 → 改 → 写回」。
class ChangeEmailModeHandlerTest {

  private static final long ME = 7L;
  private static final Instant NOW = Instant.parse("2026-10-31T08:00:00Z");
  private static final UnsubscribeToken SAVED = new UnsubscribeToken("S".repeat(43));

  private final EmailPreferenceRepository preferences = mock(EmailPreferenceRepository.class);
  private final CurrentUserPort currentUser = mock(CurrentUserPort.class);
  private final List<String> events = new ArrayList<>();
  private final ChangeEmailModeHandler handler =
      new ChangeEmailModeHandler(
          preferences,
          currentUser,
          new RecordingTransactions(events),
          Clock.fixed(NOW, ZoneOffset.UTC));

  /// 未登录：未认证异常原样传播，不碰库。
  @Test
  void requires_login() {
    when(currentUser.require()).thenThrow(new AuthenticationRequiredException());

    assertThatThrownBy(() -> handler.handle(ChangeEmailModeCommand.of("REPLY", "OFF")))
        .isInstanceOf(AuthenticationRequiredException.class);
    verifyNoInteractions(preferences);
  }

  /// 类别只能是 `REPLY` / `MENTION`：「有帮助」没有邮件选项，小写与不认识的也不行。
  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"HELPFUL", "reply", "X"})
  void rejects_bad_category(String category) {
    signedIn();

    assertThatThrownBy(() -> handler.handle(ChangeEmailModeCommand.of(category, "OFF")))
        .isInstanceOf(InvalidPreferenceRequestException.class);
    verifyNoInteractions(preferences);
  }

  /// 档位只能是 `OFF` / `INSTANT` / `DIGEST`。
  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"off", "WEEKLY"})
  void rejects_bad_mode(String mode) {
    signedIn();

    assertThatThrownBy(() -> handler.handle(ChangeEmailModeCommand.of("REPLY", mode)))
        .isInstanceOf(InvalidPreferenceRequestException.class);
    verifyNoInteractions(preferences);
  }

  /// 事务里依次：插默认值（带新令牌）、锁行、写回；返回写回后的两类档位，另一类不动。
  @Test
  void inserts_default_then_locks_and_updates() {
    signedIn();
    AtomicReference<EmailPreference> inserted = new AtomicReference<>();
    doAnswer(
            invocation -> {
              events.add("insert");
              inserted.set(invocation.getArgument(0));
              return null;
            })
        .when(preferences)
        .insertIfAbsent(any(), eq(NOW));
    when(preferences.findByUserIdForUpdate(anyLong()))
        .thenAnswer(
            invocation -> {
              events.add("lock");
              return Optional.of(
                  EmailPreference.restore(ME, SAVED, EmailMode.INSTANT, EmailMode.OFF));
            });
    doAnswer(invocation -> events.add("update")).when(preferences).updateModes(any(), eq(NOW));

    EmailPreferenceView view = handler.handle(ChangeEmailModeCommand.of("REPLY", "DIGEST"));

    assertThat(view).isEqualTo(EmailPreferenceView.of(EmailMode.DIGEST, EmailMode.OFF));
    assertThat(events)
        .containsExactly(
            RecordingTransactions.BEGIN, "insert", "lock", "update", RecordingTransactions.END);
    assertThat(inserted.get().getUserId()).isEqualTo(ME);
    assertThat(inserted.get().getReply()).isEqualTo(EmailMode.INSTANT);
    assertThat(inserted.get().getMention()).isEqualTo(EmailMode.INSTANT);
  }

  /// 当前用户是 {@link #ME}。
  private void signedIn() {
    when(currentUser.require())
        .thenReturn(CurrentUser.of(ME, 1L, AccountType.USER, ClientType.WEB));
  }
}
