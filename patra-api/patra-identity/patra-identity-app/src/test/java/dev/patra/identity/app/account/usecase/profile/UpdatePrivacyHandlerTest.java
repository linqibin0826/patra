package dev.patra.identity.app.account.usecase.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.patra.identity.domain.account.exception.UserPendingDeletionException;
import dev.patra.identity.domain.account.model.aggregate.User;
import dev.patra.identity.domain.account.model.aggregate.UserProfile;
import dev.patra.identity.domain.account.model.enums.ProfessionalVisibility;
import dev.patra.identity.domain.account.model.enums.ProfileVisibility;
import dev.patra.identity.domain.account.model.enums.UserStatus;
import dev.patra.identity.domain.account.model.vo.EmailAddress;
import dev.patra.identity.domain.account.model.vo.Username;
import dev.patra.identity.domain.account.model.vo.profile.PrivacySettings;
import dev.patra.identity.domain.account.policy.DisplayNamePolicy;
import dev.patra.identity.domain.account.policy.ProfilePolicy;
import dev.patra.identity.domain.account.port.repository.UserProfileRepository;
import dev.patra.identity.domain.account.port.repository.UserRepository;
import dev.patra.identity.domain.session.exception.InvalidUserFieldsException;
import dev.patra.tool.core.security.AccountType;
import dev.patra.tool.core.security.ClientType;
import dev.patra.tool.core.security.CurrentUser;
import dev.patra.tool.core.security.CurrentUserPort;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionOperations;

/// UpdatePrivacyHandler 单元测试：解析、保存、状态守卫。
class UpdatePrivacyHandlerTest {

  private static final long USER_ID = 42L;

  private final CurrentUserPort currentUserPort = mock(CurrentUserPort.class);
  private final UserRepository users = mock(UserRepository.class);
  private final UserProfileRepository profiles = mock(UserProfileRepository.class);
  private final UpdatePrivacyHandler handler =
      new UpdatePrivacyHandler(
          currentUserPort,
          users,
          profiles,
          new ProfilePolicy(new DisplayNamePolicy()),
          TransactionOperations.withoutTransaction());

  /// 默认：当前用户正常，资料为默认隐私；每次读都给新实例。
  @BeforeEach
  void stubHappyPath() {
    when(currentUserPort.require())
        .thenReturn(CurrentUser.of(USER_ID, 7001L, AccountType.USER, ClientType.WEB));
    when(users.findByIdForUpdate(USER_ID))
        .thenAnswer(invocation -> Optional.of(user(UserStatus.ACTIVE)));
    when(profiles.findByUserId(USER_ID))
        .thenAnswer(
            invocation ->
                Optional.of(UserProfile.restore().id(1L).userId(USER_ID).version(0L).build()));
    when(profiles.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
  }

  @Test
  void should_save_parsed_settings() {
    PrivacySettings result = handler.handle(UpdatePrivacyCommand.of("SIGNED_IN", "SELF", true));

    PrivacySettings expected =
        PrivacySettings.of(ProfileVisibility.SIGNED_IN, ProfessionalVisibility.SELF, true);
    assertThat(result).isEqualTo(expected);
    ArgumentCaptor<UserProfile> saved = ArgumentCaptor.forClass(UserProfile.class);
    verify(profiles).save(saved.capture());
    assertThat(saved.getValue().getPrivacy()).isEqualTo(expected);
  }

  @Test
  void should_report_missing_and_unknown_values_together() {
    assertThatThrownBy(() -> handler.handle(UpdatePrivacyCommand.of("PRIVATE", null, null)))
        .isInstanceOf(InvalidUserFieldsException.class);

    verifyNoInteractions(users, profiles);
  }

  @Test
  void should_reject_pending_deletion() {
    when(users.findByIdForUpdate(USER_ID))
        .thenAnswer(invocation -> Optional.of(user(UserStatus.PENDING_DELETION)));

    assertThatThrownBy(() -> handler.handle(UpdatePrivacyCommand.of("PUBLIC", "PUBLIC", false)))
        .isInstanceOf(UserPendingDeletionException.class);

    verify(profiles, never()).save(any());
  }

  /// 给定状态的用户。
  ///
  /// @param status 状态
  /// @return 用户
  private static User user(UserStatus status) {
    return User.restore()
        .id(USER_ID)
        .email(EmailAddress.of("chen.yu@example.com"))
        .username(Username.of("chen-yu"))
        .status(status)
        .deletionDueAt(
            status == UserStatus.PENDING_DELETION ? Instant.parse("2026-11-01T00:00:00Z") : null)
        .version(0L)
        .build();
  }
}
