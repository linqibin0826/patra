package dev.patra.catalog.infra.portal.citation;

import dev.patra.catalog.domain.portal.model.enums.ExportFormat;
import dev.patra.catalog.domain.portal.model.read.CitationRecord;
import dev.patra.catalog.domain.portal.port.citation.CitationExportPort;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Iterator;
import java.util.List;
import org.springframework.stereotype.Component;

/// 引用导出端口适配器：按格式创建有状态的写出器，逐批拉取并写出。
///
/// @author linqibin
/// @since 0.1.0
@Component
public class CitationExportAdapter implements CitationExportPort {

  /// 导出：文件头一次 → 每个非空批写一次并刷出 → 文件尾一次。
  ///
  /// @param format 导出格式
  /// @param batches 按输出顺序的记录批次（懒加载）
  /// @param out 输出流（不关闭）
  /// @throws IOException 写出失败时
  @Override
  public void export(ExportFormat format, Iterator<List<CitationRecord>> batches, OutputStream out)
      throws IOException {
    CitationExportWriter writer = open(format, out);
    writer.begin();
    while (batches.hasNext()) {
      List<CitationRecord> batch = batches.next();
      if (!batch.isEmpty()) {
        writer.write(batch);
        writer.flush();
      }
    }
    writer.end();
  }

  /// 按格式创建写出器。
  ///
  /// @param format 导出格式
  /// @param out 输出流
  /// @return 写出器
  private static CitationExportWriter open(ExportFormat format, OutputStream out) {
    return switch (format) {
      case RIS -> new RisExportWriter(out);
      case BIBTEX -> new BibtexExportWriter(out);
      case CSV -> new CsvExportWriter(out);
      case CSL_JSON -> new CslJsonExportWriter(out);
    };
  }
}
