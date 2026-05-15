package com.patra.starter.objectstorage.domain;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;

/// 下载操作的结果。
///
/// 此类封装了从对象存储下载的内容及其元数据。实现 {@link Closeable} 接口，
/// 调用者必须负责关闭返回的流，推荐使用 try-with-resources 语法。
///
/// **使用示例:**
///
/// ```java
/// try (DownloadResult result = objectStorage.download("bucket", "key")) {
///     InputStream content = result.getContent();
///     // 处理内容...
/// }
/// ```
@Getter
@Builder
@EqualsAndHashCode(exclude = "content")
public class DownloadResult implements Closeable {

  /// 对象内容流。
  ///
  /// 调用者必须负责关闭此流，或者调用 {@link #close()} 方法。
  private final InputStream content;

  /// 存储桶名称。
  private final String bucketName;

  /// 对象键。
  private final String objectKey;

  /// 内容长度（字节）。
  private final long contentLength;

  /// 内容类型（MIME 类型）。
  private final String contentType;

  /// 对象的 ETag 标识。
  private final String etag;

  /// 关闭底层输入流。
  ///
  /// @throws IOException 如果关闭流时发生 I/O 错误
  @Override
  public void close() throws IOException {
    if (content != null) {
      content.close();
    }
  }
}
