package dev.patra.catalog.infra.portal.citation;

import dev.patra.catalog.domain.kernel.model.vo.PageRange;
import dev.patra.catalog.domain.kernel.model.vo.PublicationDate;
import dev.patra.catalog.domain.portal.model.read.CitationAuthor;
import dev.patra.catalog.domain.portal.model.read.CitationRecord;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/// BibTeX 写出器（spec 第 6.4 节）：UTF-8 不转 TeX 重音，值里的 TeX 特殊字符转义，标题双花括号保大小写。
///
/// @author linqibin
/// @since 0.1.0
final class BibtexExportWriter extends TextExportWriter {

  private static final String[] MONTHS = {
    "jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec"
  };

  /// 创建写出器。
  ///
  /// @param out 底层输出流
  BibtexExportWriter(OutputStream out) {
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

  /// 写一条：键为 `pmid<PMID>`，没有 PMID 用 `patra<id>`。
  ///
  /// @param r 记录
  /// @throws IOException 写出失败时
  private void writeRecord(CitationRecord r) throws IOException {
    out.write("@article{" + (isBlank(r.pmid()) ? "patra" + r.id() : "pmid" + r.pmid()) + ",\n");
    if (!r.authors().isEmpty()) {
      field(
          "author",
          r.authors().stream()
              .map(BibtexExportWriter::authorName)
              .collect(Collectors.joining(" and ")));
    }
    if (!isBlank(r.title())) {
      field("title", "{" + escape(r.title()) + "}");
    }
    field("journal", escape(r.journalTitle()));
    PublicationDate date = r.publishedDate();
    if (date != null) {
      field("year", String.valueOf(date.year()));
      if (date.month() != null) {
        out.write("  month = " + MONTHS[date.month() - 1] + ",\n");
      }
    }
    field("volume", escape(r.volume()));
    field("number", escape(r.issue()));
    Optional<PageRange> range = r.pagination().firstRange();
    if (range.isPresent()) {
      PageRange p = range.get();
      field("pages", escape(p.last() == null ? p.first() : p.first() + "--" + p.last()));
    }
    field("eid", escape(r.articleNumber()));
    field("doi", escape(r.doi()));
    field("pmid", escape(r.pmid()));
    field("pmcid", escape(r.pmcid()));
    out.write("}\n\n");
  }

  /// 写一个花括号字段，值为空时跳过。
  ///
  /// @param name 字段名
  /// @param value 已转义的值，可空
  /// @throws IOException 写出失败时
  private void field(String name, String value) throws IOException {
    if (isBlank(value)) {
      return;
    }
    out.write("  " + name + " = {" + value.replaceAll("\\R", " ").strip() + "},\n");
  }

  /// 作者：团体作者整体加一层花括号；个人作者 `Family, Given`。
  ///
  /// @param a 作者
  /// @return 已转义的作者名
  private static String authorName(CitationAuthor a) {
    if (!isBlank(a.collective())) {
      return "{" + escape(a.collective()) + "}";
    }
    String given = !isBlank(a.given()) ? a.given() : a.initials();
    if (isBlank(a.family())) {
      return escape(given);
    }
    return isBlank(given) ? escape(a.family()) : escape(a.family()) + ", " + escape(given);
  }

  /// 转义 TeX 特殊字符 `\ { } % & $ # _ ~ ^`。
  ///
  /// @param value 原值，可空
  /// @return 转义后的值；入参为 null 时为 null
  private static String escape(String value) {
    if (value == null) {
      return null;
    }
    StringBuilder sb = new StringBuilder(value.length());
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      switch (c) {
        case '\\' -> sb.append("\\textbackslash{}");
        case '~' -> sb.append("\\textasciitilde{}");
        case '^' -> sb.append("\\textasciicircum{}");
        case '{', '}', '%', '&', '$', '#', '_' -> sb.append('\\').append(c);
        default -> sb.append(c);
      }
    }
    return sb.toString();
  }
}
