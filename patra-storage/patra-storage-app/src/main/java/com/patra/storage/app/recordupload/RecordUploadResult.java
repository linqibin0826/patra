package com.patra.storage.app.recordupload;

import java.time.Instant;

/** Result DTO returned after metadata persistence succeeds. */
public record RecordUploadResult(Long metadataId, Instant recordedAt) {}
