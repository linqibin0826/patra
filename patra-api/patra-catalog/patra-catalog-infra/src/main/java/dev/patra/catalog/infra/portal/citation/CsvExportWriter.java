package dev.patra.catalog.infra.portal.citation;

import dev.patra.catalog.domain.kernel.model.vo.PageRange;
import dev.patra.catalog.domain.kernel.model.vo.PublicationDate;
import dev.patra.catalog.domain.portal.model.read.CitationAuthor;
import dev.patra.catalog.domain.portal.model.read.CitationRecord;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/// CSV 写出器（spec 第 6.4 节，RFC 4180）：UTF-8 带 BOM 以便 Excel 打开中文，CRLF；表头只在 `begin` 写一次。
///
/// @author linqibin
/// @since 0.1.0
final class CsvExportWriter extends TextExportWriter {

  private static final String HEADER =
      "id,pmid,pmcid,doi,title,authors,journal,journal_abbreviation,published_date,year,volume,"
          + "issue,pages,article_number";

  /// 创建写出器。
  ///
  /// @param out 底层输出流
  CsvExportWriter(OutputStream out) {
    super(out);
  }

  /// 写 BOM 与表头。
  ///
  /// @throws IOException 写出失败时
  @Override
  public void begin() throws IOException {
    out.write('﻿');
    out.write(HEADER + "\r\n");
  }

  /// 写一批记录，每条一行。
  ///
  /// @param records 记录
  /// @throws IOException 写出失败时
  @Override
  public void write(List<CitationRecord> records) throws IOException {
    for (CitationRecord r : records) {
      PublicationDate date = r.publishedDate();
      String row =
          Stream.of(
                  String.valueOf(r.id()),
                  r.pmid(),
                  r.pmcid(),
                  r.doi(),
                  r.title(),
                  r.authors().stream()
                      .map(CsvExportWriter::authorName)
                      .filter(n -> !isBlank(n))
                      .collect(Collectors.joining("; ")),
                  r.journalTitle(),
                  r.journalAbbreviation(),
                  date == null ? null : date.toIsoString(),
                  date == null ? null : String.valueOf(date.year()),
                  r.volume(),
                  r.issue(),
                  r.pagination().firstRange().map(CsvExportWriter::pages).orElse(null),
                  r.articleNumber())
              .map(CsvExportWriter::quote)
              .collect(Collectors.joining(","));
      out.write(row + "\r\n");
    }
  }

  /// 作者：团体作者原文；个人作者 `Family Given`，没有名时用缩写。
  ///
  /// @param a 作者
  /// @return 作者名，可空
  private static String authorName(CitationAuthor a) {
    if (!isBlank(a.collective())) {
      return a.collective();
    }
    String given = !isBlank(a.given()) ? a.given() : a.initials();
    return Stream.of(a.family(), given).filter(s -> !isBlank(s)).collect(Collectors.joining(" "));
  }

  /// 页码：`首页-尾页`，没有尾页时只有首页。
  ///
  /// @param p 页码段
  /// @return 页码
  private static String pages(PageRange p) {
    return p.last() == null ? p.first() : p.first() + "-" + p.last();
  }

  /// RFC 4180 字段：含逗号、引号、回车或换行时加引号，引号翻倍；null 为空字段。
  ///
  /// @param value 值，可空
  /// @return 字段
  private static String quote(String value) {
    if (value == null) {
      return "";
    }
    if (value.indexOf(',') >= 0
        || value.indexOf('"') >= 0
        || value.indexOf('\n') >= 0
        || value.indexOf('\r') >= 0) {
      return "\"" + value.replace("\"", "\"\"") + "\"";
    }
    return value;
  }
}
