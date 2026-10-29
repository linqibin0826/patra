package dev.patra.catalog.domain.fulltext.model.vo;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;

/// 打开的全文文件：内容流加元信息，用完必须关闭。
///
/// @param content 内容流
/// @param info 元信息
/// @author linqibin
/// @since 0.11.0
public record StoredFile(InputStream content, StoredFileInfo info) implements Closeable {

  /// 创建打开的文件。
  ///
  /// @param content 内容流
  /// @param info 元信息
  /// @return 打开的文件
  public static StoredFile of(InputStream content, StoredFileInfo info) {
    return new StoredFile(content, info);
  }

  /// 关闭内容流。
  ///
  /// @throws IOException 关闭失败时抛出
  @Override
  public void close() throws IOException {
    content.close();
  }
}
