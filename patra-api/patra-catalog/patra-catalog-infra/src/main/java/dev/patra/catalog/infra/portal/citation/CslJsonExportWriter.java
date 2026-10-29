package dev.patra.catalog.infra.portal.citation;

import de.undercouch.citeproc.helper.json.StringJsonBuilderFactory;
import dev.patra.catalog.domain.portal.model.read.CitationRecord;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

/// CSL-JSON 写出器（spec 第 6.4 节）：跨批写成一个 JSON 数组，每项由 [CslItemMapper] 生成、
/// citeproc-java 的 `toJson` 输出。
///
/// @author linqibin
/// @since 0.1.0
final class CslJsonExportWriter extends TextExportWriter {

  private final StringJsonBuilderFactory jsonFactory = new StringJsonBuilderFactory();
  private int written;

  /// 创建写出器。
  ///
  /// @param out 底层输出流
  CslJsonExportWriter(OutputStream out) {
    super(out);
  }

  /// 写左括号。
  ///
  /// @throws IOException 写出失败时
  @Override
  public void begin() throws IOException {
    out.write("[");
  }

  /// 写一批，项之间用逗号分隔。
  ///
  /// @param records 记录
  /// @throws IOException 写出失败时
  @Override
  public void write(List<CitationRecord> records) throws IOException {
    for (CitationRecord r : records) {
      out.write(written == 0 ? "\n" : ",\n");
      out.write(String.valueOf(CslItemMapper.toCsl(r).toJson(jsonFactory.createJsonBuilder())));
      written++;
    }
  }

  /// 写右括号并刷出。
  ///
  /// @throws IOException 写出失败时
  @Override
  public void end() throws IOException {
    out.write(written == 0 ? "]\n" : "\n]\n");
    super.end();
  }
}
