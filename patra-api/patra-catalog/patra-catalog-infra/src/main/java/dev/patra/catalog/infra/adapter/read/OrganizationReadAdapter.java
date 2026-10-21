package dev.patra.catalog.infra.adapter.read;

import dev.patra.catalog.domain.model.read.organization.OrganizationSuggestion;
import dev.patra.catalog.domain.model.read.organization.OrganizationSummary;
import dev.patra.catalog.domain.port.read.OrganizationReadPort;
import dev.patra.catalog.domain.query.SuggestQuery;
import dev.patra.catalog.infra.persistence.dao.OrganizationDao;
import dev.patra.catalog.infra.persistence.dao.OrganizationNameDao;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/// 机构 CQRS 读适配器：按匹配模式选 native 查询、拼 LIKE 模式、把投影转成读模型。
///
/// 排序与过滤都在 SQL 里（`OrganizationSuggestSql`）；这里只负责转义与转换。
///
/// @author linqibin
/// @since 0.1.0
@Repository
@RequiredArgsConstructor
public class OrganizationReadAdapter implements OrganizationReadPort {

  private final OrganizationNameDao organizationNameDao;
  private final OrganizationDao organizationDao;

  @Override
  public List<OrganizationSuggestion> suggest(SuggestQuery query) {
    String text = query.text();
    String prefix = LikePatterns.prefix(text);
    String wordStart = LikePatterns.wordStart(text);
    List<OrganizationSuggestRow> rows =
        switch (query.mode()) {
          case SUBSTRING ->
              organizationNameDao.suggestBySubstring(
                  text, LikePatterns.contains(text), prefix, wordStart, query.limit());
          case WORD_PREFIX ->
              organizationNameDao.suggestByWordPrefix(text, prefix, wordStart, query.limit());
        };
    return rows.stream()
        .map(row -> OrganizationSuggestion.of(toSummary(row), row.getMatchedName()))
        .toList();
  }

  @Override
  public Optional<OrganizationSummary> findByRorId(String rorId) {
    return organizationDao.findSummaryByRorId(rorId).map(this::toSummary);
  }

  /// 投影 → 读模型。
  ///
  /// @param row 摘要投影
  /// @return 读模型
  private OrganizationSummary toSummary(OrganizationSummaryRow row) {
    return OrganizationSummary.builder()
        .rorId(row.getRorId())
        .displayName(row.getDisplayName())
        .zhName(row.getZhName())
        .countryCode(row.getCountryCode())
        .cityName(row.getCityName())
        .types(ProjectionLists.splitBySpace(row.getTypes()))
        .build();
  }
}
