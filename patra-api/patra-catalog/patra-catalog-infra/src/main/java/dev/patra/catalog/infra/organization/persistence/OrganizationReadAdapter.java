package dev.patra.catalog.infra.organization.persistence;

import dev.patra.catalog.domain.kernel.model.vo.SuggestQuery;
import dev.patra.catalog.domain.organization.model.read.OrganizationSuggestion;
import dev.patra.catalog.domain.organization.model.read.OrganizationSummary;
import dev.patra.catalog.domain.organization.port.read.OrganizationReadPort;
import dev.patra.catalog.infra.kernel.support.LikePatterns;
import dev.patra.catalog.infra.kernel.support.ProjectionLists;
import dev.patra.catalog.infra.organization.persistence.dao.OrganizationDao;
import dev.patra.catalog.infra.organization.persistence.dao.OrganizationNameDao;
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

  /// 子串模式下不超过这个码点数的查询走短子串查询（`%协和%` 凑不出三元组，见
  /// `OrganizationSuggestSql.NAME_SHORT_SUBSTRING`）。
  static final int SHORT_SUBSTRING_MAX_CODE_POINTS = 2;

  private final OrganizationNameDao organizationNameDao;
  private final OrganizationDao organizationDao;

  @Override
  public List<OrganizationSuggestion> suggest(SuggestQuery query) {
    String text = query.text();
    String prefix = LikePatterns.prefix(text);
    String wordStart = LikePatterns.wordStart(text);
    List<OrganizationSuggestRow> rows =
        switch (query.mode()) {
          case SUBSTRING -> suggestBySubstring(query, prefix, wordStart);
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

  /// 子串模式：2 码点以内走短子串查询，其余走 trigram 索引能用的普通子串查询。
  ///
  /// @param query 查询
  /// @param prefix 前缀模式
  /// @param wordStart 词首模式
  /// @return 候选投影
  private List<OrganizationSuggestRow> suggestBySubstring(
      SuggestQuery query, String prefix, String wordStart) {
    String text = query.text();
    String contains = LikePatterns.contains(text);
    if (text.codePointCount(0, text.length()) <= SHORT_SUBSTRING_MAX_CODE_POINTS) {
      return organizationNameDao.suggestByShortSubstring(
          text, contains, prefix, wordStart, query.limit());
    }
    return organizationNameDao.suggestBySubstring(text, contains, prefix, wordStart, query.limit());
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
