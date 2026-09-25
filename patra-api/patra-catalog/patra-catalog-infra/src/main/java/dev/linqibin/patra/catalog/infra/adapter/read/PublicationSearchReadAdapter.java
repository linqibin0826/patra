package dev.linqibin.patra.catalog.infra.adapter.read;

import dev.linqibin.commons.query.PageResult;
import dev.linqibin.commons.query.PagingParams;
import dev.linqibin.patra.catalog.domain.model.read.portal.FacetCount;
import dev.linqibin.patra.catalog.domain.model.read.portal.PortalPaperReadModel;
import dev.linqibin.patra.catalog.domain.model.read.portal.PublicationSearchFacets;
import dev.linqibin.patra.catalog.domain.model.read.portal.PublicationSearchFilter;
import dev.linqibin.patra.catalog.domain.model.vo.publication.EvidenceLevel;
import dev.linqibin.patra.catalog.domain.model.vo.publication.InlineMarkup;
import dev.linqibin.patra.catalog.domain.port.read.PublicationSearchReadPort;
import dev.linqibin.patra.catalog.infra.persistence.dao.PublicationSearchDao;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.IntFunction;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

/// 文献检索 CQRS 读适配器。
///
/// 单条 native query 组装列表项；负责 LIKE 转义、DOI 归一化、类型拆分 + [EvidenceLevel#classify]、
/// 摘要片段派生（[InlineMarkup]）。facets 采用 drill-down 语义。
///
/// @author linqibin
/// @since 0.1.0
@Repository
@RequiredArgsConstructor
public class PublicationSearchReadAdapter implements PublicationSearchReadPort {

  /// 聚合列分隔符，与 DAO 的 `string_agg(..., E'\\x1f', ...)` 一致（U+001F）。
  private static final String DELIMITER = "\u001f";

  /// 摘要片段长度（码点）。
  private static final int SNIPPET_CODE_POINTS = 300;

  private static final String[] LVL5 = typeValues(EvidenceLevel.SYSTEMATIC_REVIEW);
  private static final String[] LVL4 = typeValues(EvidenceLevel.RANDOMIZED_CONTROLLED_TRIAL);
  private static final String[] LVL3 = typeValues(EvidenceLevel.COHORT_OR_CASE_CONTROL);
  private static final String[] LVL2 = typeValues(EvidenceLevel.NON_SYSTEMATIC_REVIEW);
  private static final String[] LVL1 = typeValues(EvidenceLevel.CASE_REPORT);

  private final PublicationSearchDao dao;

  /// 一次检索的已绑定参数（LIKE 已转义、DOI 已归一化、列表已转数组）。facets 的 drill-down 靠把某维度置 null 实现。
  private record Bound(
      String keyword,
      String author,
      String pmid,
      String doi,
      Integer yearFrom,
      Integer yearTo,
      Long[] venueIds,
      String[] types,
      Boolean isOpenAccess,
      String[] languages,
      Integer[] evidenceRanks) {

    /// 由领域过滤参数构建。
    ///
    /// @param f 过滤参数
    /// @return 已绑定参数
    static Bound of(PublicationSearchFilter f) {
      return new Bound(
          escapeLike(f.keyword()),
          escapeLike(f.author()),
          blankToNull(f.pmid()),
          normalizeDoi(f.doi()),
          f.yearFrom(),
          f.yearTo(),
          toArray(f.venueIds(), Long[]::new),
          toArray(
              f.types().stream().map(t -> t.trim().toLowerCase(Locale.ROOT)).toList(),
              String[]::new),
          f.isOpenAccess(),
          toArray(f.languages(), String[]::new),
          toArray(f.evidenceLevels().stream().map(EvidenceLevel::rank).toList(), Integer[]::new));
    }
  }

  @Override
  public PageResult<PortalPaperReadModel> search(
      PublicationSearchFilter filter, PagingParams paging) {
    Bound b = Bound.of(filter);
    Page<PublicationSearchRow> page =
        dao.findSearchPage(
            b.keyword(),
            InlineMarkup.TAG_PATTERN,
            b.author(),
            b.pmid(),
            b.doi(),
            b.yearFrom(),
            b.yearTo(),
            b.venueIds(),
            b.types(),
            b.isOpenAccess(),
            b.languages(),
            b.evidenceRanks(),
            LVL5,
            LVL4,
            LVL3,
            LVL2,
            LVL1,
            filter.sort().name(),
            PageRequest.of(paging.page() - 1, paging.pageSize()));
    List<PortalPaperReadModel> items = page.getContent().stream().map(this::toReadModel).toList();
    return PageResult.of(items, paging.page(), paging.pageSize(), page.getTotalElements());
  }

  @Override
  public PublicationSearchFacets facets(PublicationSearchFilter filter) {
    Bound b = Bound.of(filter);
    return PublicationSearchFacets.builder()
        .years(
            toFacets(
                dao.facetYears(
                    b.keyword(),
                    InlineMarkup.TAG_PATTERN,
                    b.author(),
                    b.pmid(),
                    b.doi(),
                    null,
                    null,
                    b.venueIds(),
                    b.types(),
                    b.isOpenAccess(),
                    b.languages(),
                    b.evidenceRanks(),
                    LVL5,
                    LVL4,
                    LVL3,
                    LVL2,
                    LVL1)))
        .types(
            toFacets(
                dao.facetTypes(
                    b.keyword(),
                    InlineMarkup.TAG_PATTERN,
                    b.author(),
                    b.pmid(),
                    b.doi(),
                    b.yearFrom(),
                    b.yearTo(),
                    b.venueIds(),
                    null,
                    b.isOpenAccess(),
                    b.languages(),
                    b.evidenceRanks(),
                    LVL5,
                    LVL4,
                    LVL3,
                    LVL2,
                    LVL1)))
        .evidence(
            toEvidenceFacets(
                dao.facetEvidence(
                    b.keyword(),
                    InlineMarkup.TAG_PATTERN,
                    b.author(),
                    b.pmid(),
                    b.doi(),
                    b.yearFrom(),
                    b.yearTo(),
                    b.venueIds(),
                    b.types(),
                    b.isOpenAccess(),
                    b.languages(),
                    null,
                    LVL5,
                    LVL4,
                    LVL3,
                    LVL2,
                    LVL1)))
        .languages(
            toFacets(
                dao.facetLanguages(
                    b.keyword(),
                    InlineMarkup.TAG_PATTERN,
                    b.author(),
                    b.pmid(),
                    b.doi(),
                    b.yearFrom(),
                    b.yearTo(),
                    b.venueIds(),
                    b.types(),
                    b.isOpenAccess(),
                    null,
                    b.evidenceRanks(),
                    LVL5,
                    LVL4,
                    LVL3,
                    LVL2,
                    LVL1)))
        .openAccess(
            dao.countMatching(
                b.keyword(),
                InlineMarkup.TAG_PATTERN,
                b.author(),
                b.pmid(),
                b.doi(),
                b.yearFrom(),
                b.yearTo(),
                b.venueIds(),
                b.types(),
                Boolean.TRUE,
                b.languages(),
                b.evidenceRanks(),
                LVL5,
                LVL4,
                LVL3,
                LVL2,
                LVL1))
        .total(
            dao.countMatching(
                b.keyword(),
                InlineMarkup.TAG_PATTERN,
                b.author(),
                b.pmid(),
                b.doi(),
                b.yearFrom(),
                b.yearTo(),
                b.venueIds(),
                b.types(),
                b.isOpenAccess(),
                b.languages(),
                b.evidenceRanks(),
                LVL5,
                LVL4,
                LVL3,
                LVL2,
                LVL1))
        .lastSyncedAt(lastSyncedAt())
        .build();
  }

  /// 投影行 → FacetCount 列表（保持 SQL 排序）。
  ///
  /// @param rows 投影行
  /// @return facet 列表
  private static List<FacetCount> toFacets(List<PublicationFacetCountRow> rows) {
    return rows.stream().map(r -> FacetCount.of(r.getValue(), r.getCount())).toList();
  }

  /// rank 分组 → 固定 6 项（rank 降序，UNKNOWN 末尾），缺档补 0。
  ///
  /// @param rows rank 分组行（value 为 rank 文本）
  /// @return 6 项 facet
  private static List<FacetCount> toEvidenceFacets(List<PublicationFacetCountRow> rows) {
    Map<Integer, Long> byRank =
        rows.stream()
            .collect(
                Collectors.toMap(
                    r -> Integer.parseInt(r.getValue()), PublicationFacetCountRow::getCount));
    return List.of(
            EvidenceLevel.SYSTEMATIC_REVIEW,
            EvidenceLevel.RANDOMIZED_CONTROLLED_TRIAL,
            EvidenceLevel.COHORT_OR_CASE_CONTROL,
            EvidenceLevel.NON_SYSTEMATIC_REVIEW,
            EvidenceLevel.CASE_REPORT,
            EvidenceLevel.UNKNOWN)
        .stream()
        .map(level -> FacetCount.of(level.name(), byRank.getOrDefault(level.rank(), 0L)))
        .toList();
  }

  /// 全库最后采集时间；空库为 null。
  ///
  /// @return 时间或 null
  private Instant lastSyncedAt() {
    PublicationLastSyncedRow row = dao.findLastSyncedAt();
    return row == null ? null : row.getLastSyncedAt();
  }

  /// 投影行 → 读模型：类型聚合列拆开后第一个当 studyType、整个列表衍生证据等级；摘要原文派生片段。
  ///
  /// @param row 投影行
  /// @return 读模型
  private PortalPaperReadModel toReadModel(PublicationSearchRow row) {
    List<String> types = split(row.getPublicationTypesAgg());
    return PortalPaperReadModel.builder()
        .id(row.getId())
        .title(row.getTitle())
        .venueId(row.getVenueId())
        .venueName(row.getVenueName())
        .publicationYear(row.getPublicationYear())
        .authors(split(row.getAuthorNames()))
        .citationCount(row.getCitationCount())
        .doi(row.getDoi())
        .pmid(row.getPmid())
        .provenanceCode(row.getProvenanceCode())
        .studyType(types.isEmpty() ? null : types.get(0))
        .evidenceLevel(EvidenceLevel.classify(types))
        .abstractSnippet(toSnippet(row.getAbstractRaw()))
        .lastSyncedAt(row.getLastSyncedAt())
        .build();
  }

  /// LIKE 通配转义，转义符 `!`，与 SQL `ESCAPE '!'` 配套；null / 空白 → null。
  ///
  /// @param raw 原始关键词
  /// @return 转义后的关键词或 null
  private static String escapeLike(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    return raw.trim().replace("!", "!!").replace("%", "!%").replace("_", "!_");
  }

  /// DOI 归一化：去 `https://doi.org/` / `http://dx.doi.org/` 等 URL 前缀与 `doi:` 前缀，转小写；空白 → null。
  ///
  /// @param raw 原始 DOI
  /// @return 归一化 DOI 或 null
  private static String normalizeDoi(String raw) {
    String value = blankToNull(raw);
    if (value == null) {
      return null;
    }
    String lower = value.toLowerCase(Locale.ROOT);
    for (String prefix :
        List.of(
            "https://doi.org/",
            "http://doi.org/",
            "https://dx.doi.org/",
            "http://dx.doi.org/",
            "doi:")) {
      if (lower.startsWith(prefix)) {
        return lower.substring(prefix.length()).trim();
      }
    }
    return lower;
  }

  /// 证据等级 → 小写类型值数组（SQL 参数）。
  ///
  /// @param level 证据等级
  /// @return 类型值数组
  private static String[] typeValues(EvidenceLevel level) {
    return EvidenceLevel.typeValuesOf(level).toArray(String[]::new);
  }

  /// List → 数组；空列表转 null（SQL 里 `IS NULL` 短路为不过滤）。
  ///
  /// @param list 输入列表
  /// @param generator 数组构造器
  /// @param <T> 元素类型
  /// @return 数组或 null
  private static <T> T[] toArray(List<T> list, IntFunction<T[]> generator) {
    return list.isEmpty() ? null : list.toArray(generator);
  }

  /// 空白 → null，否则 trim。
  ///
  /// @param raw 原始字符串
  /// @return trim 后的字符串或 null
  private static String blankToNull(String raw) {
    return raw == null || raw.isBlank() ? null : raw.trim();
  }

  /// 摘要原文 → 可见纯文本前 300 码点；空白结果视为无摘要。
  ///
  /// @param abstractRaw 摘要原文（含内联标记），可为 null
  /// @return 片段或 null
  private static String toSnippet(String abstractRaw) {
    if (abstractRaw == null) {
      return null;
    }
    String plain =
        InlineMarkup.truncate(InlineMarkup.toPlainText(abstractRaw), SNIPPET_CODE_POINTS);
    return plain.isBlank() ? null : plain;
  }

  /// 拆分单元分隔符聚合列；null / 空白 → 空列表。
  ///
  /// @param agg 聚合列值
  /// @return 拆分结果
  private static List<String> split(String agg) {
    if (agg == null || agg.isBlank()) {
      return List.of();
    }
    return List.of(agg.split(DELIMITER, -1));
  }
}
