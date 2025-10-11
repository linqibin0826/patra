package com.patra.ingest.app.usecase.execution.execute;

import com.patra.common.enums.ProvenanceCode;
import com.patra.ingest.domain.model.vo.Batch;
import com.patra.ingest.domain.model.vo.BatchResult;
import com.patra.ingest.domain.model.vo.ExecutionContext;

/**
 * Batch executor interface.
 * <p>
 * Responsibility: execute a single batch, call the data source API, process data, and upload to storage.
 * </p>
 * <p>
 * Design notes:
 * <ul>
 *   <li>Strategy: different provenanceCodes may have different execution strategies.</li>
 *   <li>Pipeline: API call → data cleansing → upload to storage → return result.</li>
 *   <li>Error handling: on failure, catch exceptions and return BatchResult.failure().</li>
 *   <li>Cursor support: on success, extract nextCursorToken from the response.</li>
 *   <li>Storage upload: use StorageAdapter to upload to object storage and return storageKey.</li>
 * </ul>
 * </p>
 * <p>
 * Implementations should be registered in BatchExecutorRegistry and routed by provenanceCode.
 * </p>
 *
 * TODO Add a PubMed implementation
 *
 * @author linqibin
 * @since 0.1.0
 */
public interface BatchExecutor {

    /**
     * Returns the supported provenance code.
     *
     * @return provenance code
     */
    ProvenanceCode getProvenanceCode();

    /**
     * Executes a batch.
     *
     * @param context execution context (config snapshot, window, etc.)
     * @param batch batch information (query, parameters, cursor)
     * @return batch result
     */
    BatchResult execute(ExecutionContext context, Batch batch);
}
