package dev.patra.interaction.app.collection.service;

import static dev.patra.interaction.app.collection.Fixtures.card;
import static dev.patra.interaction.app.collection.Fixtures.collection;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.patra.interaction.domain.collection.exception.CollectionNotFoundException;
import dev.patra.interaction.domain.collection.model.enums.CollectionVisibility;
import dev.patra.interaction.domain.collection.model.vo.CollectionId;
import dev.patra.interaction.domain.collection.port.read.LibraryReadPort;
import dev.patra.interaction.domain.subject.exception.UserDirectoryUnavailableException;
import dev.patra.interaction.domain.subject.model.enums.AccountState;
import dev.patra.interaction.domain.subject.port.user.UserDirectoryPort;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/// 公开收藏夹的可见性：本人总能看；别人只能看公开的、且 identity 判定所有者主页对他可见。
class CollectionAccessTest {

  private static final long OWNER = 7L;
  private static final long VISITOR = 9L;
  private static final CollectionId C = CollectionId.of(500L);

  private final LibraryReadPort reads = mock(LibraryReadPort.class);
  private final UserDirectoryPort users = mock(UserDirectoryPort.class);
  private final CollectionAccess access = new CollectionAccess(reads, users);

  /// 本人能看自己的私有夹。
  @Test
  void should_let_owner_read_private() {
    stubCollection(CollectionVisibility.PRIVATE);
    when(users.findCards(List.of(OWNER), OWNER))
        .thenReturn(Map.of(OWNER, card(OWNER, AccountState.ACTIVE, true)));

    ReadableCollection readable = access.open(C, Optional.of(OWNER));

    assertThat(readable.viewerIsOwner()).isTrue();
    assertThat(readable.owner().username()).isEqualTo("u7");
  }

  /// 本人看自己的夹不依赖 identity：identity 不可用时照样可读，只是没有所有者摘要。
  @Test
  void should_let_owner_read_without_identity() {
    stubCollection(CollectionVisibility.PRIVATE);
    when(users.findCards(List.of(OWNER), OWNER))
        .thenThrow(new UserDirectoryUnavailableException(new RuntimeException()));

    ReadableCollection readable = access.open(C, Optional.of(OWNER));

    assertThat(readable.viewerIsOwner()).isTrue();
    assertThat(readable.owner()).isNull();
  }

  /// 别人看私有夹 404。
  @Test
  void should_404_private_for_others() {
    stubCollection(CollectionVisibility.PRIVATE);
    when(users.findCards(List.of(OWNER), VISITOR))
        .thenReturn(Map.of(OWNER, card(OWNER, AccountState.ACTIVE, true)));

    assertThatThrownBy(() -> access.open(C, Optional.of(VISITOR)))
        .isInstanceOf(CollectionNotFoundException.class);
    assertThatThrownBy(() -> access.open(C, Optional.empty()))
        .isInstanceOf(CollectionNotFoundException.class);
  }

  /// 公开夹跟随 profileVisible；identity 省略所有者也是 404。
  @Test
  void should_follow_profile_visible_for_public() {
    stubCollection(CollectionVisibility.PUBLIC);
    when(users.findCards(List.of(OWNER), VISITOR))
        .thenReturn(Map.of(OWNER, card(OWNER, AccountState.ACTIVE, true)));
    when(users.findCards(List.of(OWNER), null))
        .thenReturn(Map.of(OWNER, card(OWNER, AccountState.ACTIVE, false)));

    assertThat(access.open(C, Optional.of(VISITOR)).viewerIsOwner()).isFalse();
    assertThatThrownBy(() -> access.open(C, Optional.empty()))
        .isInstanceOf(CollectionNotFoundException.class);

    when(users.findCards(List.of(OWNER), VISITOR)).thenReturn(Map.of());
    assertThatThrownBy(() -> access.open(C, Optional.of(VISITOR)))
        .isInstanceOf(CollectionNotFoundException.class);
  }

  /// identity 不可用是 503，不变成 404。
  @Test
  void should_propagate_identity_outage_for_public() {
    stubCollection(CollectionVisibility.PUBLIC);
    when(users.findCards(List.of(OWNER), null))
        .thenThrow(new UserDirectoryUnavailableException(new RuntimeException()));

    assertThatThrownBy(() -> access.open(C, Optional.empty()))
        .isInstanceOf(UserDirectoryUnavailableException.class);
  }

  /// 不存在 404。
  @Test
  void should_404_when_missing() {
    when(reads.findCollection(C)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> access.open(C, Optional.of(OWNER)))
        .isInstanceOf(CollectionNotFoundException.class);
  }

  /// 收藏夹桩。
  ///
  /// @param visibility 可见性
  private void stubCollection(CollectionVisibility visibility) {
    when(reads.findCollection(C)).thenReturn(Optional.of(collection(500L, OWNER, visibility, 3)));
  }
}
