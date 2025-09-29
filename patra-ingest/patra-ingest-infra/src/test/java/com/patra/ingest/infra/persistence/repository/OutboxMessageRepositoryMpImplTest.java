package com.patra.ingest.infra.persistence.repository;

import com.patra.ingest.domain.exception.OutboxPersistenceException;
import com.patra.ingest.domain.model.entity.OutboxMessage;
import com.patra.ingest.infra.persistence.converter.OutboxMessageConverter;
import com.patra.ingest.infra.persistence.entity.OutboxMessageDO;
import com.patra.ingest.infra.persistence.mapper.OutboxMessageMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * {@link OutboxMessageRepositoryMpImpl} 的单元测试（全 Mock）。
 */
class OutboxMessageRepositoryMpImplTest {

    private OutboxMessage.Builder base() {
        return OutboxMessage.builder()
                .aggregateType("PLAN")
                .aggregateId(1L)
                .channel("ch")
                .opType("HARVEST")
                .partitionKey("p1")
                .dedupKey("d1")
                .payloadJson("{}");
    }

    @Test
    @DisplayName("saveAll 空列表/正常插入")
    void saveAll_shouldInsertEach() {
        OutboxMessageMapper mapper = mock(OutboxMessageMapper.class);
        OutboxMessageConverter converter = mock(OutboxMessageConverter.class);
        OutboxMessageRepositoryMpImpl repo = new OutboxMessageRepositoryMpImpl(mapper, converter);
        // 空输入直接返回
        repo.saveAll(null);
        repo.saveAll(List.of());

        // 非空逐条 insert
        OutboxMessage msg = base().build();
        OutboxMessageDO entity = new OutboxMessageDO();
        when(converter.toEntity(msg)).thenReturn(entity);
        repo.saveAll(List.of(msg));
        verify(mapper, times(1)).insert(entity);
    }

    @Test
    @DisplayName("saveOrUpdate 根据 id 判定 insert/update")
    void saveOrUpdate_shouldInsertOrUpdate() {
        OutboxMessageMapper mapper = mock(OutboxMessageMapper.class);
        OutboxMessageConverter converter = mock(OutboxMessageConverter.class);
        OutboxMessageRepositoryMpImpl repo = new OutboxMessageRepositoryMpImpl(mapper, converter);

        OutboxMessage msg = base().build();
        OutboxMessageDO entity = new OutboxMessageDO();
        when(converter.toEntity(msg)).thenReturn(entity);
        repo.saveOrUpdate(msg);
        verify(mapper).insert(entity);

        entity.setId(1L);
        repo.saveOrUpdate(msg);
        verify(mapper).updateById(entity);

        // null 忽略分支
        repo.saveOrUpdate(null);
        verifyNoMoreInteractions(mapper);
    }

    @Test
    @DisplayName("findByChannelAndDedup 正常/空返回")
    void findByChannelAndDedup() {
        OutboxMessageMapper mapper = mock(OutboxMessageMapper.class);
        OutboxMessageConverter converter = mock(OutboxMessageConverter.class);
        OutboxMessageRepositoryMpImpl repo = new OutboxMessageRepositoryMpImpl(mapper, converter);

        when(mapper.findByChannelAndDedup("ch", "d1")).thenReturn(null);
        assertTrue(repo.findByChannelAndDedup("ch", "d1").isEmpty());

        OutboxMessageDO entity = new OutboxMessageDO();
        OutboxMessage domain = base().build();
        when(mapper.findByChannelAndDedup("ch", "d1")).thenReturn(entity);
        when(converter.toDomain(entity)).thenReturn(domain);
        Optional<OutboxMessage> res = repo.findByChannelAndDedup("ch", "d1");
        assertTrue(res.isPresent());
        assertEquals("d1", res.get().getDedupKey());
    }

    @Test
    @DisplayName("fetchPending limit<=0/空/正常映射")
    void fetchPending_shouldHandleLimitAndEmpty() {
        OutboxMessageMapper mapper = mock(OutboxMessageMapper.class);
        OutboxMessageConverter converter = mock(OutboxMessageConverter.class);
        OutboxMessageRepositoryMpImpl repo = new OutboxMessageRepositoryMpImpl(mapper, converter);

        assertTrue(repo.fetchPending("ch", Instant.now(), 0).isEmpty());

        when(mapper.fetchPending(anyString(), any(), anyInt())).thenReturn(List.of());
        assertTrue(repo.fetchPending("ch", Instant.now(), 10).isEmpty());

        OutboxMessageDO entity = new OutboxMessageDO();
        OutboxMessage domain = base().build();
        when(mapper.fetchPending(anyString(), any(), anyInt())).thenReturn(List.of(entity));
        when(converter.toDomain(entity)).thenReturn(domain);
        List<OutboxMessage> list = repo.fetchPending("ch", Instant.now(), 10);
        assertEquals(1, list.size());
    }

    @Test
    @DisplayName("acquireLease 返回影响行数判定成功与否")
    void acquireLease_branch() {
        OutboxMessageMapper mapper = mock(OutboxMessageMapper.class);
        OutboxMessageConverter converter = mock(OutboxMessageConverter.class);
        OutboxMessageRepositoryMpImpl repo = new OutboxMessageRepositoryMpImpl(mapper, converter);

        when(mapper.acquireLease(anyLong(), anyLong(), anyString(), any())).thenReturn(0);
        assertFalse(repo.acquireLease(1L, 1L, "o", Instant.now()));
        when(mapper.acquireLease(anyLong(), anyLong(), anyString(), any())).thenReturn(1);
        assertTrue(repo.acquireLease(1L, 1L, "o", Instant.now()));
    }

    @Test
    @DisplayName("markPublished/markDeferred/markFailed 影响行数!=1 抛异常")
    void markOps_shouldThrowOnConflict() {
        OutboxMessageMapper mapper = mock(OutboxMessageMapper.class);
        OutboxMessageConverter converter = mock(OutboxMessageConverter.class);
        OutboxMessageRepositoryMpImpl repo = new OutboxMessageRepositoryMpImpl(mapper, converter);

        when(mapper.markPublished(anyLong(), anyLong(), anyString())).thenReturn(0);
        assertThrows(OutboxPersistenceException.class, () -> repo.markPublished(1L, 1L, "m"));

        when(mapper.markDeferred(anyLong(), anyLong(), anyInt(), any(), anyString(), anyString())).thenReturn(0);
        assertThrows(OutboxPersistenceException.class, () -> repo.markDeferred(1L, 1L, 1, Instant.now(), "E", "err"));

        when(mapper.markFailed(anyLong(), anyLong(), anyInt(), anyString(), anyString())).thenReturn(0);
        assertThrows(OutboxPersistenceException.class, () -> repo.markFailed(1L, 1L, 3, "E", "err"));

        // 成功路径
        when(mapper.markPublished(anyLong(), anyLong(), anyString())).thenReturn(1);
        repo.markPublished(1L, 1L, "m");
        when(mapper.markDeferred(anyLong(), anyLong(), anyInt(), any(), anyString(), anyString())).thenReturn(1);
        repo.markDeferred(1L, 1L, 1, Instant.now(), "E", "err");
        when(mapper.markFailed(anyLong(), anyLong(), anyInt(), anyString(), anyString())).thenReturn(1);
        repo.markFailed(1L, 1L, 3, "E", "err");
    }
}
