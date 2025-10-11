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
 * Unit tests for {@link OutboxMessageRepositoryMpImpl} (full mocks).
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
    @DisplayName("saveAll empty list / normal insert")
    void saveAll_shouldInsertEach() {
        OutboxMessageMapper mapper = mock(OutboxMessageMapper.class);
        OutboxMessageConverter converter = mock(OutboxMessageConverter.class);
        OutboxMessageRepositoryMpImpl repo = new OutboxMessageRepositoryMpImpl(mapper, converter);
        // Empty input: no-op
        repo.saveAll(null);
        repo.saveAll(List.of());

        // Non-empty: insert each
        OutboxMessage msg = base().build();
        OutboxMessageDO entity = new OutboxMessageDO();
        when(converter.toEntity(msg)).thenReturn(entity);
        repo.saveAll(List.of(msg));
        verify(mapper, times(1)).insert(entity);
    }

    @Test
    @DisplayName("saveOrUpdate insert/update decided by id presence")
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

        // Null input branch
        repo.saveOrUpdate(null);
        verifyNoMoreInteractions(mapper);
    }

    @Test
    @DisplayName("findByChannelAndDedup normal/empty")
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
    @DisplayName("fetchPending limit<=0 / empty / normal mapping")
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
    @DisplayName("acquireLease uses affected row count to determine success")
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
    @DisplayName("markPublished/markDeferred/markFailed throws when affected!=1")
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

        // Success paths
        when(mapper.markPublished(anyLong(), anyLong(), anyString())).thenReturn(1);
        repo.markPublished(1L, 1L, "m");
        when(mapper.markDeferred(anyLong(), anyLong(), anyInt(), any(), anyString(), anyString())).thenReturn(1);
        repo.markDeferred(1L, 1L, 1, Instant.now(), "E", "err");
        when(mapper.markFailed(anyLong(), anyLong(), anyInt(), anyString(), anyString())).thenReturn(1);
        repo.markFailed(1L, 1L, 3, "E", "err");
    }
}
