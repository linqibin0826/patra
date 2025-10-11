package com.patra.ingest.app.usecase.plan.slicer;

import com.patra.ingest.app.usecase.plan.slicer.model.SlicePlan;
import com.patra.ingest.app.usecase.plan.slicer.model.SlicePlanningContext;
import com.patra.ingest.domain.model.enums.SliceStrategy;
import java.util.List;

/**
 * Slice planning strategy interface.
 * <p>Defines common capabilities for different slicing strategies, including the
 * strategy code/enum and the logic to break a window into slices based on context.</p>
 *
 * <p>Implementations must ensure:
 * <ul>
 *   <li>code is unique so the strategy can be located from a registry;</li>
 *   <li>the returned slices are sorted and sliceNo starts from 1 and increments by 1;</li>
 *   <li>return an empty collection when slicing is not possible; the caller will handle it.</li>
 * </ul></p>
 *
 * @author linqibin
 * @since 0.1.0
 */
public interface SlicePlanner {

    /**
     * Return the strategy identifier, typically aligned with the configured strategy code.
     *
     * @return strategy enum
     */
    SliceStrategy code();

    /**
     * Split the planning window into ordered slices using the provided context.
     *
     * @param context slicing context including window, expressions, and configuration snapshot
     * @return ordered list of slices; empty when slicing is not possible
     */
    List<SlicePlan> slice(SlicePlanningContext context);
}
