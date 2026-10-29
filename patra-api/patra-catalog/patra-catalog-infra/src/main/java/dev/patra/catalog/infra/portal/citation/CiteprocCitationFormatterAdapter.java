package dev.patra.catalog.infra.portal.citation;

import de.undercouch.citeproc.CSL;
import de.undercouch.citeproc.ListItemDataProvider;
import de.undercouch.citeproc.csl.CSLItemData;
import dev.patra.catalog.domain.portal.model.enums.CitationStyle;
import dev.patra.catalog.domain.portal.model.read.CitationEntry;
import dev.patra.catalog.domain.portal.model.read.CitationRecord;
import dev.patra.catalog.domain.portal.port.citation.CitationFormatterPort;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/// 用 citeproc-java 与官方 CSL 样式渲染引用（spec 第 7.2 节）。
///
/// `CSL` 有状态、不是线程安全的，每次调用新建；渲染两遍：HTML 取 `csl-left-margin` 作为序号，
/// 纯文本去掉开头的序号作为正文。全部用 `en-US` locale（GB/T 7714 西文文献用 et al.，见 spec 第 7.3 节）。
///
/// @author linqibin
/// @since 0.1.0
@Component
public class CiteprocCitationFormatterAdapter implements CitationFormatterPort {

  private static final String LOCALE = "en-US";
  private static final Pattern LEFT_MARGIN =
      Pattern.compile("<div class=\"csl-left-margin\">(.*?)</div>", Pattern.DOTALL);

  /// 渲染一组引用。
  ///
  /// @param style 样式
  /// @param records 引用记录，按请求顺序
  /// @return 引用条目；编号样式按入参顺序，APA 按样式顺序
  @Override
  public List<CitationEntry> format(CitationStyle style, List<CitationRecord> records) {
    if (records.isEmpty()) {
      return List.of();
    }
    CSLItemData[] items = records.stream().map(CslItemMapper::toCsl).toArray(CSLItemData[]::new);
    List<String> html = render(style, items, "html");
    List<String> text = render(style, items, "text");
    List<CitationEntry> entries = new ArrayList<>(text.size());
    for (int i = 0; i < text.size(); i++) {
      Matcher m = LEFT_MARGIN.matcher(html.get(i));
      String label = m.find() ? m.group(1).strip() : null;
      String body = text.get(i).strip();
      if (label != null && body.startsWith(label)) {
        body = body.substring(label.length()).strip();
      }
      entries.add(CitationEntry.of(label, body));
    }
    return List.copyOf(entries);
  }

  /// 用一个新的 `CSL` 实例按指定输出格式渲染参考文献表。
  ///
  /// @param style 样式
  /// @param items CSL 条目，按请求顺序注册
  /// @param outputFormat `html` 或 `text`
  /// @return 每条一项
  private static List<String> render(
      CitationStyle style, CSLItemData[] items, String outputFormat) {
    try {
      CSL csl = new CSL(new ListItemDataProvider(items), styleId(style), LOCALE);
      csl.setOutputFormat(outputFormat);
      csl.registerCitationItems(
          Arrays.stream(items).map(CSLItemData::getId).toArray(String[]::new));
      return Arrays.asList(csl.makeBibliography().getEntries());
    } catch (IOException e) {
      throw new UncheckedIOException("加载 CSL 样式失败：" + styleId(style), e);
    }
  }

  /// 样式对应的官方 CSL 样式文件名。
  ///
  /// @param style 样式
  /// @return CSL 样式 ID
  private static String styleId(CitationStyle style) {
    return switch (style) {
      case GB_T_7714_2015 -> "china-national-standard-gb-t-7714-2015-numeric";
      case VANCOUVER -> "nlm-citation-sequence";
      case APA_7 -> "apa";
      case AMA_11 -> "american-medical-association";
    };
  }
}
