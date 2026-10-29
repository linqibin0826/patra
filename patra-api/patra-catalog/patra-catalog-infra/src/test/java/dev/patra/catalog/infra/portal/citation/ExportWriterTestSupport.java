package dev.patra.catalog.infra.portal.citation;

import dev.patra.catalog.domain.portal.model.read.CitationRecord;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Function;

/// 写出器测试共用：按批写入、读夹具。
///
/// @author linqibin
/// @since 0.1.0
final class ExportWriterTestSupport {

  private ExportWriterTestSupport() {}

  /// 用新写出器分批写入，返回全部字节。
  ///
  /// @param factory 由输出流创建写出器
  /// @param batches 每批的记录
  /// @return 写出的字节
  /// @throws IOException 写出失败时
  @SafeVarargs
  static byte[] write(
      Function<ByteArrayOutputStream, CitationExportWriter> factory,
      List<CitationRecord>... batches)
      throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    CitationExportWriter writer = factory.apply(out);
    writer.begin();
    for (List<CitationRecord> batch : batches) {
      writer.write(batch);
    }
    writer.end();
    return out.toByteArray();
  }

  /// 三篇样例分两批（第一篇、后两篇）写入。
  ///
  /// @param factory 由输出流创建写出器
  /// @return 写出的字节
  /// @throws IOException 写出失败时
  static byte[] writeSamplesInTwoBatches(
      Function<ByteArrayOutputStream, CitationExportWriter> factory) throws IOException {
    return write(
        factory,
        List.of(CitationRecordFixture.full()),
        List.of(
            CitationRecordFixture.yearOnlyWithArticleNumber(), CitationRecordFixture.yearMonth()));
  }

  /// 读导出夹具的原始字节。
  ///
  /// @param name 文件名
  /// @return 字节
  /// @throws IOException 读取失败时
  static byte[] fixture(String name) throws IOException {
    try (InputStream in =
        ExportWriterTestSupport.class.getResourceAsStream("/citation/export/" + name)) {
      return in.readAllBytes();
    }
  }

  /// 按 UTF-8 解码。
  ///
  /// @param bytes 字节
  /// @return 文本
  static String text(byte[] bytes) {
    return new String(bytes, StandardCharsets.UTF_8);
  }
}
