package dev.patra.catalog.infra.portal.citation;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

/// 文本格式写出器的基类：UTF-8 缓冲写出，`end` 时刷出，不关闭底层输出流。
///
/// @author linqibin
/// @since 0.1.0
abstract class TextExportWriter implements CitationExportWriter {

  /// UTF-8 缓冲写出器。
  protected final Writer out;

  /// 创建写出器。
  ///
  /// @param out 底层输出流
  protected TextExportWriter(OutputStream out) {
    this.out = new BufferedWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));
  }

  /// 默认没有文件头。
  ///
  /// @throws IOException 写出失败时
  @Override
  public void begin() throws IOException {}

  /// 刷出缓冲。
  ///
  /// @throws IOException 写出失败时
  @Override
  public void flush() throws IOException {
    out.flush();
  }

  /// 默认没有文件尾，只刷出缓冲。
  ///
  /// @throws IOException 写出失败时
  @Override
  public void end() throws IOException {
    out.flush();
  }

  /// 值是否为空或只含空白。
  ///
  /// @param value 值，可空
  /// @return 是否空白
  protected static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
