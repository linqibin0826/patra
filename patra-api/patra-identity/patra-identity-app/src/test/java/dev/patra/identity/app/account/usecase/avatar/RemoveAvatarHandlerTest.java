package dev.patra.identity.app.account.usecase.avatar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.patra.identity.domain.account.exception.UserPendingDeletionException;
import dev.patra.identity.domain.account.model.aggregate.User;
import dev.patra.identity.domain.account.model.aggregate.UserProfile;
import dev.patra.identity.domain.account.model.enums.UserStatus;
import dev.patra.identity.domain.account.model.vo.EmailAddress;
import dev.patra.identity.domain.account.model.vo.Username;
import dev.patra.identity.domain.account.model.vo.avatar.Avatar;
import dev.patra.identity.domain.account.port.avatar.AvatarStoragePort;
import dev.patra.identity.domain.account.port.repository.UserProfileRepository;
import dev.patra.identity.domain.account.port.repository.UserRepository;
import dev.patra.tool.core.security.AccountType;
import dev.patra.tool.core.security.ClientType;
import dev.patra.tool.core.security.CurrentUser;
import dev.patra.tool.core.security.CurrentUserPort;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.transaction.support.TransactionOperations;

/// RemoveAvatarHandler 单元测试：锁内清键、提交后删对象。
class RemoveAvatarHandlerTest {

  private static final long USER_ID = 42L;
  private static final Avatar OLD = Avatar.of(USER_ID, "0123456789abcdef");

  private final CurrentUserPort currentUserPort = mock(CurrentUserPort.class);
  private final UserRepository users = mock(UserRepository.class);
  private final UserProfileRepository profiles = mock(UserProfileRepository.class);
  private final AvatarStoragePort storage = mock(AvatarStoragePort.class);
  private final RemoveAvatarHandler handler =
      new RemoveAvatarHandler(
          currentUserPort, users, profiles, storage, TransactionOperations.withoutTransaction());

  /// 默认：当前用户正常，资料里有头像。
  @BeforeEach
  void stubHappyPath() {
    when(currentUserPort.require())
        .thenReturn(CurrentUser.of(USER_ID, 7001L, AccountType.USER, ClientType.WEB));
    when(users.findByIdForUpdate(USER_ID))
        .thenAnswer(invocation -> Optional.of(user(UserStatus.ACTIVE)));
    when(profiles.findByUserId(USER_ID)).thenAnswer(invocation -> Optional.of(profileWith(OLD)));
    when(profiles.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
  }

  @Test
  void should_clear_key_then_delete_objects() {
    handler.handle(RemoveAvatarCommand.of());

    InOrder order = inOrder(profiles, storage);
    ArgumentCaptor<UserProfile> saved = ArgumentCaptor.forClass(UserProfile.class);
    order.verify(profiles).save(saved.capture());
    order.verify(storage).delete(OLD);
    assertThat(saved.getValue().getAvatar()).isEmpty();
  }

  @Test
  void should_not_delete_when_no_avatar() {
    when(profiles.findByUserId(USER_ID)).thenAnswer(invocation -> Optional.of(profileWith(null)));

    handler.handle(RemoveAvatarCommand.of());

    verifyNoInteractions(storage);
    verify(profiles, never()).save(any());
  }

  @Test
  void should_reject_pending_deletion_without_deleting() {
    when(users.findByIdForUpdate(USER_ID))
        .thenAnswer(invocation -> Optional.of(user(UserStatus.PENDING_DELETION)));

    assertThatThrownBy(() -> handler.handle(RemoveAvatarCommand.of()))
        .isInstanceOf(UserPendingDeletionException.class);

    verifyNoInteractions(storage);
  }

  /// 资料，带给定头像（可空）。
  ///
  /// @param avatar 头像
  /// @return 资料
  private static UserProfile profileWith(Avatar avatar) {
    return UserProfile.restore().id(1L).userId(USER_ID).avatar(avatar).version(0L).build();
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
