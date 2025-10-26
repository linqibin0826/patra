package com.patra.storage.adapter.internal;

import com.patra.storage.api.RecordUploadResponse;
import com.patra.storage.api.UploadRecordRequest;
import com.patra.storage.app.recordupload.RecordUploadCommand;
import com.patra.storage.app.recordupload.RecordUploadOrchestrator;
import com.patra.storage.app.recordupload.RecordUploadResult;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.InetAddress;
import java.net.UnknownHostException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** REST controller exposing the internal storage metadata endpoint. */
@Slf4j
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/storage")
public class StorageEndpointImpl {

  private final RecordUploadOrchestrator orchestrator;

  /**
   * Persists metadata describing a successfully uploaded object.
   *
   * @param request validated upload payload
   * @param httpRequest servlet request (for IP capture)
   * @return 201 Created with metadata id
   */
  @PostMapping("/files/record")
  public ResponseEntity<RecordUploadResponse> recordUpload(
      @RequestBody @Valid UploadRecordRequest request, HttpServletRequest httpRequest) {
    RecordUploadCommand command = buildCommand(request, httpRequest);
    RecordUploadResult result = orchestrator.execute(command);
    RecordUploadResponse response =
        new RecordUploadResponse(result.metadataId(), result.recordedAt());
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  private RecordUploadCommand buildCommand(
      UploadRecordRequest request, HttpServletRequest httpRequest) {
    return new RecordUploadCommand(
        request.bucketName(),
        request.objectKey(),
        request.fileSize(),
        request.contentType(),
        request.md5Hash(),
        request.sha256Hash(),
        request.serviceName(),
        request.businessType(),
        request.businessId(),
        request.correlationData(),
        request.providerType(),
        request.expiresAt(),
        resolveIp(httpRequest),
        request.recordRemarks());
  }

  private byte[] resolveIp(HttpServletRequest request) {
    String header = request.getHeader("X-Forwarded-For");
    String source = header != null ? header.split(",")[0].trim() : request.getRemoteAddr();
    if (source == null || source.isBlank()) {
      return null;
    }
    try {
      return InetAddress.getByName(source).getAddress();
    } catch (UnknownHostException ex) {
      log.warn("Unable to parse client ip address: {}", source, ex);
      return null;
    }
  }
}
