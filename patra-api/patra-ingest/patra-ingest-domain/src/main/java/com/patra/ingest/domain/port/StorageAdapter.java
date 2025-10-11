package com.patra.ingest.domain.port;

import com.patra.ingest.domain.model.vo.StorageUploadRequest;
import com.patra.ingest.domain.model.vo.StorageUploadResult;

/**
 * Port for interacting with object storage (MinIO/S3).
 * <p>Provides upload capabilities and standardized object path generation.</p>
 *
 * @author linqibin
 * @since 0.1.0
 */
public interface StorageAdapter {

    /**
     * Upload data to object storage.
     *
     * @param request upload request payload
     * @return result containing object path, size, and metadata
     */
    StorageUploadResult upload(StorageUploadRequest request);

    /**
     * Generate an object storage path.
     * <p>Pattern: {@code {bucket}/{provenanceCode-lower}/{yyyy}/{MM}/run_{runId}/batch_{batchNo(000)}.json.gz}</p>
     *
     * @param provenanceCode provenance code
     * @param runId          run identifier
     * @param batchNo        batch number
     * @return object key (bucket excluded)
     */
    String generateObjectPath(String provenanceCode, Long runId, int batchNo);
}
