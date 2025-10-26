package com.patra.storage.api;

import jakarta.validation.Valid;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/** Feign contract for the internal storage metadata service. */
@Validated
@FeignClient(name = "patra-storage", path = "/internal/storage")
public interface StorageClient {

  /**
   * Records upload metadata after the file has been stored in object storage.
   *
   * @param request upload payload
   * @return metadata identifier and recorded timestamp
   */
  @PostMapping(value = "/files/record", consumes = MediaType.APPLICATION_JSON_VALUE)
  RecordUploadResponse recordUpload(@RequestBody @Valid UploadRecordRequest request);
}
