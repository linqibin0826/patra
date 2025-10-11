package com.patra.ingest.infra.persistence.repository;

import com.patra.ingest.domain.model.aggregate.PlanAggregate;
import com.patra.ingest.infra.persistence.converter.PlanConverter;
import com.patra.ingest.infra.persistence.entity.PlanDO;
import com.patra.ingest.infra.persistence.mapper.PlanMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link PlanRepositoryMpImpl}.
 */
class PlanRepositoryMpImplTest {

    @Test
    @DisplayName("save chooses insert or update by id presence and returns aggregate")
    void save_shouldInsertOrUpdate() {
        PlanMapper mapper = mock(PlanMapper.class);
        PlanConverter converter = mock(PlanConverter.class);
        PlanRepositoryMpImpl repo = new PlanRepositoryMpImpl(mapper, converter);

        PlanAggregate agg = PlanAggregate.create(1L, "k", "P", "HARVEST", null, null, null, null, null, null, null, null);
        PlanDO entity = new PlanDO();
        when(converter.toEntity(agg)).thenReturn(entity);
        when(converter.toAggregate(entity)).thenReturn(agg);

        PlanAggregate saved = repo.save(agg);
        verify(mapper).insert(entity);
        assertSame(agg, saved);

        entity.setId(1L);
        repo.save(agg);
        verify(mapper).updateById(entity);
    }

    @Test
    @DisplayName("findByPlanKey blank returns empty; normal mapping to Optional")
    void findByPlanKey_shouldHandleBlank() {
        PlanMapper mapper = mock(PlanMapper.class);
        PlanConverter converter = mock(PlanConverter.class);
        PlanRepositoryMpImpl repo = new PlanRepositoryMpImpl(mapper, converter);

        assertTrue(repo.findByPlanKey(" ").isEmpty());
        when(mapper.findByPlanKey("k")).thenReturn(null);
        assertTrue(repo.findByPlanKey("k").isEmpty());

        PlanDO entity = new PlanDO();
        PlanAggregate agg = PlanAggregate.create(1L, "k", "P", "HARVEST", null, null, null, null, null, null, null, null);
        when(mapper.findByPlanKey("k")).thenReturn(entity);
        when(converter.toAggregate(entity)).thenReturn(agg);
        Optional<PlanAggregate> res = repo.findByPlanKey("k");
        assertTrue(res.isPresent());
    }

    @Test
    @DisplayName("existsByPlanKey blank/normal branches")
    void existsByPlanKey_shouldHandleBlank() {
        PlanMapper mapper = mock(PlanMapper.class);
        PlanRepositoryMpImpl repo = new PlanRepositoryMpImpl(mapper, mock(PlanConverter.class));
        assertFalse(repo.existsByPlanKey(" "));
        when(mapper.countByPlanKey("k")).thenReturn(1);
        assertTrue(repo.existsByPlanKey("k"));
    }
}
