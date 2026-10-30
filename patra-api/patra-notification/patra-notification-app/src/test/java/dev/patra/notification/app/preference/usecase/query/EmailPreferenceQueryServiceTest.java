package dev.patra.notification.app.preference.usecase.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import dev.patra.notification.domain.kernel.exception.IdentityUnavailableException;
import dev.patra.notification.domain.kernel.model.enums.AccountState;
import dev.patra.notification.domain.kernel.model.read.Recipient;
import dev.patra.notification.domain.kernel.port.identity.RecipientPort;
import dev.patra.notification.domain.preference.exception.UnsubscribeLinkInvalidException;
import dev.patra.notification.domain.preference.model.aggregate.EmailPreference;
import dev.patra.notification.domain.preference.model.enums.UnsubscribeCategory;
import dev.patra.notification.domain.preference.model.read.EmailMode;
import dev.patra.notification.domain.preference.model.vo.UnsubscribeToken;
import dev.patra.notification.domain.preference.port.repository.EmailPreferenceRepository;
import dev.patra.tool.core.security.AccountType;
import dev.patra.tool.core.security.AuthenticationRequiredException;
import dev.patra.tool.core.security.ClientType;
import dev.patra.tool.core.security.CurrentUser;
import dev.patra.tool.core.security.CurrentUserPort;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/// 偏好的读端：本人偏好（需登录、没有行给默认值）；退订落地页的读（只读、链接无效不区分原因、回查 identity）。
class EmailPreferenceQueryServiceTest {

  private static final long ME = 7L;
  private static final String TOKEN = "T".repeat(43);

  private final EmailPreferenceRepository preferences = mock(EmailPreferenceRepository.class);
  private final RecipientPort recipients = mock(RecipientPort.class);
  private final CurrentUserPort currentUser = mock(CurrentUserPort.class);
  private final EmailPreferenceQueryService query =
      new EmailPreferenceQueryService(preferences, recipients, currentUser);

  /// 未登录读本人偏好：未认证异常原样传播。
  @Test
  void mine_requires_login() {
    when(currentUser.require()).thenThrow(new AuthenticationRequiredException());

    assertThatThrownBy(query::mine).isInstanceOf(AuthenticationRequiredException.class);
    verifyNoInteractions(preferences);
  }

  /// 没有偏好行：两类都是默认的离站逐条。
  @Test
  void mine_returns_defaults_when_no_row() {
    signedIn();
    when(preferences.findByUserId(ME)).thenReturn(Optional.empty());

    assertThat(query.mine())
        .isEqualTo(EmailPreferenceView.of(EmailMode.INSTANT, EmailMode.INSTANT));
  }

  /// 有偏好行：原样返回两类档位。
  @Test
  void mine_returns_saved_modes() {
    signedIn();
    when(preferences.findByUserId(ME))
        .thenReturn(Optional.of(preference(EmailMode.DIGEST, EmailMode.OFF)));

    assertThat(query.mine()).isEqualTo(EmailPreferenceView.of(EmailMode.DIGEST, EmailMode.OFF));
  }

  /// 链接被截断、改写、缺参数，或类别是落地页才有的 `ALL`：都是链接无效，而且不查库、不调 identity。
  @ParameterizedTest
  @MethodSource("malformedLinks")
  void inspect_rejects_malformed_link_without_querying(String token, String category) {
    assertThatThrownBy(() -> query.inspect(token, category))
        .isInstanceOf(UnsubscribeLinkInvalidException.class);
    verifyNoInteractions(preferences, recipients);
  }

  /// 库里没有这个令牌：链接无效，不调 identity。
  @Test
  void inspect_rejects_unknown_token() {
    when(preferences.findByToken(new UnsubscribeToken(TOKEN))).thenReturn(Optional.empty());

    assertThatThrownBy(() -> query.inspect(TOKEN, "REPLY"))
        .isInstanceOf(UnsubscribeLinkInvalidException.class);
    verifyNoInteractions(recipients);
  }

  /// identity 不认识这个人、账号已注销、邮箱为空：都是链接无效。
  @ParameterizedTest
  @ValueSource(strings = {"missing", "deleted", "no-email"})
  void should_reject_link_when_recipient_missing_or_deleted(String situation) {
    givenToken(EmailMode.INSTANT, EmailMode.INSTANT);
    Optional<Recipient> recipient =
        switch (situation) {
          case "missing" -> Optional.empty();
          case "deleted" -> Optional.of(recipient(null, AccountState.DELETED));
          default -> Optional.of(recipient(null, AccountState.ACTIVE));
        };
    when(recipients.find(ME)).thenReturn(recipient);

    assertThatThrownBy(() -> query.inspect(TOKEN, "REPLY"))
        .isInstanceOf(UnsubscribeLinkInvalidException.class);
  }

  /// identity 不可用：503 原样传播，落地页提示稍后再试。
  @Test
  void inspect_propagates_identity_unavailable() {
    givenToken(EmailMode.INSTANT, EmailMode.INSTANT);
    when(recipients.find(anyLong()))
        .thenThrow(new IdentityUnavailableException(new IllegalStateException("down")));

    assertThatThrownBy(() -> query.inspect(TOKEN, "REPLY"))
        .isInstanceOf(IdentityUnavailableException.class);
  }

  /// 有效链接：脱敏邮箱、原样回显类别、两类档位、两个判断（spec 5.3）。
  @Test
  void inspect_returns_masked_email_and_flags() {
    givenToken(EmailMode.INSTANT, EmailMode.OFF);
    when(recipients.find(ME))
        .thenReturn(Optional.of(recipient("chen.yu@example.com", AccountState.ACTIVE)));

    assertThat(query.inspect(" " + TOKEN + " ", "MENTION"))
        .isEqualTo(
            UnsubscribeLinkView.builder()
                .maskedEmail("c***@example.com")
                .category(UnsubscribeCategory.MENTION)
                .reply(EmailMode.INSTANT)
                .mention(EmailMode.OFF)
                .alreadyUnsubscribed(true)
                .othersEnabled(true)
                .build());
  }

  /// 封禁、注销冷静期的账号链接照常有效。
  @ParameterizedTest
  @ValueSource(strings = {"BANNED", "PENDING_DELETION"})
  void inspect_accepts_banned_and_pending_deletion(String state) {
    givenToken(EmailMode.DIGEST, EmailMode.INSTANT);
    when(recipients.find(ME))
        .thenReturn(Optional.of(recipient("chen.yu@example.com", AccountState.valueOf(state))));

    UnsubscribeLinkView view = query.inspect(TOKEN, "DIGEST");

    assertThat(view.alreadyUnsubscribed()).isFalse();
    assertThat(view.othersEnabled()).isTrue();
  }

  /// 打开落地页不改任何东西：仓储只被按令牌读过一次。
  @Test
  void inspect_does_not_write() {
    givenToken(EmailMode.INSTANT, EmailMode.INSTANT);
    when(recipients.find(ME))
        .thenReturn(Optional.of(recipient("chen.yu@example.com", AccountState.ACTIVE)));

    query.inspect(TOKEN, "REPLY");

    verify(preferences).findByToken(new UnsubscribeToken(TOKEN));
    verifyNoMoreInteractions(preferences);
  }

  /// 读接口遇到的坏链接。
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
        Arguments.of(TOKEN, "mention"),
        Arguments.of(TOKEN, "ALL"));
  }

  /// 当前用户是 {@link #ME}。
  private void signedIn() {
    when(currentUser.require())
        .thenReturn(CurrentUser.of(ME, 1L, AccountType.USER, ClientType.WEB));
  }

  /// 库里有 {@link #TOKEN} 对应的偏好行。
  ///
  /// @param reply 「回复我的」档位
  /// @param mention 「@ 我的」档位
  private void givenToken(EmailMode reply, EmailMode mention) {
    when(preferences.findByToken(new UnsubscribeToken(TOKEN)))
        .thenReturn(Optional.of(preference(reply, mention)));
  }

  /// 一份偏好。
  ///
  /// @param reply 「回复我的」档位
  /// @param mention 「@ 我的」档位
  /// @return 偏好
  private static EmailPreference preference(EmailMode reply, EmailMode mention) {
    return EmailPreference.restore(ME, new UnsubscribeToken(TOKEN), reply, mention);
  }

  /// 一份收件信息。
  ///
  /// @param email 邮箱，可空
  /// @param state 账号状态
  /// @return 收件信息
  private static Recipient recipient(String email, AccountState state) {
    return Recipient.builder()
        .userId(ME)
        .email(email)
        .emailVerified(email != null)
        .state(state)
        .username(email == null ? null : "chen-yu")
        .displayName(email == null ? null : "陈宇")
        .build();
  }
}
