package dev.patra.interaction.app.collection.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.patra.interaction.domain.collection.model.read.DeletedUserData;
import dev.patra.interaction.domain.collection.port.read.LibraryReadPort;
import dev.patra.interaction.domain.collection.port.repository.LibraryRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

/// collection 登记的注销清理动作：有收藏数据的用户、先拿用户锁再删三张表。
class CollectionUserDataCleanupGatewayImplTest {

  private final LibraryReadPort reads = mock(LibraryReadPort.class);
  private final LibraryRepository library = mock(LibraryRepository.class);
  private final CollectionUserDataCleanupGatewayImpl cleanup =
      new CollectionUserDataCleanupGatewayImpl(reads, library);

  @Test
  void should_name_collection_subdomain() {
    assertThat(cleanup.subdomain()).isEqualTo("collection");
  }

  @Test
  void should_list_owners_from_library() {
    when(reads.findOwnersAfter(0L, 100)).thenReturn(List.of(1L, 2L));

    assertThat(cleanup.findUsersAfter(0L, 100)).containsExactly(1L, 2L);
  }

  /// 先拿用户锁，和他可能残留的请求串行，再删；返回三张表删掉的行数之和。
  @Test
  void should_lock_owner_before_deleting() {
    when(library.deleteAllOf(1L)).thenReturn(DeletedUserData.of(5, 2, 3));

    assertThat(cleanup.deleteAllOf(1L)).isEqualTo(10);
    InOrder order = inOrder(library);
    order.verify(library).lockOwner(1L);
    order.verify(library).deleteAllOf(1L);
  }
}
