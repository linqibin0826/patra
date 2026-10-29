package dev.patra.interaction.app.collection.usecase.collection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.patra.interaction.app.collection.RecordingTransactions;
import dev.patra.interaction.app.collection.TestUsers;
import dev.patra.interaction.domain.collection.exception.CollectionErrorTrait;
import dev.patra.interaction.domain.collection.exception.CollectionNotFoundException;
import dev.patra.interaction.domain.collection.exception.PublishNotAllowedException;
import dev.patra.interaction.domain.collection.model.enums.CollectionVisibility;
import dev.patra.interaction.domain.collection.model.read.CollectionSummary;
import dev.patra.interaction.domain.collection.model.vo.CollectionId;
import dev.patra.interaction.domain.collection.port.repository.CollectionRepository;
import dev.patra.interaction.domain.collection.port.repository.LibraryRepository;
import dev.patra.interaction.domain.subject.exception.UserDirectoryUnavailableException;
import dev.patra.interaction.domain.subject.model.enums.AccountState;
import dev.patra.interaction.domain.subject.model.read.UserCard;
import dev.patra.interaction.domain.subject.port.user.UserDirectoryPort;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/// 设为公开：经 identity 回查，先看账号状态、再看邮箱；设为私有不回查。
class SetVisibilityHandlerTest {

  private static final long U = 1001L;
  private static final CollectionId C = CollectionId.of(500L);
  private static final Instant NOW = Instant.parse("2026-10-24T08:00:00Z");

  private final LibraryRepository library = mock(LibraryRepository.class);
  private final CollectionRepository collections = mock(CollectionRepository.class);
  private final UserDirectoryPort users = mock(UserDirectoryPort.class);
  private SetVisibilityHandler handler;

  @BeforeEach
  void setUp() {
    handler =
        new SetVisibilityHandler(
            library,
            collections,
            users,
            TestUsers.signedIn(U),
            new RecordingTransactions(new ArrayList<>()),
            Clock.fixed(NOW, ZoneOffset.UTC));
    when(collections.findOwned(U, C))
        .thenReturn(Optional.of(summary(CollectionVisibility.PRIVATE)));
    when(collections.updateVisibility(U, C, CollectionVisibility.PUBLIC, NOW))
        .thenReturn(summary(CollectionVisibility.PUBLIC));
  }

  /// 正常且已验证 → 公开。
  @Test
  void should_publish_when_active_and_verified() {
    when(users.findCards(List.of(U), U)).thenReturn(Map.of(U, card(AccountState.ACTIVE, true)));

    assertThat(handler.handle(SetVisibilityCommand.of(500L, "public")).visibility())
        .isEqualTo(CollectionVisibility.PUBLIC);
  }

  /// 非正常状态一律 ACCOUNT_RESTRICTED，即使邮箱也没验证。
  ///
  /// @param state 账号状态
  @ParameterizedTest
  @EnumSource(
      value = AccountState.class,
      names = {"BANNED", "PENDING_DELETION", "DELETED"})
  void should_reject_publish_for_restricted_accounts(AccountState state) {
    when(users.findCards(List.of(U), U)).thenReturn(Map.of(U, card(state, false)));

    assertReason(CollectionErrorTrait.ACCOUNT_RESTRICTED);
  }

  /// identity 不认识自己（省略）也算账号异常。
  @Test
  void should_reject_publish_when_identity_omits_owner() {
    when(users.findCards(List.of(U), U)).thenReturn(Map.of());

    assertReason(CollectionErrorTrait.ACCOUNT_RESTRICTED);
  }

  /// 正常但邮箱未验证。
  @Test
  void should_reject_publish_when_email_unverified() {
    when(users.findCards(List.of(U), U)).thenReturn(Map.of(U, card(AccountState.ACTIVE, false)));

    assertReason(CollectionErrorTrait.EMAIL_UNVERIFIED);
  }

  /// 公开改私有不回查 identity。
  @Test
  void should_not_call_identity_when_making_private() {
    when(collections.findOwned(U, C)).thenReturn(Optional.of(summary(CollectionVisibility.PUBLIC)));

    handler.handle(SetVisibilityCommand.of(500L, "private"));

    verifyNoInteractions(users);
    verify(collections).updateVisibility(U, C, CollectionVisibility.PRIVATE, NOW);
  }

  /// 已经公开再设公开：幂等，不回查、不写。
  @Test
  void should_skip_identity_when_already_public() {
    when(collections.findOwned(U, C)).thenReturn(Optional.of(summary(CollectionVisibility.PUBLIC)));

    assertThat(handler.handle(SetVisibilityCommand.of(500L, "public")).visibility())
        .isEqualTo(CollectionVisibility.PUBLIC);
    verifyNoInteractions(users);
    verify(collections, never()).updateVisibility(anyLong(), any(), any(), any());
  }

  /// identity 不可用原样传播（503），不写。
  @Test
  void should_propagate_identity_outage() {
    when(users.findCards(List.of(U), U))
        .thenThrow(new UserDirectoryUnavailableException(new RuntimeException()));

    assertThatThrownBy(() -> handler.handle(SetVisibilityCommand.of(500L, "public")))
        .isInstanceOf(UserDirectoryUnavailableException.class);
    verify(collections, never()).updateVisibility(anyLong(), any(), any(), any());
  }

  /// 不是自己的 404。
  @Test
  void should_404_for_someone_elses_collection() {
    when(collections.findOwned(U, C)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> handler.handle(SetVisibilityCommand.of(500L, "public")))
        .isInstanceOf(CollectionNotFoundException.class);
    verifyNoInteractions(users);
  }

  /// 断言被拒的原因码。
  ///
  /// @param reason 原因码
  private void assertReason(CollectionErrorTrait reason) {
    assertThatThrownBy(() -> handler.handle(SetVisibilityCommand.of(500L, "public")))
        .isInstanceOfSatisfying(
            PublishNotAllowedException.class,
            ex -> assertThat(ex.getErrorTraits()).contains(reason));
    verify(collections, never()).updateVisibility(anyLong(), any(), any(), any());
  }

  /// 卡片。
  ///
  /// @param state 状态
  /// @param verified 邮箱是否验证
  /// @return 卡片
  private static UserCard card(AccountState state, boolean verified) {
    return UserCard.builder().userId(U).state(state).emailVerified(verified).build();
  }

  /// 收藏夹。
  ///
  /// @param visibility 可见性
  /// @return 收藏夹
  private static CollectionSummary summary(CollectionVisibility visibility) {
    return CollectionSummary.builder()
        .collectionId(C)
        .ownerId(U)
        .name("组会")
        .visibility(visibility)
        .createdAt(NOW)
        .updatedAt(NOW)
        .build();
  }
}
