package com.patra.ingest.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.patra.ingest.domain.model.aggregate.PlanSliceAggregate;
import com.patra.ingest.domain.port.PlanSliceRepository;
import com.patra.ingest.infra.persistence.converter.PlanSliceConverter;
import com.patra.ingest.infra.persistence.entity.PlanSliceDO;
import com.patra.ingest.infra.persistence.mapper.PlanSliceMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

/**
 * MyBatis-Plus implementation of PlanSliceRepository for plan slice aggregates.
 *
 * <p>Responsibilities:
 *
 * <ul>
 *   <li>Insert and update slice aggregates based on ID presence.
 *   <li>Batch save with sequential calls maintaining input order.
 *   <li>Query all slices by planId for scheduling and replay.
 * </ul>
 *
 * <p>Design and constraints:
 *
 * <ul>
 *   <li>No complex state machine validation in repository layer; state transitions controlled by
 *       application services.
 *   <li>Optimistic locking: if DO contains version field, updates handled automatically by
 *       MyBatis-Plus; can extend conditional updates for future concurrency scenarios.
 *   <li>Idempotency: caller ensures no duplicate creation of same business semantic slice (e.g.,
 *       sliceSignatureHash).
 * </ul>
 *
 * <p>Logging strategy:
 *
 * <ul>
 *   <li>DEBUG: insert/update logs planId and hash (exprHash represents expression fingerprint).
 *   <li>No INFO: avoid noise on high-frequency paths; errors handled by upper layers.
 * </ul>
 */
@Repository
@RequiredArgsConstructor
@Slf4j
public class PlanSliceRepositoryMpImpl implements PlanSliceRepository {

  private final PlanSliceMapper mapper;
  private final PlanSliceConverter converter;

  /**
   * Saves a single slice by inserting or updating.
   *
   * @param slice slice aggregate
   * @return persisted aggregate re-mapped to include generated fields
   */
  @Override
  public PlanSliceAggregate save(PlanSliceAggregate slice) {
    PlanSliceDO entity = converter.toEntity(slice);
    if (entity.getId() == null) {
      if (log.isDebugEnabled()) {
        log.debug("slice insert planId={} hash={}", entity.getPlanId(), entity.getExprHash());
      }
      mapper.insert(entity);
    } else {
      if (log.isDebugEnabled()) {
        log.debug(
            "slice update id={} planId={} hash={}",
            entity.getId(),
            entity.getPlanId(),
            entity.getExprHash());
      }
      mapper.updateById(entity);
    }
    return converter.toAggregate(entity);
  }

  /**
   * Batch saves slices by sequentially calling {@link #save(PlanSliceAggregate)}.
   *
   * @param slices collection of slices
   * @return persisted results maintaining input order
   */
  @Override
  public List<PlanSliceAggregate> saveAll(List<PlanSliceAggregate> slices) {
    List<PlanSliceAggregate> persisted = new ArrayList<>(slices.size());
    for (PlanSliceAggregate slice : slices) {
      persisted.add(save(slice));
    }
    return persisted;
  }

  /**
   * Finds slices by plan ID.
   *
   * @param planId plan ID
   * @return list of slices, may be empty
   */
  @Override
  public List<PlanSliceAggregate> findByPlanId(Long planId) {
    return mapper.selectList(new QueryWrapper<PlanSliceDO>().eq("plan_id", planId)).stream()
        .map(converter::toAggregate)
        .collect(Collectors.toList());
  }

  @Override
  public Optional<PlanSliceAggregate> findById(Long sliceId) {
    if (sliceId == null) {
      return Optional.empty();
    }
    PlanSliceDO entity = mapper.selectById(sliceId);
    return Optional.ofNullable(entity).map(converter::toAggregate);
  }
}
