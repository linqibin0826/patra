package com.patra.catalog.infra.adapter.integration.letpub;

import com.patra.catalog.domain.port.enrichment.LetPubVenueData;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

/// LetPub 详情页 HTML 解析器。
///
/// 从 LetPub 期刊详情页 HTML 中提取全部字段，使用 Jsoup DOM 解析替代正则表达式，
/// 提高对 LetPub 前端变更的容错性。
///
/// **设计原则**：
///
/// - 优先使用 Jsoup CSS 选择器和 DOM 导航
/// - 仅在解析 JavaScript 嵌入数据（ECharts）时使用正则
/// - 一次扫描主表格构建字段映射表，按关键词 O(1) 查找
///
/// **解析策略**：
///
/// | 区域 | 策略 | 说明 |
/// |------|------|------|
/// | 期刊名 | Jsoup h1 过滤 | 跳过 `display:none` 的品牌/装饰 h1 |
/// | 基本信息 | fieldMap 文本提取 | label → 下一个兄弟 td |
/// | JCR 分区 | Jsoup 嵌套表格导航 | JIF + JCI 两个子表格 |
/// | CAS 分区 | 优先级选择 + 可见 span | 新锐版 > 升级版 > 旧版 |
/// | IF 趋势 | 正则解析 JS | ECharts `showecharts_if_trend()` 函数 |
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
public class LetPubDetailPageParser {

  // ========== 正则常量（热路径重复使用，提升为 static final 避免重复编译） ==========

  /// `display:none` 样式检测正则。
  private static final Pattern DISPLAY_NONE = Pattern.compile("(?i)display\\s*:\\s*none");

  /// CAS 日期提取正则（从标签文本中提取"2026年3月"格式日期）。
  private static final Pattern CAS_DATE = Pattern.compile("(20\\d{2}年\\d{1,2}月)");

  /// 括号内容提取正则（兼容全角/半角括号）。
  private static final Pattern PARENS_CONTENT = Pattern.compile("[（(]\\s*(.+?)\\s*[）)]");

  /// 审稿速度 - 期刊官网数据段。
  private static final Pattern REVIEW_SPEED_OFFICIAL = Pattern.compile("期刊官网数据：(.*?)(?:网友|$)");

  /// 审稿速度 - 网友分享经验段。
  private static final Pattern REVIEW_SPEED_USER = Pattern.compile("网友分享经验：\\s*(.*?)$");

  /// 录用比例百分比。
  private static final Pattern ACCEPTANCE_PERCENT = Pattern.compile("([\\d.]+%)");

  /// IF 趋势 - ECharts xAxis.data 数组。
  private static final Pattern IF_TREND_YEAR_ARRAY =
      Pattern.compile("xAxis.*?data\\s*:\\s*\\[(.*?)]", Pattern.DOTALL);

  /// IF 趋势 - ECharts series.data 数值数组。
  private static final Pattern IF_TREND_VALUE_ARRAY =
      Pattern.compile("series.*?data\\s*:\\s*\\[([\\d.,\\s]+)]", Pattern.DOTALL);

  /// 单引号字符串提取。
  private static final Pattern QUOTED_STRING = Pattern.compile("'([^']*)'");

  // ========== 业务标签常量（LetPub 页面固定结构） ==========

  /// 期刊信息主表格的表头文本（定位主表格用）。
  private static final String LABEL_BASIC_INFO = "基本信息";

  /// 预警名单行标签关键字（需从 CAS 扫描中排除）。
  private static final String LABEL_WARNING = "预警";

  /// CAS 新锐版分区表标签关键字。
  private static final String LABEL_CAS_PIONEER = "新锐期刊分区表";

  /// CAS 常规分区表标签关键字（升级版/基础版/旧版等）。
  private static final String LABEL_CAS_STANDARD = "期刊分区表";

  /// CAS 新锐版的版本标识后缀。
  private static final String EDITION_PIONEER = "新锐版";

  // ========== 主入口 ==========

  /// 解析详情页 HTML，提取全部期刊评价字段。
  ///
  /// @param html 详情页完整 HTML
  /// @param journalId LetPub 内部期刊 ID
  /// @return 期刊评价数据
  LetPubVenueData parse(String html, String journalId) {
    Document doc = Jsoup.parse(html);
    Map<String, Element> fieldMap = buildFieldMap(doc);

    var builder = LetPubVenueData.builder().letPubJournalId(journalId);

    parseJournalName(doc, builder);
    parseBasicInfo(fieldMap, builder);
    parseJcrPartition(fieldMap, builder);
    parseCasPartition(fieldMap, builder);
    parseWarningList(fieldMap, builder);
    parseReviewSpeed(fieldMap, builder);
    parseAcceptanceRate(fieldMap, builder);
    parseApc(fieldMap, builder);
    parseIndexedIn(fieldMap, builder);
    parseFiveYearIf(fieldMap, builder);
    parseImpactFactorTrend(html, builder);

    return builder.build();
  }

  // ========== 字段映射表 ==========

  /// 一次扫描期刊信息主表格，构建 label → value-td 映射。
  ///
  /// 页面可能包含多个 `table.table_yjfx`（如学科分类表），
  /// 通过表头"基本信息"定位期刊信息表，避免选错表格。
  ///
  /// @param doc 已解析的文档
  /// @return label 文本 → 值 td Element 映射
  private Map<String, Element> buildFieldMap(Document doc) {
    Map<String, Element> map = new LinkedHashMap<>();
    Element mainTable = findJournalInfoTable(doc);
    if (mainTable == null) {
      return map;
    }
    for (Element tr : mainTable.select("> tbody > tr")) {
      Elements tds = tr.select("> td");
      if (tds.size() >= 2) {
        String label = tds.first().text().trim();
        if (!label.isEmpty()) {
          map.put(label, tds.get(1));
        }
      }
    }
    return map;
  }

  /// 定位期刊信息主表格。
  ///
  /// 按优先级查找：
  /// 1. `table.table_yjfx` 且含"基本信息"表头
  /// 2. 任意 table 含"基本信息"表头（class 变更时的 fallback）
  ///
  /// @param doc 已解析的文档
  /// @return 期刊信息主表格，未找到返回 null
  private Element findJournalInfoTable(Document doc) {
    String thSelector = "th:contains(" + LABEL_BASIC_INFO + ")";
    for (Element table : doc.select("table.table_yjfx")) {
      if (!table.select(thSelector).isEmpty()) {
        return table;
      }
    }
    for (Element table : doc.select("table")) {
      if (!table.select(thSelector).isEmpty()) {
        return table;
      }
    }
    return null;
  }

  /// 按关键词搜索字段映射表，返回第一个匹配的值 Element。
  ///
  /// 按 keywords 顺序逐个尝试，对每个关键词遍历 fieldMap，
  /// 返回首个 label 包含该关键词的条目。
  ///
  /// @param fieldMap 字段映射表
  /// @param keywords 搜索关键词（按优先级排列）
  /// @return 匹配的值 Element，未找到返回 null
  private Element findFieldElement(Map<String, Element> fieldMap, String... keywords) {
    for (String keyword : keywords) {
      for (Map.Entry<String, Element> entry : fieldMap.entrySet()) {
        if (entry.getKey().contains(keyword)) {
          return entry.getValue();
        }
      }
    }
    return null;
  }

  /// 按关键词搜索字段映射表，返回值 td 的纯文本。
  ///
  /// Jsoup `Element.text()` 已通过 `StringUtil.normaliseWhitespace()` 折叠连续空白，
  /// 无需再次清理。
  ///
  /// @param fieldMap 字段映射表
  /// @param keywords 搜索关键词（按优先级排列）
  /// @return 文本内容，未找到返回空字符串
  private String getFieldText(Map<String, Element> fieldMap, String... keywords) {
    Element el = findFieldElement(fieldMap, keywords);
    return el != null ? el.text() : "";
  }

  // ========== 各区域解析 ==========

  /// 从可见 h1 提取期刊名称。
  ///
  /// 页面包含多个 h1：品牌标识（`display:none`）、期刊名、页脚装饰。
  /// 取第一个可见 h1 的文本，去除"期刊收藏夹"后缀。
  private void parseJournalName(Document doc, LetPubVenueData.LetPubVenueDataBuilder builder) {
    doc.select("h1").stream()
        .filter(h1 -> !DISPLAY_NONE.matcher(h1.attr("style")).find())
        .findFirst()
        .ifPresent(
            h1 -> {
              String name = h1.text().split("期刊收藏夹")[0].trim();
              builder.letPubName(name);
            });
  }

  /// 从 fieldMap 提取基本信息字段。
  ///
  /// **注意**：`出版国家或地区`、`出版语言`、`出版周期`、`出版年份` 四个字段
  /// 由 PubMed NLM Serfile 作为权威来源提供（见 `VenuePubmedImportHandler`），
  /// 此处不再抽取以避免多源冲突与死字段。
  private void parseBasicInfo(
      Map<String, Element> fieldMap, LetPubVenueData.LetPubVenueDataBuilder builder) {
    builder.researchDirection(getFieldText(fieldMap, "涉及的研究方向"));

    String articlesStr = getFieldText(fieldMap, "年文章数");
    builder.articlesPerYear(parseInteger(articlesStr.replace(",", "")));

    builder.goldOaPercent(getFieldText(fieldMap, "Gold OA文章占比"));
    builder.researchArticlePercent(getFieldText(fieldMap, "研究类文章占比"));
  }

  /// 解析 WOS JCR 分区（JIF + JCI 两个子表格）。
  ///
  /// 在值 td 内查找包含"按JIF指标"和"按JCI指标"文本的子表格，
  /// 分别提取学科、收录子集、分区、排名。
  private void parseJcrPartition(
      Map<String, Element> fieldMap, LetPubVenueData.LetPubVenueDataBuilder builder) {
    Element jcrTd = findFieldElement(fieldMap, "WOS期刊JCR分区", "JCR分区");
    if (jcrTd == null) {
      return;
    }

    for (Element table : jcrTd.select("table")) {
      String tableText = table.text();
      if (tableText.contains("按JIF指标")) {
        parseJcrSubTable(table, builder, true);
      } else if (tableText.contains("按JCI指标")) {
        parseJcrSubTable(table, builder, false);
      }
    }
  }

  /// 解析 JCR 子表格（JIF 或 JCI）的第一数据行。
  private void parseJcrSubTable(
      Element table, LetPubVenueData.LetPubVenueDataBuilder builder, boolean isJif) {
    Elements rows = table.select("tr");
    for (int i = 1; i < rows.size(); i++) {
      Elements tds = rows.get(i).select("> td");
      if (tds.size() >= 4) {
        String subject = tds.get(0).text().replace("学科：", "").trim();
        String collection = tds.get(1).text().trim();
        String quartile = tds.get(2).text().trim();
        String rank = tds.get(3).text().trim();

        if (isJif) {
          builder.jcrSubject(subject);
          builder.jcrCollection(collection);
          builder.jifQuartile(quartile);
          builder.jifRank(rank);
        } else {
          builder.jciQuartile(quartile);
          builder.jciRank(rank);
        }
        break;
      }
    }
  }

  /// 解析所有 CAS 中科院分区版本。
  ///
  /// LetPub 页面通常同时展示多个版本（新锐版/升级版/旧版），每个版本占一行。
  /// 本方法扫描所有 CAS 行并为每个版本生成一个 {@link LetPubVenueData.CasPartition}。
  private void parseCasPartition(
      Map<String, Element> fieldMap, LetPubVenueData.LetPubVenueDataBuilder builder) {
    List<CasSection> sections = findAllCasSections(fieldMap);
    if (sections.isEmpty()) {
      return;
    }

    List<LetPubVenueData.CasPartition> partitions = new ArrayList<>();
    for (CasSection section : sections) {
      LetPubVenueData.CasPartition partition = parseSingleCasSection(section);
      if (partition != null) {
        partitions.add(partition);
      }
    }

    if (!partitions.isEmpty()) {
      builder.casPartitions(partitions);
    }
  }

  /// 扫描字段映射表，返回所有 CAS 分区行。
  ///
  /// 识别规则：
  /// - 含 `新锐期刊分区表` → 规范化为 `YYYY年M月新锐版`
  /// - 含 `期刊分区表`（非新锐/非预警）→ 从括号提取原始版本号
  ///
  /// 排除含 `预警` 的行（预警名单使用相同前缀）。
  private List<CasSection> findAllCasSections(Map<String, Element> fieldMap) {
    List<CasSection> sections = new ArrayList<>();
    for (Map.Entry<String, Element> entry : fieldMap.entrySet()) {
      String label = entry.getKey();
      if (label.contains(LABEL_WARNING)) {
        continue;
      }

      if (label.contains(LABEL_CAS_PIONEER)) {
        sections.add(new CasSection(normalizeCasVersion(label, EDITION_PIONEER), entry.getValue()));
      } else if (label.contains(LABEL_CAS_STANDARD)) {
        sections.add(new CasSection(extractVersionFromParens(label), entry.getValue()));
      }
    }
    return sections;
  }

  /// 解析单个 CAS 分区行，生成 `CasPartition` 记录。
  ///
  /// 表格结构：
  /// ```
  /// | 大类学科 | 小类学科 | Top期刊 | 综述期刊 |
  /// | 名称 + span(分区) | 嵌套表(学科名 + span分区) | 是/否 | 是/否 |
  /// ```
  ///
  /// 分区值存在多个 `<span>` 标签（历史版本通过 `display:none` 隐藏），
  /// 仅提取可见 span 的文本作为当前分区。
  ///
  /// @param section CAS 分区行候选项
  /// @return 解析后的分区记录，结构不符合预期时返回 null
  private LetPubVenueData.CasPartition parseSingleCasSection(CasSection section) {
    Element valueTd = section.valueTd();

    // 查找含 <th> 表头的数据表格
    Element casTable = null;
    for (Element table : valueTd.select("table")) {
      if (!table.select("th").isEmpty()) {
        casTable = table;
        break;
      }
    }
    if (casTable == null) {
      return null;
    }

    // 找到第一个数据行（跳过表头行）
    Element dataRow = null;
    for (Element tr : casTable.select("> tbody > tr, > tr")) {
      if (tr.select("> th").isEmpty() && !tr.select("> td").isEmpty()) {
        dataRow = tr;
        break;
      }
    }
    if (dataRow == null) {
      return null;
    }

    Elements cells = dataRow.select("> td");
    if (cells.size() < 4) {
      return null;
    }

    // Cell 0: 大类学科 — ownText() 取类别名，可见 span 取分区
    Element majorCell = cells.get(0);
    String majorCategory = majorCell.ownText().trim();
    String majorQuartile = getVisibleSpanText(majorCell);

    // Cell 1: 小类学科 — 嵌套 table 提取
    MinorSubjectData minor = parseMinorSubject(cells.get(1));

    // Cell 2: Top 期刊
    Boolean topJournal = parseBooleanFlag(cells.get(2).text());

    // Cell 3: 综述期刊
    Boolean reviewJournal = parseBooleanFlag(cells.get(3).text());

    return LetPubVenueData.CasPartition.builder()
        .version(section.version())
        .majorCategory(majorCategory)
        .majorQuartile(majorQuartile)
        .minorSubject(minor.subject())
        .minorQuartile(minor.quartile())
        .topJournal(topJournal)
        .reviewJournal(reviewJournal)
        .build();
  }

  /// 解析小类学科（嵌套表格内的第一行）。
  private MinorSubjectData parseMinorSubject(Element cell) {
    Element nestedTable = cell.selectFirst("table");
    if (nestedTable == null) {
      return new MinorSubjectData("", "");
    }
    Element firstRow = nestedTable.selectFirst("tr");
    if (firstRow == null) {
      return new MinorSubjectData("", "");
    }
    Elements tds = firstRow.select("> td");
    if (tds.isEmpty()) {
      return new MinorSubjectData("", "");
    }

    String subject = tds.first().text().trim();
    String quartile = tds.size() >= 2 ? getVisibleSpanText(tds.get(1)) : "";
    return new MinorSubjectData(subject, quartile);
  }

  /// 解析预警名单。
  private void parseWarningList(
      Map<String, Element> fieldMap, LetPubVenueData.LetPubVenueDataBuilder builder) {
    String text = getFieldText(fieldMap, "预警名单");
    if (!text.isEmpty()) {
      builder.warningListStatus(text);
    }
  }

  /// 解析审稿速度（区分官方数据和网友经验）。
  private void parseReviewSpeed(
      Map<String, Element> fieldMap, LetPubVenueData.LetPubVenueDataBuilder builder) {
    String text = getFieldText(fieldMap, "平均审稿速度");
    if (text.isEmpty()) {
      return;
    }

    Matcher officialMatcher = REVIEW_SPEED_OFFICIAL.matcher(text);
    if (officialMatcher.find()) {
      builder.reviewSpeedOfficial(officialMatcher.group(1).trim());
    }

    Matcher userMatcher = REVIEW_SPEED_USER.matcher(text);
    if (userMatcher.find()) {
      builder.reviewSpeedUser(userMatcher.group(1).trim());
    }
  }

  /// 解析录用比例。
  private void parseAcceptanceRate(
      Map<String, Element> fieldMap, LetPubVenueData.LetPubVenueDataBuilder builder) {
    String text = getFieldText(fieldMap, "平均录用比例");
    if (text.isEmpty()) {
      return;
    }

    Matcher percentMatcher = ACCEPTANCE_PERCENT.matcher(text);
    if (percentMatcher.find()) {
      builder.acceptanceRate(percentMatcher.group(1));
    }
  }

  /// 解析 APC 费用信息。
  private void parseApc(
      Map<String, Element> fieldMap, LetPubVenueData.LetPubVenueDataBuilder builder) {
    String text = getFieldText(fieldMap, "APC文章处理费信息");
    if (!text.isEmpty()) {
      builder.apcInfo(text.length() > 200 ? text.substring(0, 200) : text);
    }
  }

  /// 解析数据库收录列表。
  private void parseIndexedIn(
      Map<String, Element> fieldMap, LetPubVenueData.LetPubVenueDataBuilder builder) {
    String text = getFieldText(fieldMap, "SCI期刊收录");
    if (text.isEmpty()) {
      return;
    }

    List<String> databases =
        Arrays.stream(text.split(",\\s*")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    builder.indexedIn(databases);
  }

  /// 解析五年影响因子。
  private void parseFiveYearIf(
      Map<String, Element> fieldMap, LetPubVenueData.LetPubVenueDataBuilder builder) {
    builder.fiveYearImpactFactor(parseDouble(getFieldText(fieldMap, "五年影响因子")));
  }

  /// 从 ECharts `showecharts_if_trend()` 函数中解析影响因子趋势数据。
  ///
  /// 提取 `xAxis.data`（年份数组）和 `series[0].data`（IF 值数组），
  /// 年份格式从 `"2024-2025年度"` 简化为 `"2024-2025"`。
  private void parseImpactFactorTrend(String html, LetPubVenueData.LetPubVenueDataBuilder builder) {
    int funcPos = html.indexOf("function showecharts_if_trend");
    if (funcPos < 0) {
      return;
    }
    int endPos = html.indexOf("setOption", funcPos);
    if (endPos < 0) {
      return;
    }
    String funcSection = html.substring(funcPos, endPos);

    Matcher yearMatcher = IF_TREND_YEAR_ARRAY.matcher(funcSection);
    if (!yearMatcher.find()) {
      return;
    }
    List<String> years = extractQuotedStrings(yearMatcher.group(1));

    Matcher valueMatcher = IF_TREND_VALUE_ARRAY.matcher(funcSection);
    if (!valueMatcher.find()) {
      return;
    }
    String[] valueTokens = valueMatcher.group(1).split(",");

    if (years.size() != valueTokens.length) {
      log.warn("IF 趋势年份({})与数值({})数量不匹配", years.size(), valueTokens.length);
      return;
    }

    Map<String, Double> trend = new LinkedHashMap<>();
    for (int i = 0; i < years.size(); i++) {
      String year = years.get(i).replace("年度", "").trim();
      Double value = parseDouble(valueTokens[i].trim());
      if (value != null) {
        trend.put(year, value);
      }
    }
    builder.impactFactorTrend(trend);
  }

  // ========== CAS 版本辅助 ==========

  /// 规范化 CAS 版本号（新锐版专用）。
  ///
  /// 从标签文本提取日期部分，拼接版本后缀。
  /// 例：`"《新锐期刊分区表》（2026年3月发布）"` → `"2026年3月新锐版"`。
  private String normalizeCasVersion(String label, String edition) {
    Matcher m = CAS_DATE.matcher(label);
    if (m.find()) {
      return m.group(1) + edition;
    }
    return edition;
  }

  /// 从标签括号内容提取版本号。
  ///
  /// 例：`"期刊分区表（2025年3月升级版）"` → `"2025年3月升级版"`。
  private String extractVersionFromParens(String label) {
    Matcher m = PARENS_CONTENT.matcher(label);
    if (m.find()) {
      return m.group(1).trim();
    }
    return label;
  }

  // ========== DOM 辅助 ==========

  /// 在容器中查找第一个可见 span 的文本。
  ///
  /// LetPub 在分区单元格中放置多个 `<span>`（历史版本通过
  /// `display:none` 隐藏），仅保留一个可见的当前值。
  ///
  /// @param container 包含 span 元素的父容器
  /// @return 可见 span 文本，无可见 span 时返回空字符串
  private String getVisibleSpanText(Element container) {
    return container.select("span").stream()
        .filter(span -> !DISPLAY_NONE.matcher(span.attr("style")).find())
        .findFirst()
        .map(Element::text)
        .orElse("");
  }

  /// 解析"是/否"布尔标识。
  ///
  /// @param text 单元格文本
  /// @return `true`=是, `false`=否, `null`=N/A 或无法识别
  private Boolean parseBooleanFlag(String text) {
    if (text == null) {
      return null;
    }
    String cleaned = text.trim();
    if ("是".equals(cleaned)) {
      return true;
    }
    if ("否".equals(cleaned)) {
      return false;
    }
    return null;
  }

  // ========== 通用工具 ==========

  /// 从逗号分隔的引号字符串中提取值列表。
  private static List<String> extractQuotedStrings(String raw) {
    List<String> result = new ArrayList<>();
    Matcher m = QUOTED_STRING.matcher(raw);
    while (m.find()) {
      result.add(m.group(1));
    }
    return result;
  }

  /// 安全解析整数。
  private static Integer parseInteger(String text) {
    if (text == null || text.isBlank()) {
      return null;
    }
    try {
      return Integer.parseInt(text.replaceAll("[^\\d]", ""));
    } catch (NumberFormatException e) {
      return null;
    }
  }

  /// 安全解析 Double。
  private static Double parseDouble(String text) {
    if (text == null || text.isBlank()) {
      return null;
    }
    try {
      return Double.parseDouble(text.replaceAll("[^\\d.]", ""));
    } catch (NumberFormatException e) {
      return null;
    }
  }

  /// CAS 分区候选项。
  ///
  /// @param version 规范化后的版本号（如"2026年3月新锐版"）
  /// @param valueTd 对应的值 td Element
  private record CasSection(String version, Element valueTd) {}

  /// CAS 小类学科提取结果。
  ///
  /// @param subject 学科名称
  /// @param quartile 分区（如"1区"）
  private record MinorSubjectData(String subject, String quartile) {}
}
