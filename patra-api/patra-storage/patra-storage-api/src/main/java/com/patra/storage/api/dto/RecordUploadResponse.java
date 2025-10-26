package com.patra.storage.api.dto;

import java.time.Instant;

/** Response returned by the storage service when metadata persistence completes. */
public record RecordUploadResponse(Long metadataId, Instant recordedAt) {}
