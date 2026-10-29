package dev.patra.identity.app.account.usecase.avatar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.patra.identity.domain.account.exception.AvatarStorageException;
import dev.patra.identity.domain.account.exception.InvalidAvatarException;
import dev.patra.identity.domain.account.exception.UserPendingDeletionException;
import dev.patra.identity.domain.account.model.aggregate.User;
import dev.patra.identity.domain.account.model.aggregate.UserProfile;
import dev.patra.identity.domain.account.model.enums.UserStatus;
import dev.patra.identity.domain.account.model.vo.EmailAddress;
import dev.patra.identity.domain.account.model.vo.Username;
import dev.patra.identity.domain.account.model.vo.avatar.Avatar;
import dev.patra.identity.domain.account.model.vo.avatar.AvatarRejection;
import dev.patra.identity.domain.account.model.vo.avatar.AvatarRenditions;
import dev.patra.identity.domain.account.port.avatar.AvatarImagePort;
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
import org.mockito.InOrder;
import org.springframework.transaction.support.TransactionOperations;

/// ChangeAvatarHandler 单元测试：事务外出图写对象、锁内换键、提交后删旧对象。
class ChangeAvatarHandlerTest {

  private static final long USER_ID = 42L;
  private static final byte[] UPLOAD = {9, 9, 9};
  private static final AvatarRenditions RENDITIONS =
      AvatarRenditions.of(new byte[] {1, 2, 3}, new byte[] {4});
  private static final Avatar NEW = Avatar.forContent(USER_ID, new byte[] {1, 2, 3});
  private static final Avatar OLD = Avatar.of(USER_ID, "0123456789abcdef");

  private final CurrentUserPort currentUserPort = mock(CurrentUserPort.class);
  private final UserRepository users = mock(UserRepository.class);
  private final UserProfileRepository profiles = mock(UserProfileRepository.class);
  private final AvatarImagePort images = mock(AvatarImagePort.class);
  private final AvatarStoragePort storage = mock(AvatarStoragePort.class);
  private final ChangeAvatarHandler handler =
      new ChangeAvatarHandler(
          currentUserPort,
          users,
          profiles,
          images,
          storage,
          TransactionOperations.withoutTransaction());

  /// 默认：当前用户正常，资料里已有旧头像；每次读都给新实例。
  @BeforeEach
  void stubHappyPath() {
    when(currentUserPort.require())
        .thenReturn(CurrentUser.of(USER_ID, 7001L, AccountType.USER, ClientType.WEB));
    when(users.findById(USER_ID)).thenAnswer(invocation -> Optional.of(user(UserStatus.ACTIVE)));
    when(users.findByIdForUpdate(USER_ID))
        .thenAnswer(invocation -> Optional.of(user(UserStatus.ACTIVE)));
    when(profiles.findByUserId(USER_ID)).thenAnswer(invocation -> Optional.of(profileWith(OLD)));
    when(profiles.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(images.render(UPLOAD)).thenReturn(RENDITIONS);
  }

  @Test
  void should_store_then_save_then_delete_previous() {
    Avatar result = handler.handle(ChangeAvatarCommand.of(UPLOAD));

    assertThat(result).isEqualTo(NEW);
    InOrder order = inOrder(storage, profiles);
    order.verify(storage).store(NEW, RENDITIONS);
    order.verify(profiles).save(any());
    order.verify(storage).delete(OLD);
  }

  @Test
  void should_reject_pending_deletion_before_processing() {
    when(users.findById(USER_ID))
        .thenAnswer(invocation -> Optional.of(user(UserStatus.PENDING_DELETION)));

    assertThatThrownBy(() -> handler.handle(ChangeAvatarCommand.of(UPLOAD)))
        .isInstanceOf(UserPendingDeletionException.class);

    verifyNoInteractions(images, storage);
  }

  @Test
  void should_reject_empty_content_as_required() {
    assertThatThrownBy(() -> handler.handle(ChangeAvatarCommand.of(new byte[0])))
        .isInstanceOf(InvalidAvatarException.class)
        .extracting(e -> ((InvalidAvatarException) e).getRejection())
        .isEqualTo(AvatarRejection.REQUIRED);

    verifyNoInteractions(images, storage);
  }

  @Test
  void should_short_circuit_when_same_image_uploaded_again() {
    when(profiles.findByUserId(USER_ID)).thenAnswer(invocation -> Optional.of(profileWith(NEW)));

    Avatar result = handler.handle(ChangeAvatarCommand.of(UPLOAD));

    assertThat(result).isEqualTo(NEW);
    verify(storage, never()).store(any(), any());
    verify(storage, never()).delete(any());
    verify(profiles, never()).save(any());
  }

  @Test
  void should_delete_new_objects_when_transaction_fails() {
    when(users.findByIdForUpdate(USER_ID))
        .thenAnswer(invocation -> Optional.of(user(UserStatus.PENDING_DELETION)));

    assertThatThrownBy(() -> handler.handle(ChangeAvatarCommand.of(UPLOAD)))
        .isInstanceOf(UserPendingDeletionException.class);

    verify(storage).delete(NEW);
    verify(storage, never()).delete(OLD);
  }

  @Test
  void should_not_touch_database_when_storing_fails() {
    doThrow(new AvatarStorageException("boom", new RuntimeException()))
        .when(storage)
        .store(NEW, RENDITIONS);

    assertThatThrownBy(() -> handler.handle(ChangeAvatarCommand.of(UPLOAD)))
        .isInstanceOf(AvatarStorageException.class);

    verify(profiles, never()).save(any());
  }

  @Test
  void should_still_return_when_deleting_previous_fails() {
    doThrow(new AvatarStorageException("boom", new RuntimeException())).when(storage).delete(OLD);

    Avatar result = handler.handle(ChangeAvatarCommand.of(UPLOAD));

    assertThat(result).isEqualTo(NEW);
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
