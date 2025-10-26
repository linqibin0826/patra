package com.patra.storage.adapter.internal;

import com.patra.storage.api.dto.RecordUploadResponse;
import com.patra.storage.api.dto.UploadRecordRequest;
import com.patra.storage.api.endpoint.StorageEndpoint;
import com.patra.storage.app.recordupload.RecordUploadCommand;
import com.patra.storage.app.recordupload.RecordUploadOrchestrator;
import com.patra.storage.app.recordupload.RecordUploadResult;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.InetAddress;
import java.net.UnknownHostException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** REST controller implementing the internal storage metadata endpoint. */
@Slf4j
@Validated
@RestController
@RequiredArgsConstructor
public class StorageEndpointImpl implements StorageEndpoint {

  private final RecordUploadOrchestrator orchestrator;

  /**
   * Persists metadata describing a successfully uploaded object.
   *
   * @param request validated upload payload
   * @return 201 Created with metadata id
   */
  @Override
  public RecordUploadResponse recordUpload(@RequestBody @Valid UploadRecordRequest request) {
    HttpServletRequest httpRequest = getCurrentRequest();
    RecordUploadCommand command = buildCommand(request, httpRequest);
    RecordUploadResult result = orchestrator.execute(command);
    return new RecordUploadResponse(result.metadataId(), result.recordedAt());
  }

  private HttpServletRequest getCurrentRequest() {
    ServletRequestAttributes attributes =
        (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
    return attributes.getRequest();
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
