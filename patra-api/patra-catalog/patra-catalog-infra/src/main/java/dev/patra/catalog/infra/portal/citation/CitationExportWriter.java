package dev.patra.catalog.infra.portal.citation;

import dev.patra.catalog.domain.portal.model.read.CitationRecord;
import java.io.IOException;
import java.util.List;

/// 有状态的导出写出器（[CitationExportAdapter] 内部使用）：`begin` 一次、`write` 每批一次、`end` 一次；不关闭底层输出流。
///
/// @author linqibin
/// @since 0.1.0
interface CitationExportWriter {

  /// 写文件头（CSV 的 BOM 与表头、JSON 的左括号等）。
  ///
  /// @throws IOException 写出失败时
  void begin() throws IOException;

  /// 写一批记录。
  ///
  /// @param records 记录，按输出顺序
  /// @throws IOException 写出失败时
  void write(List<CitationRecord> records) throws IOException;

  /// 把已写的内容刷到底层输出流（每批写完调用，让客户端尽早收到字节）。
  ///
  /// @throws IOException 写出失败时
  void flush() throws IOException;

  /// 写文件尾并刷出缓冲。
  ///
  /// @throws IOException 写出失败时
  void end() throws IOException;
}
