package dev.patra.notification.app.preference.usecase.unsubscribe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.patra.notification.domain.kernel.model.enums.NotificationCategory;
import dev.patra.notification.domain.preference.exception.UnsubscribeLinkInvalidException;
import dev.patra.notification.domain.preference.model.aggregate.EmailPreference;
import dev.patra.notification.domain.preference.model.enums.UnsubscribeCategory;
import dev.patra.notification.domain.preference.model.read.EmailMode;
import dev.patra.notification.domain.preference.model.vo.UnsubscribeToken;
import dev.patra.notification.domain.preference.port.repository.EmailPreferenceRepository;
import dev.patra.tool.test.transaction.RecordingTransactions;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.transaction.support.TransactionOperations;

/// 退订（落地页的确认与一键退订共用）：链接不合法不查库；锁行后设成关，有变化才写回；一键退订不收 `ALL`。
class UnsubscribeHandlerTest {

  private static final long USER = 7L;
  private static final Instant NOW = Instant.parse("2026-10-31T08:00:00Z");
  private static final String TOKEN = "T".repeat(43);

  private final EmailPreferenceRepository preferences = mock(EmailPreferenceRepository.class);
  private final UnsubscribeHandler handler =
      new UnsubscribeHandler(
          preferences,
          TransactionOperations.withoutTransaction(),
          Clock.fixed(NOW, ZoneOffset.UTC));

  /// 被截断、改写、缺参数的链接：两种入口都是链接无效，不查库。
  @ParameterizedTest
  @MethodSource("malformedLinks")
  void rejects_malformed_without_querying(String token, String category) {
    assertThatThrownBy(() -> handler.handle(UnsubscribeCommand.oneClick(token, category)))
        .isInstanceOf(UnsubscribeLinkInvalidException.class);
    assertThatThrownBy(() -> handler.handle(UnsubscribeCommand.confirm(token, category)))
        .isInstanceOf(UnsubscribeLinkInvalidException.class);
    verifyNoInteractions(preferences);
  }

  /// 一键退订的地址里不会有 `ALL`。
  @Test
  void one_click_rejects_all() {
    assertThatThrownBy(() -> handler.handle(UnsubscribeCommand.oneClick(TOKEN, "ALL")))
        .isInstanceOf(UnsubscribeLinkInvalidException.class);
    verifyNoInteractions(preferences);
  }

  /// 落地页上的「关闭全部提醒邮件」传 `ALL`。
  @Test
  void confirm_accepts_all() {
    givenLocked(EmailMode.DIGEST, EmailMode.INSTANT);

    UnsubscribeResult result = handler.handle(UnsubscribeCommand.confirm(TOKEN, "ALL"));

    assertThat(result.turnedOff())
        .containsExactly(NotificationCategory.REPLY, NotificationCategory.MENTION);
    assertThat(result.othersEnabled()).isFalse();
  }

  /// 库里没有这个令牌：链接无效。
  @Test
  void unknown_token_is_invalid() {
    when(preferences.findByTokenForUpdate(any())).thenReturn(Optional.empty());

    assertThatThrownBy(() -> handler.handle(UnsubscribeCommand.oneClick(TOKEN, "REPLY")))
        .isInstanceOf(UnsubscribeLinkInvalidException.class);
  }

  /// 设成关并写回，结果按 spec 8.3：类别、关掉了哪些、两类当前档位、其余还开着没有。
  @Test
  void applies_unsubscribe_and_writes_when_changed() {
    givenLocked(EmailMode.DIGEST, EmailMode.INSTANT);

    UnsubscribeResult result = handler.handle(UnsubscribeCommand.oneClick(" " + TOKEN, "DIGEST"));

    assertThat(result)
        .isEqualTo(
            UnsubscribeResult.builder()
                .category(UnsubscribeCategory.DIGEST)
                .turnedOff(List.of(NotificationCategory.REPLY))
                .reply(EmailMode.OFF)
                .mention(EmailMode.INSTANT)
                .othersEnabled(true)
                .build());
    verify(preferences).updateModes(any(), eq(NOW));
  }

  /// 锁行与写回在同一个事务里（行锁要在事务里才持有到写回）。
  @Test
  void locks_and_writes_inside_one_transaction() {
    List<String> events = new ArrayList<>();
    UnsubscribeHandler recorded =
        new UnsubscribeHandler(
            preferences, new RecordingTransactions(events), Clock.fixed(NOW, ZoneOffset.UTC));
    when(preferences.findByTokenForUpdate(new UnsubscribeToken(TOKEN)))
        .thenAnswer(
            invocation -> {
              events.add("lock");
              return Optional.of(
                  EmailPreference.restore(
                      USER, new UnsubscribeToken(TOKEN), EmailMode.INSTANT, EmailMode.INSTANT));
            });
    doAnswer(invocation -> events.add("update")).when(preferences).updateModes(any(), eq(NOW));

    recorded.handle(UnsubscribeCommand.oneClick(TOKEN, "REPLY"));

    assertThat(events)
        .containsExactly(RecordingTransactions.BEGIN, "lock", "update", RecordingTransactions.END);
  }

  /// 已经退订过：不写库，`turnedOff` 为空，结果照常返回。
  @Test
  void repeated_call_does_not_write_and_returns_empty_turned_off() {
    givenLocked(EmailMode.OFF, EmailMode.INSTANT);

    UnsubscribeResult result = handler.handle(UnsubscribeCommand.confirm(TOKEN, "REPLY"));

    assertThat(result.turnedOff()).isEmpty();
    assertThat(result.reply()).isEqualTo(EmailMode.OFF);
    assertThat(result.othersEnabled()).isTrue();
    verify(preferences, never()).updateModes(any(), any());
  }

  /// 命令进日志时看不到令牌。
  @Test
  void command_to_string_hides_token() {
    assertThat(UnsubscribeCommand.oneClick(TOKEN, "REPLY").toString())
        .doesNotContain(TOKEN)
        .contains("REPLY");
  }

  /// 两种入口都不认的坏链接。
  ///
  /// @return 令牌与类别
  static Stream<Arguments> malformedLinks() {
    return Stream.of(
        Arguments.of(null, "REPLY"),
        Arguments.of("abc", "REPLY"),
        Arguments.of(TOKEN.substring(1), "REPLY"),
        Arguments.of(TOKEN.substring(0, 20) + " " + TOKEN.substring(21), "REPLY"),
        Arguments.of(TOKEN, null),
        Arguments.of(TOKEN, "MEN"),
        Arguments.of(TOKEN, "mention"));
  }

  /// 按令牌锁行能读到这份偏好。
  ///
  /// @param reply 「回复我的」档位
  /// @param mention 「@ 我的」档位
  private void givenLocked(EmailMode reply, EmailMode mention) {
    when(preferences.findByTokenForUpdate(new UnsubscribeToken(TOKEN)))
        .thenReturn(
            Optional.of(
                EmailPreference.restore(USER, new UnsubscribeToken(TOKEN), reply, mention)));
  }
}
