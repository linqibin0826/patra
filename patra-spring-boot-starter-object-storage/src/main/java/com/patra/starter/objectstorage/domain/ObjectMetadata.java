package com.patra.starter.objectstorage.domain;

import java.util.Collections;
import java.util.Map;
import lombok.Builder;
import lombok.Getter;

/** 描述将要上传的对象的基本元数据。 */
@Getter
@Builder
public class ObjectMetadata {

  /** 内容长度(字节) */
  private final long contentLength;

  /** 内容类型,默认为二进制流 */
  @Builder.Default private final String contentType = "application/octet-stream";

  /** 用户自定义元数据 */
  @Builder.Default private final Map<String, String> userMetadata = Collections.emptyMap();
}
