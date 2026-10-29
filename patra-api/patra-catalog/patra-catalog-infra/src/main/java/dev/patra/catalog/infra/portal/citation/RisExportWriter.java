package dev.patra.catalog.infra.portal.citation;

import dev.patra.catalog.domain.kernel.model.vo.PageRange;
import dev.patra.catalog.domain.kernel.model.vo.PublicationDate;
import dev.patra.catalog.domain.portal.model.read.CitationAuthor;
import dev.patra.catalog.domain.portal.model.read.CitationRecord;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Optional;

/// RIS 写出器（spec 第 6.4 节）：行尾 CRLF，每条 `TY  - JOUR` 开始、`ER  - ` 结束，条目之间空一行。
///
/// @author linqibin
/// @since 0.1.0
final class RisExportWriter extends TextExportWriter {

  private static final String CRLF = "\r\n";

  /// 创建写出器。
  ///
  /// @param out 底层输出流
  RisExportWriter(OutputStream out) {
    super(out);
  }

  /// 写一批记录。
  ///
  /// @param records 记录
  /// @throws IOException 写出失败时
  @Override
  public void write(List<CitationRecord> records) throws IOException {
    for (CitationRecord record : records) {
      writeRecord(record);
    }
  }

  /// 写一条。
  ///
  /// @param r 记录
  /// @throws IOException 写出失败时
  private void writeRecord(CitationRecord r) throws IOException {
    tag("TY", "JOUR");
    for (CitationAuthor author : r.authors()) {
      tag("AU", authorName(author));
    }
    tag("TI", r.title());
    tag("T2", r.journalTitle());
    tag("J2", r.journalAbbreviation());
    tag("VL", r.volume());
    tag("IS", r.issue());
    Optional<PageRange> range = r.pagination().firstRange();
    if (range.isPresent()) {
      tag("SP", range.get().first());
      tag("EP", range.get().last());
    } else {
      tag("SP", r.articleNumber());
    }
    tag("C7", r.articleNumber());
    PublicationDate date = r.publishedDate();
    if (date != null) {
      tag("PY", String.valueOf(date.year()));
      tag("DA", da(date));
    }
    tag("DO", r.doi());
    tag("AN", r.pmid());
    if (!isBlank(r.pmid())) {
      tag("M2", "PMID: " + r.pmid());
    }
    tag("C2", r.pmcid());
    if (!isBlank(r.pmid())) {
      tag("UR", "https://pubmed.ncbi.nlm.nih.gov/" + r.pmid() + "/");
    }
    out.write("ER  - " + CRLF + CRLF);
  }

  /// 写一个标签行，值为空时跳过；值里的换行折成空格。
  ///
  /// @param tag 标签
  /// @param value 值，可空
  /// @throws IOException 写出失败时
  private void tag(String tag, String value) throws IOException {
    if (isBlank(value)) {
      return;
    }
    out.write(tag + "  - " + value.replaceAll("\\R", " ").strip() + CRLF);
  }

  /// 作者：团体作者原文；个人作者 `Family, Given`，没有名时用缩写。
  ///
  /// @param a 作者
  /// @return 作者名，可空
  private static String authorName(CitationAuthor a) {
    if (!isBlank(a.collective())) {
      return a.collective();
    }
    String given = !isBlank(a.given()) ? a.given() : a.initials();
    if (isBlank(a.family())) {
      return given;
    }
    return isBlank(given) ? a.family() : a.family() + ", " + given;
  }

  /// `DA` 标签：`YYYY/MM/DD/`，缺的部分留空。
  ///
  /// @param d 出版日期
  /// @return `2021/10/14/`、`2022/10//` 或 `2023///`
  private static String da(PublicationDate d) {
    String month = d.month() == null ? "" : String.format("%02d", d.month());
    String day = d.day() == null ? "" : String.format("%02d", d.day());
    return String.format("%04d/%s/%s/", d.year(), month, day);
  }
}
