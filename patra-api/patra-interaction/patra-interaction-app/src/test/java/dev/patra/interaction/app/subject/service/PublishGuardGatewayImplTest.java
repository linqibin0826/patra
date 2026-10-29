package dev.patra.interaction.app.subject.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.patra.interaction.domain.subject.exception.PublishIneligibleException;
import dev.patra.interaction.domain.subject.exception.PublishRateLimitedException;
import dev.patra.interaction.domain.subject.exception.SubjectErrorTrait;
import dev.patra.interaction.domain.subject.exception.UserDirectoryUnavailableException;
import dev.patra.interaction.domain.subject.model.enums.AccountState;
import dev.patra.interaction.domain.subject.model.enums.PublishKind;
import dev.patra.interaction.domain.subject.model.read.UserCard;
import dev.patra.interaction.domain.subject.policy.PublishQuota;
import dev.patra.interaction.domain.subject.port.repository.PublishQuotaRepository;
import dev.patra.interaction.domain.subject.port.user.UserDirectoryPort;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InOrder;

/// 发表守卫：资格先看账号状态再看邮箱；频率在作者锁里读、判、写，被拒时不写。
class PublishGuardGatewayImplTest {

  private static final long U = 7L;

  /// 北京时间 2026-10-27 16:00。
  private static final Instant NOW = Instant.parse("2026-10-27T08:00:00Z");

  private final UserDirectoryPort users = mock(UserDirectoryPort.class);
  private final PublishQuotaRepository quotas = mock(PublishQuotaRepository.class);
  private final PublishGuardGatewayImpl guard =
      new PublishGuardGatewayImpl(users, quotas, Clock.fixed(NOW, ZoneOffset.UTC));

  @Test
  void should_return_own_card_when_eligible() {
    UserCard self = card(AccountState.ACTIVE, true);
    when(users.findCards(List.of(U), U)).thenReturn(Map.of(U, self));

    assertThat(guard.requireEligible(U)).isEqualTo(self);
  }

  /// 封禁、冷静期、已注销都是账号状态异常，哪怕邮箱已验证。
  ///
  /// @param state 账号状态
  @ParameterizedTest
  @EnumSource(
      value = AccountState.class,
      names = {"BANNED", "PENDING_DELETION", "DELETED"})
  void should_reject_restricted_account(AccountState state) {
    when(users.findCards(List.of(U), U)).thenReturn(Map.of(U, card(state, true)));

    assertRejectedWith(SubjectErrorTrait.ACCOUNT_RESTRICTED);
  }

  /// 被封禁又没验证邮箱：先报账号状态异常，而不是让他去验证邮箱。
  @Test
  void should_report_account_restriction_before_unverified_email() {
    when(users.findCards(List.of(U), U)).thenReturn(Map.of(U, card(AccountState.BANNED, false)));

    assertRejectedWith(SubjectErrorTrait.ACCOUNT_RESTRICTED);
  }

  /// identity 不认识这个人：按账号状态异常处理。
  @Test
  void should_reject_when_identity_omits_user() {
    when(users.findCards(List.of(U), U)).thenReturn(Map.of());

    assertRejectedWith(SubjectErrorTrait.ACCOUNT_RESTRICTED);
  }

  @Test
  void should_reject_unverified_email() {
    when(users.findCards(List.of(U), U)).thenReturn(Map.of(U, card(AccountState.ACTIVE, false)));

    assertRejectedWith(SubjectErrorTrait.EMAIL_UNVERIFIED);
  }

  @Test
  void should_propagate_directory_unavailable() {
    UserDirectoryUnavailableException down =
        new UserDirectoryUnavailableException(new RuntimeException("down"));
    when(users.findCards(List.of(U), U)).thenThrow(down);

    assertThatThrownBy(() -> guard.requireEligible(U)).isSameAs(down);
  }

  /// 先拿作者锁，再读计数、判规则、写回。
  @Test
  void should_lock_then_read_then_save() {
    when(quotas.find(U, PublishKind.COMMENT)).thenReturn(Optional.empty());

    guard.admit(U, PublishKind.COMMENT);

    InOrder order = inOrder(quotas);
    order.verify(quotas).lock(U);
    order.verify(quotas).find(U, PublishKind.COMMENT);
    order
        .verify(quotas)
        .save(U, PublishKind.COMMENT, new PublishQuota(LocalDate.of(2026, 10, 27), 1, NOW));
  }

  /// 被拒时什么都不写。
  @Test
  void should_not_save_when_rejected() {
    when(quotas.find(U, PublishKind.COMMENT))
        .thenReturn(
            Optional.of(new PublishQuota(LocalDate.of(2026, 10, 27), 100, NOW.minusSeconds(3600))));

    assertThatThrownBy(() -> guard.admit(U, PublishKind.COMMENT))
        .isInstanceOfSatisfying(
            PublishRateLimitedException.class,
            ex -> assertThat(ex.getErrorTraits()).contains(SubjectErrorTrait.DAILY_LIMIT_REACHED));
    verify(quotas, never()).save(anyLong(), any(), any());
  }

  /// 断言资格被拒，并带指定原因码。
  ///
  /// @param reason 原因码
  private void assertRejectedWith(SubjectErrorTrait reason) {
    assertThatThrownBy(() -> guard.requireEligible(U))
        .isInstanceOfSatisfying(
            PublishIneligibleException.class,
            ex -> assertThat(ex.getErrorTraits()).contains(reason));
  }

  /// 自己的名片。
  ///
  /// @param state 账号状态
  /// @param emailVerified 邮箱是否已验证
  /// @return 名片
  private static UserCard card(AccountState state, boolean emailVerified) {
    return UserCard.builder()
        .userId(U)
        .username("chen-yu")
        .displayName("陈宇")
        .avatarPalette(3)
        .state(state)
        .emailVerified(emailVerified)
        .profileVisible(true)
        .build();
  }
}
