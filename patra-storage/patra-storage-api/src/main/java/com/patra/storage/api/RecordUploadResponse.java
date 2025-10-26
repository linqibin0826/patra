package com.patra.storage.api;

import java.time.Instant;

/** Response returned by the storage service when metadata persistence completes. */
public record RecordUploadResponse(Long metadataId, Instant recordedAt) {}
