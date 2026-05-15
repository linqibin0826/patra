package dev.linqibin.patra.objectstorage.adapter.rest.internal;

import dev.linqibin.commons.cqrs.CommandBus;
import dev.linqibin.patra.objectstorage.api.dto.RecordUploadResponse;
import dev.linqibin.patra.objectstorage.api.dto.UploadRecordRequest;
import dev.linqibin.patra.objectstorage.api.endpoint.StorageEndpoint;
import dev.linqibin.patra.objectstorage.app.recordupload.RecordUploadCommand;
import dev.linqibin.patra.objectstorage.app.recordupload.RecordUploadResult;
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

/// 存储端点实现。
///
/// 实现内部存储元数据端点的REST控制器,作为适配器层的入站适配器, 接收来自其他微服务的 HTTP Interface 客户端请求,并将其转换为应用层的用例调用。
///
/// 职责:
///
/// - 实现API契约({@link StorageEndpoint})
///   - 验证请求DTO
///   - 提取HTTP上下文信息(如客户端IP)
///   - 构建应用层命令对象
///   - 通过 CommandBus 分发命令
///   - 转换结果为响应DTO
///
@Slf4j
@Validated
@RestController
@RequiredArgsConstructor
public class StorageEndpointImpl implements StorageEndpoint {

  private final CommandBus commandBus;

  /// 持久化描述成功上传对象的元数据。
  ///
  /// 接收来自其他微服务的文件上传记录请求,提取HTTP请求上下文, 构建命令对象并通过 CommandBus 分发执行。
  ///
  /// @param request 已验证的上传载荷
  /// @return 201 Created,包含元数据ID
  @Override
  public RecordUploadResponse recordUpload(@RequestBody @Valid UploadRecordRequest request) {
    HttpServletRequest httpRequest = getCurrentRequest();
    RecordUploadCommand command = buildCommand(request, httpRequest);
    RecordUploadResult result = commandBus.handle(command);
    return new RecordUploadResponse(result.metadataId(), result.recordedAt());
  }

  /// 获取当前HTTP请求对象。
  ///
  /// @return 当前请求的 HttpServletRequest
  private HttpServletRequest getCurrentRequest() {
    ServletRequestAttributes attributes =
        (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
    return attributes.getRequest();
  }

  /// 构建记录上传命令。
  ///
  /// @param request 上传记录请求
  /// @param httpRequest HTTP请求对象
  /// @return 记录上传命令
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

  /// 解析客户端IP地址。
  ///
  /// @param request HTTP请求对象
  /// @return IP地址的字节数组表示,解析失败时返回null
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
