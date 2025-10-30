/**
 * patra-storage service and object storage integration adapters.
 *
 * <p>This package contains adapters for integrating with:
 *
 * <ul>
 *   <li><strong>patra-storage</strong> service - Metadata recording service
 *   <li><strong>Object storage</strong> - S3/MinIO for literature JSON file storage
 * </ul>
 *
 * <p>Key components:
 *
 * <ul>
 *   <li>{@link com.patra.ingest.infra.integration.storage.LiteratureStorageAdapter} - Implements
 *       {@link com.patra.ingest.domain.port.LiteratureStoragePort} for storing literature to object
 *       storage (S3/MinIO)
 *   <li>{@link com.patra.ingest.infra.integration.storage.StorageMetadataAdapter} - Implements
 *       {@link com.patra.ingest.domain.port.StorageMetadataPort} for recording metadata via
 *       patra-storage service
 *   <li>{@code acl/} - Anti-Corruption Layer (ACL) for converting domain models to catalog API DTOs
 * </ul>
 *
 * @since 0.1.0
 */
package com.patra.ingest.infra.integration.storage;
