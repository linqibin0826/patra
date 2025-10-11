package com.patra.ingest.domain.port;

import com.patra.ingest.domain.model.aggregate.PlanSliceAggregate;

import java.util.List;
import java.util.Optional;

/**
 * Repository port for plan slices.
 * <p>Persists slices generated during planning so task assembly and replay can query precise windows.</p>
 *
 * @author linqibin
 * @since 0.1.0
 */
public interface PlanSliceRepository {

    /**
     * Persist or update a single plan slice.
     *
     * @param slice plan slice aggregate containing window and filter context
     * @return persisted slice
     */
    PlanSliceAggregate save(PlanSliceAggregate slice);

    /**
     * Persist multiple plan slices at once.
     *
     * @param slices slices to persist
     * @return persisted slices
     */
    List<PlanSliceAggregate> saveAll(List<PlanSliceAggregate> slices);

    /**
     * Retrieve all slices for a plan.
     *
     * @param planId plan identifier
     * @return list of slices
     */
    List<PlanSliceAggregate> findByPlanId(Long planId);

    /**
     * Retrieve a slice by identifier.
     *
     * @param sliceId slice identifier
     * @return slice or {@link Optional#empty()}
     */
    Optional<PlanSliceAggregate> findById(Long sliceId);
}
