package com.patra.ingest.infra.persistence.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.patra.ingest.domain.exception.OutboxPersistenceException;
import com.patra.ingest.domain.model.entity.OutboxMessage;
import com.patra.ingest.infra.persistence.converter.OutboxMessageConverter;
import com.patra.ingest.infra.persistence.entity.OutboxMessageDO;
import com.patra.ingest.infra.persistence.mapper.OutboxMessageMapper;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/// OutboxMessageRepositoryMpImpl 单元测试。
/// 
/// 测试策略：
/// 
/// - 使用 Mockito Mock 所有依赖（Mapper, Converter）
///   - 不启动 Spring 容器，纯单元测试
///   - 验证方法调用、参数传递和返回值转换
///   - 覆盖发件箱模式的租约和状态转换语义
/// 
/// 覆盖场景：
/// 
/// - 批量保存消息
///   - 单个保存或更新消息
///   - 按通道和去重键查找
///   - 获取待发布消息
///   - 租约操作（获取/释放）
///   - 状态转换（标记为已发布/延迟/失败）
///   - 批量操作（查询/更新/upsert）
/// 
/// @author linqibin
/// @since 0.1.0
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("OutboxMessageRepositoryMpImpl 单元测试")
class OutboxMessageRepositoryMpImplTest {

  @Mock private OutboxMessageMapper mapper;
  @Mock private OutboxMessageConverter converter;

  @InjectMocks private OutboxMessageRepositoryMpImpl repository;

  private static final Long TEST_MESSAGE_ID = 1L;
  private static final String TEST_CHANNEL = "task-ready";
  private static final String TEST_DEDUP_KEY = "dedup-001";
  private static final Long TEST_VERSION = 1L;
  private static final String TEST_OWNER = "relay-01";
  private static final Instant TEST_NOW = Instant.parse("2025-01-01T00:00:00Z");

  @Nested
  @DisplayName("批量保存操作")
  class SaveAllTests {

    @Test
    @DisplayName("应批量插入待发布消息")
    void shouldBatchInsertPendingMessages() {
      // Given
      OutboxMessage message1 = createTestOutboxMessage(null, TEST_DEDUP_KEY + "-1");
      OutboxMessage message2 = createTestOutboxMessage(null, TEST_DEDUP_KEY + "-2");
      List<OutboxMessage> messages = List.of(message1, message2);

      OutboxMessageDO entity1 = createTestOutboxMessageDO(null, TEST_DEDUP_KEY + "-1");
      OutboxMessageDO entity2 = createTestOutboxMessageDO(null, TEST_DEDUP_KEY + "-2");

      when(converter.toEntity(message1)).thenReturn(entity1);
      when(converter.toEntity(message2)).thenReturn(entity2);
      when(mapper.insert(any(OutboxMessageDO.class))).thenReturn(1);

      // When
      repository.saveAll(messages);

      // Then
      verify(converter).toEntity(message1);
      verify(converter).toEntity(message2);
      verify(mapper).insert(entity1);
      verify(mapper).insert(entity2);
    }

    @Test
    @DisplayName("应在消息列表为 null 时不执行任何操作")
    void shouldDoNothingWhenMessagesIsNull() {
      // When
      repository.saveAll(null);

      // Then
      verify(mapper, never()).insert(any(OutboxMessageDO.class));
    }

    @Test
    @DisplayName("应在消息列表为空时不执行任何操作")
    void shouldDoNothingWhenMessagesIsEmpty() {
      // When
      repository.saveAll(Collections.emptyList());

      // Then
      verify(mapper, never()).insert(any(OutboxMessageDO.class));
    }
  }

  @Nested
  @DisplayName("保存或更新操作")
  class SaveOrUpdateTests {

    @Test
    @DisplayName("应在 ID 为 null 时插入消息")
    void shouldInsertWhenIdIsNull() {
      // Given
      OutboxMessage message = createTestOutboxMessage(null, TEST_DEDUP_KEY);
      OutboxMessageDO entity = createTestOutboxMessageDO(null, TEST_DEDUP_KEY);

      when(converter.toEntity(message)).thenReturn(entity);
      when(mapper.insert(entity)).thenReturn(1);

      // When
      repository.saveOrUpdate(message);

      // Then
      verify(converter).toEntity(message);
      verify(mapper).insert(entity);
      verify(mapper, never()).updateById(any(OutboxMessageDO.class));
    }

    @Test
    @DisplayName("应在 ID 存在时更新消息")
    void shouldUpdateWhenIdExists() {
      // Given
      OutboxMessage message = createTestOutboxMessage(TEST_MESSAGE_ID, TEST_DEDUP_KEY);
      OutboxMessageDO entity = createTestOutboxMessageDO(TEST_MESSAGE_ID, TEST_DEDUP_KEY);

      when(converter.toEntity(message)).thenReturn(entity);
      when(mapper.updateById(entity)).thenReturn(1);

      // When
      repository.saveOrUpdate(message);

      // Then
      verify(converter).toEntity(message);
      verify(mapper).updateById(entity);
      verify(mapper, never()).insert(any(OutboxMessageDO.class));
    }

    @Test
    @DisplayName("应在消息为 null 时不执行任何操作")
    void shouldDoNothingWhenMessageIsNull() {
      // When
      repository.saveOrUpdate(null);

      // Then
      verify(mapper, never()).insert(any(OutboxMessageDO.class));
      verify(mapper, never()).updateById(any(OutboxMessageDO.class));
    }
  }

  @Nested
  @DisplayName("查询操作")
  class FindTests {

    @Test
    @DisplayName("应按通道和去重键查找消息")
    void shouldFindByChannelAndDedup() {
      // Given
      OutboxMessageDO entity = createTestOutboxMessageDO(TEST_MESSAGE_ID, TEST_DEDUP_KEY);
      OutboxMessage message = createTestOutboxMessage(TEST_MESSAGE_ID, TEST_DEDUP_KEY);

      when(mapper.findByChannelAndDedup(TEST_CHANNEL, TEST_DEDUP_KEY)).thenReturn(entity);
      when(converter.toDomain(entity)).thenReturn(message);

      // When
      Optional<OutboxMessage> result =
          repository.findByChannelAndDedup(TEST_CHANNEL, TEST_DEDUP_KEY);

      // Then
      assertThat(result).isPresent().contains(message);
      verify(mapper).findByChannelAndDedup(TEST_CHANNEL, TEST_DEDUP_KEY);
      verify(converter).toDomain(entity);
    }

    @Test
    @DisplayName("应在消息不存在时返回空 Optional")
    void shouldReturnEmptyWhenMessageNotFound() {
      // Given
      when(mapper.findByChannelAndDedup(TEST_CHANNEL, TEST_DEDUP_KEY)).thenReturn(null);

      // When
      Optional<OutboxMessage> result =
          repository.findByChannelAndDedup(TEST_CHANNEL, TEST_DEDUP_KEY);

      // Then
      assertThat(result).isEmpty();
      verify(mapper).findByChannelAndDedup(TEST_CHANNEL, TEST_DEDUP_KEY);
      verify(converter, never()).toDomain(any(OutboxMessageDO.class));
    }
  }

  @Nested
  @DisplayName("获取待发布消息")
  class FetchPendingTests {

    @Test
    @DisplayName("应获取指定通道的待发布消息")
    void shouldFetchPendingMessagesForChannel() {
      // Given
      OutboxMessageDO entity1 = createTestOutboxMessageDO(1L, "dedup-1");
      OutboxMessageDO entity2 = createTestOutboxMessageDO(2L, "dedup-2");
      List<OutboxMessageDO> entities = List.of(entity1, entity2);

      OutboxMessage message1 = createTestOutboxMessage(1L, "dedup-1");
      OutboxMessage message2 = createTestOutboxMessage(2L, "dedup-2");

      when(mapper.fetchPending(TEST_CHANNEL, TEST_NOW, 10)).thenReturn(entities);
      when(converter.toDomain(entity1)).thenReturn(message1);
      when(converter.toDomain(entity2)).thenReturn(message2);

      // When
      List<OutboxMessage> result = repository.fetchPending(TEST_CHANNEL, TEST_NOW, 10);

      // Then
      assertThat(result).hasSize(2).containsExactly(message1, message2);
      verify(mapper).fetchPending(TEST_CHANNEL, TEST_NOW, 10);
    }

    @Test
    @DisplayName("应在 limit 小于等于 0 时返回空列表")
    void shouldReturnEmptyListWhenLimitIsZeroOrNegative() {
      // When
      List<OutboxMessage> result = repository.fetchPending(TEST_CHANNEL, TEST_NOW, 0);

      // Then
      assertThat(result).isEmpty();
      verify(mapper, never()).fetchPending(anyString(), any(Instant.class), eq(0));
    }

    @Test
    @DisplayName("应支持从所有通道获取消息（channel 为 null）")
    void shouldFetchPendingFromAllChannelsWhenChannelIsNull() {
      // Given
      OutboxMessageDO entity = createTestOutboxMessageDO(1L, "dedup-1");
      List<OutboxMessageDO> entities = List.of(entity);
      OutboxMessage message = createTestOutboxMessage(1L, "dedup-1");

      when(mapper.fetchPending(null, TEST_NOW, 10)).thenReturn(entities);
      when(converter.toDomain(entity)).thenReturn(message);

      // When
      List<OutboxMessage> result = repository.fetchPending(null, TEST_NOW, 10);

      // Then
      assertThat(result).hasSize(1).containsExactly(message);
      verify(mapper).fetchPending(null, TEST_NOW, 10);
    }
  }

  @Nested
  @DisplayName("租约操作")
  class LeaseTests {

    @Test
    @DisplayName("应成功获取租约")
    void shouldAcquireLeaseSuccessfully() {
      // Given
      Instant leaseExpireAt = TEST_NOW.plusSeconds(300);
      when(mapper.acquireLease(TEST_MESSAGE_ID, TEST_VERSION, TEST_OWNER, leaseExpireAt))
          .thenReturn(1);

      // When
      boolean result =
          repository.acquireLease(TEST_MESSAGE_ID, TEST_VERSION, TEST_OWNER, leaseExpireAt);

      // Then
      assertThat(result).isTrue();
      verify(mapper).acquireLease(TEST_MESSAGE_ID, TEST_VERSION, TEST_OWNER, leaseExpireAt);
    }

    @Test
    @DisplayName("应在版本冲突时获取租约失败")
    void shouldFailToAcquireLeaseWhenVersionConflict() {
      // Given
      Instant leaseExpireAt = TEST_NOW.plusSeconds(300);
      when(mapper.acquireLease(TEST_MESSAGE_ID, TEST_VERSION, TEST_OWNER, leaseExpireAt))
          .thenReturn(0);

      // When
      boolean result =
          repository.acquireLease(TEST_MESSAGE_ID, TEST_VERSION, TEST_OWNER, leaseExpireAt);

      // Then
      assertThat(result).isFalse();
      verify(mapper).acquireLease(TEST_MESSAGE_ID, TEST_VERSION, TEST_OWNER, leaseExpireAt);
    }
  }

  @Nested
  @DisplayName("状态转换操作")
  class StateTransitionTests {

    @Test
    @DisplayName("应成功标记消息为已发布")
    void shouldMarkPublishedSuccessfully() {
      // Given
      when(mapper.markPublished(TEST_MESSAGE_ID, TEST_VERSION)).thenReturn(1);

      // When
      repository.markPublished(TEST_MESSAGE_ID, TEST_VERSION);

      // Then
      verify(mapper).markPublished(TEST_MESSAGE_ID, TEST_VERSION);
    }

    @Test
    @DisplayName("应在版本冲突时标记已发布失败并抛出异常")
    void shouldThrowExceptionWhenMarkPublishedFailsDueToVersionConflict() {
      // Given
      when(mapper.markPublished(TEST_MESSAGE_ID, TEST_VERSION)).thenReturn(0);

      // When & Then
      assertThatThrownBy(() -> repository.markPublished(TEST_MESSAGE_ID, TEST_VERSION))
          .isInstanceOf(OutboxPersistenceException.class)
          .hasMessageContaining("Failed to update Outbox state to PUBLISHED");

      verify(mapper).markPublished(TEST_MESSAGE_ID, TEST_VERSION);
    }

    @Test
    @DisplayName("应成功标记消息为延迟重试")
    void shouldMarkDeferredSuccessfully() {
      // Given
      Instant nextRetryAt = TEST_NOW.plusSeconds(60);
      when(mapper.markDeferred(
              TEST_MESSAGE_ID, TEST_VERSION, 1, nextRetryAt, "NETWORK_ERROR", "Connection timeout"))
          .thenReturn(1);

      // When
      repository.markDeferred(
          TEST_MESSAGE_ID, TEST_VERSION, 1, nextRetryAt, "NETWORK_ERROR", "Connection timeout");

      // Then
      verify(mapper)
          .markDeferred(
              TEST_MESSAGE_ID, TEST_VERSION, 1, nextRetryAt, "NETWORK_ERROR", "Connection timeout");
    }

    @Test
    @DisplayName("应在版本冲突时标记延迟失败并抛出异常")
    void shouldThrowExceptionWhenMarkDeferredFailsDueToVersionConflict() {
      // Given
      Instant nextRetryAt = TEST_NOW.plusSeconds(60);
      when(mapper.markDeferred(
              TEST_MESSAGE_ID, TEST_VERSION, 1, nextRetryAt, "NETWORK_ERROR", "Connection timeout"))
          .thenReturn(0);

      // When & Then
      assertThatThrownBy(
              () ->
                  repository.markDeferred(
                      TEST_MESSAGE_ID,
                      TEST_VERSION,
                      1,
                      nextRetryAt,
                      "NETWORK_ERROR",
                      "Connection timeout"))
          .isInstanceOf(OutboxPersistenceException.class)
          .hasMessageContaining("Failed to mark Outbox for retry");

      verify(mapper)
          .markDeferred(
              TEST_MESSAGE_ID, TEST_VERSION, 1, nextRetryAt, "NETWORK_ERROR", "Connection timeout");
    }

    @Test
    @DisplayName("应成功标记消息为永久失败")
    void shouldMarkFailedSuccessfully() {
      // Given
      when(mapper.markFailed(
              TEST_MESSAGE_ID, TEST_VERSION, 3, "MAX_RETRIES_EXCEEDED", "Exhausted all retries"))
          .thenReturn(1);

      // When
      repository.markFailed(
          TEST_MESSAGE_ID, TEST_VERSION, 3, "MAX_RETRIES_EXCEEDED", "Exhausted all retries");

      // Then
      verify(mapper)
          .markFailed(
              TEST_MESSAGE_ID, TEST_VERSION, 3, "MAX_RETRIES_EXCEEDED", "Exhausted all retries");
    }

    @Test
    @DisplayName("应在版本冲突时标记失败失败并抛出异常")
    void shouldThrowExceptionWhenMarkFailedFailsDueToVersionConflict() {
      // Given
      when(mapper.markFailed(
              TEST_MESSAGE_ID, TEST_VERSION, 3, "MAX_RETRIES_EXCEEDED", "Exhausted all retries"))
          .thenReturn(0);

      // When & Then
      assertThatThrownBy(
              () ->
                  repository.markFailed(
                      TEST_MESSAGE_ID,
                      TEST_VERSION,
                      3,
                      "MAX_RETRIES_EXCEEDED",
                      "Exhausted all retries"))
          .isInstanceOf(OutboxPersistenceException.class)
          .hasMessageContaining("Failed to mark Outbox as DEAD");

      verify(mapper)
          .markFailed(
              TEST_MESSAGE_ID, TEST_VERSION, 3, "MAX_RETRIES_EXCEEDED", "Exhausted all retries");
    }
  }

  @Nested
  @DisplayName("批量操作")
  class BatchOperationsTests {

    @Test
    @DisplayName("应批量查询消息")
    void shouldFindByChannelAndDedupIn() {
      // Given
      List<String> dedupKeys = List.of("dedup-1", "dedup-2");
      OutboxMessageDO entity1 = createTestOutboxMessageDO(1L, "dedup-1");
      OutboxMessageDO entity2 = createTestOutboxMessageDO(2L, "dedup-2");
      List<OutboxMessageDO> entities = List.of(entity1, entity2);

      OutboxMessage message1 = createTestOutboxMessage(1L, "dedup-1");
      OutboxMessage message2 = createTestOutboxMessage(2L, "dedup-2");

      when(mapper.findByChannelAndDedupIn(TEST_CHANNEL, dedupKeys)).thenReturn(entities);
      when(converter.toDomain(entity1)).thenReturn(message1);
      when(converter.toDomain(entity2)).thenReturn(message2);

      // When
      List<OutboxMessage> result = repository.findByChannelAndDedupIn(TEST_CHANNEL, dedupKeys);

      // Then
      assertThat(result).hasSize(2).containsExactly(message1, message2);
      verify(mapper).findByChannelAndDedupIn(TEST_CHANNEL, dedupKeys);
    }

    @Test
    @DisplayName("应在去重键列表为空时返回空列表")
    void shouldReturnEmptyListWhenDedupKeysIsEmpty() {
      // When
      List<OutboxMessage> result =
          repository.findByChannelAndDedupIn(TEST_CHANNEL, Collections.emptyList());

      // Then
      assertThat(result).isEmpty();
      verify(mapper, never()).findByChannelAndDedupIn(anyString(), any());
    }

    @Test
    @DisplayName("应批量更新消息")
    void shouldUpdateBatch() {
      // Given
      OutboxMessage message1 = createTestOutboxMessage(1L, "dedup-1");
      OutboxMessage message2 = createTestOutboxMessage(2L, "dedup-2");
      List<OutboxMessage> messages = List.of(message1, message2);

      OutboxMessageDO entity1 = createTestOutboxMessageDO(1L, "dedup-1");
      OutboxMessageDO entity2 = createTestOutboxMessageDO(2L, "dedup-2");

      when(converter.toEntity(message1)).thenReturn(entity1);
      when(converter.toEntity(message2)).thenReturn(entity2);
      when(mapper.updateById(any(OutboxMessageDO.class))).thenReturn(1);

      // When
      repository.updateBatch(messages);

      // Then
      verify(mapper).updateById(entity1);
      verify(mapper).updateById(entity2);
    }

    @Test
    @DisplayName("应在批量更新时检查消息 ID")
    void shouldThrowExceptionWhenUpdateBatchWithNullId() {
      // Given
      OutboxMessage message = createTestOutboxMessage(null, "dedup-1");
      OutboxMessageDO entity = createTestOutboxMessageDO(null, "dedup-1");
      when(converter.toEntity(message)).thenReturn(entity);

      // When & Then
      assertThatThrownBy(() -> repository.updateBatch(List.of(message)))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Cannot update Outbox message without ID");
    }

    @Test
    @DisplayName("应批量 upsert 消息")
    void shouldUpsertBatch() {
      // Given
      OutboxMessage message1 = createTestOutboxMessage(1L, "dedup-1");
      OutboxMessage message2 = createTestOutboxMessage(2L, "dedup-2");
      List<OutboxMessage> messages = List.of(message1, message2);

      OutboxMessageDO entity1 = createTestOutboxMessageDO(1L, "dedup-1");
      OutboxMessageDO entity2 = createTestOutboxMessageDO(2L, "dedup-2");

      when(converter.toEntity(message1)).thenReturn(entity1);
      when(converter.toEntity(message2)).thenReturn(entity2);
      when(mapper.upsertBatch(any())).thenReturn(2);

      // When
      repository.upsertBatch(messages);

      // Then
      verify(mapper).upsertBatch(any());
    }

    @Test
    @DisplayName("应在消息列表为空时不执行 upsert")
    void shouldDoNothingWhenUpsertBatchWithEmptyList() {
      // When
      repository.upsertBatch(Collections.emptyList());

      // Then
      verify(mapper, never()).upsertBatch(any());
    }
  }

  // ==================== 辅助方法 ====================

  private OutboxMessage createTestOutboxMessage(Long id, String dedupKey) {
    OutboxMessage message = org.mockito.Mockito.mock(OutboxMessage.class);
    when(message.getId()).thenReturn(id);
    when(message.getChannel()).thenReturn(TEST_CHANNEL);
    when(message.getDedupKey()).thenReturn(dedupKey);
    when(message.getVersion()).thenReturn(TEST_VERSION);
    return message;
  }

  private OutboxMessageDO createTestOutboxMessageDO(Long id, String dedupKey) {
    OutboxMessageDO entity = new OutboxMessageDO();
    entity.setId(id);
    entity.setChannel(TEST_CHANNEL);
    entity.setDedupKey(dedupKey);
    entity.setVersion(TEST_VERSION);
    entity.setStatusCode("PENDING");
    return entity;
  }
}
