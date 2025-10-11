package com.patra.ingest.infra.persistence.repository;

import com.patra.ingest.domain.model.aggregate.PlanAggregate;
import com.patra.ingest.domain.port.PlanRepository;
import com.patra.ingest.infra.persistence.converter.PlanConverter;
import com.patra.ingest.infra.persistence.entity.PlanDO;
import com.patra.ingest.infra.persistence.mapper.PlanMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * MyBatis-Plus implementation of PlanRepository (Infrastructure layer).
 * <p>
 * Responsibilities:
 * <ul>
 *   <li>Mapping between PlanAggregate and PlanDO</li>
 *   <li>Idempotent query by planKey / existence check</li>
 *   <li>Insert / update (no complex conditional updates; optimistic locking via MP version field)</li>
 * </ul>
 * </p>
 * Logging strategy:
 * <ul>
 *   <li>DEBUG: log key fields on insert/update (id, planKey)</li>
 *   <li>INFO: avoid noisy high-frequency CRUD logs</li>
 * </ul>
 * Thread-safety: stateless singleton via dependency injection.
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class PlanRepositoryMpImpl implements PlanRepository {

    /** Plan mapper. */
    private final PlanMapper planMapper;
    /** Aggregate-to-DO converter. */
    private final PlanConverter planConverter;

    /**
     * Saves a Plan: insert or update based on presence of ID.
     * <p>Converts aggregate to DO and back to ensure version/auto-increment fields are reflected.</p>
     * @param plan aggregate (required)
     * @return persisted aggregate
     */
    @Override
    public PlanAggregate save(PlanAggregate plan) {
        PlanDO entity = planConverter.toEntity(plan);
        if (entity.getId() == null) {
            if (log.isDebugEnabled()) {
                log.debug("[INGEST][INFRA] plan insert planKey={}", entity.getPlanKey());
            }
            planMapper.insert(entity);
        } else {
            if (log.isDebugEnabled()) {
                log.debug("[INGEST][INFRA] plan update id={} planKey={}", entity.getId(), entity.getPlanKey());
            }
            planMapper.updateById(entity);
        }
        return planConverter.toAggregate(entity);
    }

    /**
     * Finds a plan by planKey.
     * @param planKey idempotent key (empty returns empty)
     */
    @Override
    public Optional<PlanAggregate> findByPlanKey(String planKey) {
        if (planKey == null || planKey.isBlank()) {
            return Optional.empty();
        }
        PlanDO entity = planMapper.findByPlanKey(planKey);
        return Optional.ofNullable(entity).map(planConverter::toAggregate);
    }

    /**
     * Checks whether a planKey exists.
     * @param planKey idempotent key
     */
    @Override
    public boolean existsByPlanKey(String planKey) {
        if (planKey == null || planKey.isBlank()) {
            return false;
        }
        return planMapper.countByPlanKey(planKey) > 0;
    }

    @Override
    public Optional<PlanAggregate> findById(Long planId) {
        if (planId == null) {
            return Optional.empty();
        }
        PlanDO entity = planMapper.selectById(planId);
        return Optional.ofNullable(entity).map(planConverter::toAggregate);
    }
}
