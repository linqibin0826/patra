package dev.linqibin.patra.catalog.infra.adapter.read;

import dev.linqibin.commons.query.PageResult;
import dev.linqibin.commons.query.PagingParams;
import dev.linqibin.patra.catalog.domain.model.read.portal.FacetCount;
import dev.linqibin.patra.catalog.domain.model.read.portal.VenueBrowseFacets;
import dev.linqibin.patra.catalog.domain.model.read.portal.VenueBrowseFilter;
import dev.linqibin.patra.catalog.domain.model.read.portal.VenueBrowseReadModel;
import dev.linqibin.patra.catalog.domain.port.read.VenueBrowseReadPort;
import dev.linqibin.patra.catalog.infra.persistence.dao.VenueDao;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

/// 期刊浏览检索 CQRS 读适配器。
///
/// 单次 LATERAL JOIN（venue + 最新年 JCR/CAS），支持多值 OR 过滤和多种排序，无 N+1。
/// `facets()` 采用 drill-down 语义：每个分组维度的计数忽略该维度自身的已选值。
/// 负责对原始 keyword 执行 LIKE 转义（`!` 为转义符），与 SQL `ESCAPE '!'` 配套。
///
/// @author linqibin
/// @since 0.1.0
@Repository
@RequiredArgsConstructor
public class VenueBrowseReadAdapter implements VenueBrowseReadPort {

  private final VenueDao venueDao;

  /// 分页检索期刊浏览列表。
  ///
  /// 支持关键词全文搜索与多值筛选（学科、JCR 分区、CAS 分区、国家等），
  /// 多值条件在同一维度内为 OR 语义，跨维度之间为 AND 语义。
  /// 结果按 `filter.sort()` 排序，页码从 1 开始。
  ///
  /// @param filter 筛选条件，包含关键词、分区、布尔标志等
  /// @param paging 分页参数（1-based page + pageSize）
  /// @return 当前页数据及总条数

  @Override
  public PageResult<VenueBrowseReadModel> search(VenueBrowseFilter filter, PagingParams paging) {
    Page<VenueBrowseRow> page =
        venueDao.findPortalVenueBrowsePage(
            escapeLike(filter.keyword()),
            filter.sort().name(),
            toArray(filter.subjects()),
            toArray(filter.jcrQuartiles()),
            toArray(filter.casQuartiles()),
            filter.casTop(),
            filter.isOpenAccess(),
            filter.doaj(),
            toArray(filter.countryCodes()),
            PageRequest.of(paging.page() - 1, paging.pageSize()));

    List<VenueBrowseReadModel> items = page.getContent().stream().map(this::toReadModel).toList();
    return PageResult.of(items, paging.page(), paging.pageSize(), page.getTotalElements());
  }

  /// 计算各筛选维度的 drill-down 计数。
  ///
  /// 每个维度的计数采用「当前 query + 除本维度外其它已选维度」的聚合策略：
  /// 即计算某维度候选值的命中数时，暂时忽略该维度自身已选值，
  /// 从而保证组内多选（同一维度勾选多个值）不会使其余候选项计数归零。
  ///
  /// 示例：用户已选 JCR=Q1，计算 JCR 各值计数时不带入 Q1 约束，
  /// 使 Q2/Q3/Q4 仍然显示真实命中数，支持继续追加选择。
  ///
  /// @param filter 当前筛选条件（含已选维度值）
  /// @return 各维度候选值及其 drill-down 命中计数

  @Override
  public VenueBrowseFacets facets(VenueBrowseFilter filter) {
    String kw = escapeLike(filter.keyword());
    String[] subjects = toArray(filter.subjects());
    String[] jcrQuartiles = toArray(filter.jcrQuartiles());
    String[] casQuartiles = toArray(filter.casQuartiles());
    String[] countryCodes = toArray(filter.countryCodes());
    Boolean casTop = filter.casTop();
    Boolean isOpenAccess = filter.isOpenAccess();
    Boolean doaj = filter.doaj();

    return VenueBrowseFacets.builder()
        .subjects(
            venueDao
                .facetSubjects(
                    kw, jcrQuartiles, casQuartiles, casTop, isOpenAccess, doaj, countryCodes)
                .stream()
                .map(r -> FacetCount.of(r.getValue(), r.getCount()))
                .toList())
        .jcrQuartiles(
            venueDao
                .facetJcrQuartiles(
                    kw, subjects, casQuartiles, casTop, isOpenAccess, doaj, countryCodes)
                .stream()
                .map(r -> FacetCount.of(r.getValue(), r.getCount()))
                .toList())
        .casQuartiles(
            venueDao
                .facetCasQuartiles(
                    kw, subjects, jcrQuartiles, casTop, isOpenAccess, doaj, countryCodes)
                .stream()
                .map(r -> FacetCount.of(r.getValue(), r.getCount()))
                .toList())
        .countries(
            venueDao
                .facetCountries(
                    kw, subjects, jcrQuartiles, casQuartiles, casTop, isOpenAccess, doaj)
                .stream()
                .map(r -> FacetCount.of(r.getValue(), r.getCount()))
                .toList())
        .casTop(
            venueDao.countCasTop(
                kw, subjects, jcrQuartiles, casQuartiles, isOpenAccess, doaj, countryCodes))
        .openAccess(
            venueDao.countOpenAccess(
                kw, subjects, jcrQuartiles, casQuartiles, casTop, doaj, countryCodes))
        .doaj(
            venueDao.countDoaj(
                kw, subjects, jcrQuartiles, casQuartiles, casTop, isOpenAccess, countryCodes))
        .build();
  }

  /// List → `String[]` 转换：空列表转 null（表示"不过滤"），非空则转数组。
  ///
  /// @param list 输入列表，null 或空均表示无过滤
  /// @return String 数组，或 null
  private String[] toArray(List<String> list) {
    if (list == null || list.isEmpty()) {
      return null;
    }
    return list.toArray(String[]::new);
  }

  /// 对 LIKE 前缀关键词转义特殊字符，转义符为 `!`，与 SQL `ESCAPE '!'` 子句配套。
  ///
  /// @param keyword 原始关键词，null 时直接返回 null
  /// @return 转义后的关键词，或 null
  private String escapeLike(String keyword) {
    if (keyword == null) {
      return null;
    }
    return keyword.replace("!", "!!").replace("%", "!%").replace("_", "!_");
  }

  private VenueBrowseReadModel toReadModel(VenueBrowseRow row) {
    return VenueBrowseReadModel.builder()
        .id(row.getId())
        .name(row.getName())
        .abbr(row.getAbbr())
        .coverObjectKey(row.getCoverObjectKey())
        .impactFactor(row.getImpactFactor())
        .jcrQuartile(row.getJcrQuartile())
        .jcrSubject(row.getJcrSubject())
        .casMajorCategory(row.getCasMajorCategory())
        .casMajorQuartile(row.getCasMajorQuartile())
        .casIsTop(row.getCasIsTop())
        .countryCode(row.getCountryCode())
        .citedByCount(row.getCitedByCount())
        .foundedYear(row.getFoundedYear())
        .isOpenAccess(row.getIsOpenAccess())
        .isInDoaj(row.getIsInDoaj())
        .issnL(row.getIssnL())
        .build();
  }
}
