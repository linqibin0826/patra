package com.patra.storage.api.endpoint;

import com.patra.storage.api.dto.RecordUploadResponse;
import com.patra.storage.api.dto.UploadRecordRequest;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Internal API contract for storage metadata recording.
 *
 * <p>Exposes endpoints for recording file upload metadata to internal microservices via Feign
 * client integration.
 *
 * @author linqibin
 * @since 0.1.0
 */
public interface StorageEndpoint {

  String BASE_PATH = "/internal/storage";

  /**
   * Records upload metadata after the file has been stored in object storage.
   *
   * @param request upload payload
   * @return metadata identifier and recorded timestamp
   */
  @PostMapping(value = BASE_PATH + "/files/record", consumes = MediaType.APPLICATION_JSON_VALUE)
  RecordUploadResponse recordUpload(@RequestBody @Valid UploadRecordRequest request);
}
