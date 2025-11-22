package com.patra.objectstorage.api.endpoint;

import com.patra.objectstorage.api.dto.RecordUploadResponse;
import com.patra.objectstorage.api.dto.UploadRecordRequest;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/// 存储元数据记录的内部 API 契约。
///
/// 通过 Feign 客户端集成向内部微服务暴露记录文件上传元数据的端点。
///
/// @author linqibin
/// @since 0.1.0
public interface StorageEndpoint {

  String BASE_PATH = "/internal/storage";

  /// 在文件存储到对象存储后记录上传元数据。
  ///
  /// @param request 上传有效负载
  /// @return 元数据标识符和记录时间戳
  @PostMapping(value = BASE_PATH + "/files/record", consumes = MediaType.APPLICATION_JSON_VALUE)
  RecordUploadResponse recordUpload(@RequestBody @Valid UploadRecordRequest request);
}
