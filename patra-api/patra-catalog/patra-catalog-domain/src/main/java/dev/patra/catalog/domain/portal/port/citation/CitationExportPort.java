package dev.patra.catalog.domain.portal.port.citation;

import dev.patra.catalog.domain.portal.model.enums.ExportFormat;
import dev.patra.catalog.domain.portal.model.read.CitationRecord;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Iterator;
import java.util.List;

/// 引用导出端口：把逐批产生的引用记录写成指定格式。
///
/// @author linqibin
/// @since 0.1.0
public interface CitationExportPort {

  /// 导出。
  ///
  /// 批由调用方懒加载：实现逐批调用 `next()`、写完一批再取下一批，内存里只有当前一批；
  /// 文件头尾各写一次，一批都没有时仍输出合法的空文件（CSV 只有表头、CSL-JSON 为空数组）。
  ///
  /// @param format 导出格式
  /// @param batches 按输出顺序的记录批次
  /// @param out 输出流（不关闭）
  /// @throws IOException 写出失败时（例如客户端断开）
  void export(ExportFormat format, Iterator<List<CitationRecord>> batches, OutputStream out)
      throws IOException;
}
