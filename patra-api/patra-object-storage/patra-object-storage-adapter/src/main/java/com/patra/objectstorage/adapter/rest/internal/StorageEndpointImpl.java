package com.patra.objectstorage.adapter.rest.internal;

import com.patra.objectstorage.api.dto.RecordUploadResponse;
import com.patra.objectstorage.api.dto.UploadRecordRequest;
import com.patra.objectstorage.api.endpoint.StorageEndpoint;
import com.patra.objectstorage.app.recordupload.RecordUploadCommand;
import com.patra.objectstorage.app.recordupload.RecordUploadOrchestrator;
import com.patra.objectstorage.app.recordupload.RecordUploadResult;
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

/**
 * 存储端点实现。
 *
 * <p>实现内部存储元数据端点的REST控制器,作为适配器层的入站适配器, 接收来自其他微服务的Feign客户端请求,并将其转换为应用层的用例调用。
 *
 * <p>职责:
 *
 * <ul>
 *   <li>实现API契约({@link StorageEndpoint})
 *   <li>验证请求DTO
 *   <li>提取HTTP上下文信息(如客户端IP)
 *   <li>构建应用层命令对象
 *   <li>委托给编排器执行用例
 *   <li>转换结果为响应DTO
 * </ul>
 */
@Slf4j
@Validated
@RestController
@RequiredArgsConstructor
public class StorageEndpointImpl implements StorageEndpoint {

  private final RecordUploadOrchestrator orchestrator;

  /**
   * 持久化描述成功上传对象的元数据。
   *
   * <p>接收来自其他微服务的文件上传记录请求,提取HTTP请求上下文, 构建命令对象并委托给编排器执行持久化操作。
   *
   * @param request 已验证的上传载荷
   * @return 201 Created,包含元数据ID
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
