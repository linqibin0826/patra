package dev.patra.interaction.app.collection.usecase.query;

import static dev.patra.interaction.app.collection.Fixtures.card;
import static dev.patra.interaction.app.collection.Fixtures.collection;
import static dev.patra.interaction.app.collection.Fixtures.view;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.patra.interaction.app.collection.TestUsers;
import dev.patra.interaction.app.collection.service.CollectionAccess;
import dev.patra.interaction.app.collection.service.SubjectViews;
import dev.patra.interaction.domain.collection.model.enums.CollectionVisibility;
import dev.patra.interaction.domain.collection.model.read.CollectionSummary;
import dev.patra.interaction.domain.collection.model.read.ItemRef;
import dev.patra.interaction.domain.collection.model.vo.CollectionId;
import dev.patra.interaction.domain.collection.port.read.LibraryReadPort;
import dev.patra.interaction.domain.subject.exception.UserDirectoryUnavailableException;
import dev.patra.interaction.domain.subject.model.enums.AccountState;
import dev.patra.interaction.domain.subject.model.enums.SubjectState;
import dev.patra.interaction.domain.subject.model.vo.SubjectId;
import dev.patra.interaction.domain.subject.port.read.SubjectReadPort;
import dev.patra.interaction.domain.subject.port.user.UserDirectoryPort;
import dev.patra.tool.core.security.CurrentUserPort;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/// 公开收藏夹与主页公开收藏夹列表的查询。
class PublicCollectionQueryServiceTest {

  private static final long OWNER = 7L;
  private static final long VISITOR = 9L;
  private static final CollectionId C = CollectionId.of(500L);
  private static final SubjectId S1 = SubjectId.of(11L);
  private static final Instant T = Instant.parse("2026-10-24T00:00:00Z");

  private final LibraryReadPort reads = mock(LibraryReadPort.class);
  private final UserDirectoryPort users = mock(UserDirectoryPort.class);
  private final SubjectReadPort subjects = mock(SubjectReadPort.class);

  /// 别人的主页对访客不可见、或 identity 省略他：空列表（不是 404）。
  @Test
  void should_return_empty_profile_list_when_owner_not_visible() {
    PublicCollectionQueryService service = service(TestUsers.signedIn(VISITOR));
    when(users.findCards(List.of(OWNER), VISITOR))
        .thenReturn(Map.of(OWNER, card(OWNER, AccountState.ACTIVE, false)));

    assertThat(service.userCollections(OWNER)).isEmpty();
    when(users.findCards(List.of(OWNER), VISITOR)).thenReturn(Map.of());
    assertThat(service.userCollections(OWNER)).isEmpty();
    verify(reads, never()).findPublicCollections(anyLong());
  }

  /// 可见时回公开收藏夹；匿名访客透传 null。
  @Test
  void should_list_public_collections_when_visible() {
    PublicCollectionQueryService service = service(TestUsers.anonymous());
    when(users.findCards(List.of(OWNER), null))
        .thenReturn(Map.of(OWNER, card(OWNER, AccountState.ACTIVE, true)));
    List<CollectionSummary> published =
        List.of(collection(500L, OWNER, CollectionVisibility.PUBLIC, 2));
    when(reads.findPublicCollections(OWNER)).thenReturn(published);

    assertThat(service.userCollections(OWNER)).isEqualTo(published);
  }

  /// 本人看自己：只回公开的，不问 identity。
  @Test
  void should_list_own_public_collections_without_identity() {
    PublicCollectionQueryService service = service(TestUsers.signedIn(OWNER));

    service.userCollections(OWNER);

    verifyNoInteractions(users);
    verify(reads).findPublicCollections(OWNER);
  }

  /// identity 不可用是 503。
  @Test
  void should_propagate_identity_outage_for_profile_list() {
    PublicCollectionQueryService service = service(TestUsers.anonymous());
    when(users.findCards(List.of(OWNER), null))
        .thenThrow(new UserDirectoryUnavailableException(new RuntimeException()));

    assertThatThrownBy(() -> service.userCollections(OWNER))
        .isInstanceOf(UserDirectoryUnavailableException.class);
  }

  /// 公开夹的条目：带加入时间，不读所有者的备注与归属。
  @Test
  void should_page_public_items_without_notes() {
    PublicCollectionQueryService service = service(TestUsers.anonymous());
    when(reads.findCollection(C))
        .thenReturn(Optional.of(collection(500L, OWNER, CollectionVisibility.PUBLIC, 1)));
    when(users.findCards(List.of(OWNER), null))
        .thenReturn(Map.of(OWNER, card(OWNER, AccountState.ACTIVE, true)));
    when(reads.pageCollection(OWNER, C, 0, 50)).thenReturn(List.of(ItemRef.of(S1, T)));
    when(subjects.findByIds(anyCollection()))
        .thenReturn(Map.of(S1, view(11L, SubjectState.ACTIVE, "101", "J", "2019")));

    PageView<PublicItemView> page = service.items(500L, null, 1, 50);

    assertThat(page.items())
        .containsExactly(
            PublicItemView.builder()
                .itemId(S1)
                .type(view(11L, SubjectState.ACTIVE, "101", "J", "2019").type())
                .key("101")
                .state(SubjectState.ACTIVE)
                .addedAt(T)
                .build());
    verify(reads, never()).findEntries(anyLong(), anyCollection());
    verify(reads, never()).findMemberships(anyLong(), anyCollection());
  }

  /// 收藏夹头：带所有者摘要与「是否本人」。
  @Test
  void should_return_header_with_owner() {
    PublicCollectionQueryService service = service(TestUsers.signedIn(VISITOR));
    when(reads.findCollection(C))
        .thenReturn(Optional.of(collection(500L, OWNER, CollectionVisibility.PUBLIC, 1)));
    when(users.findCards(List.of(OWNER), VISITOR))
        .thenReturn(Map.of(OWNER, card(OWNER, AccountState.ACTIVE, true)));

    PublicCollectionView header = service.collection(500L);

    assertThat(header.owner().username()).isEqualTo("u7");
    assertThat(header.viewerIsOwner()).isFalse();
  }

  /// 建服务。
  ///
  /// @param currentUser 当前用户
  /// @return 服务
  private PublicCollectionQueryService service(CurrentUserPort currentUser) {
    return new PublicCollectionQueryService(
        new CollectionAccess(reads, users), reads, users, new SubjectViews(subjects), currentUser);
  }
}
