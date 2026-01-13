package com.patra.objectstorage.api.endpoint;

import com.patra.objectstorage.api.dto.RecordUploadResponse;
import com.patra.objectstorage.api.dto.UploadRecordRequest;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

/// 存储元数据记录的内部 API 契约。
///
/// 此接口同时用于：
///
/// - **服务端**：Controller 实现此接口，Spring MVC 自动识别 `@HttpExchange` 注解
/// - **客户端**：通过 HTTP Interface 代理调用远程服务
///
/// @author linqibin
/// @since 0.1.0
@HttpExchange(
    url = "/_internal/storage",
    accept = "application/json",
    contentType = "application/json")
public interface StorageEndpoint {

  /// 在文件存储到对象存储后记录上传元数据。
  ///
  /// @param request 上传有效负载
  /// @return 元数据标识符和记录时间戳
  @PostExchange("/files/record")
  RecordUploadResponse recordUpload(@RequestBody UploadRecordRequest request);
}
