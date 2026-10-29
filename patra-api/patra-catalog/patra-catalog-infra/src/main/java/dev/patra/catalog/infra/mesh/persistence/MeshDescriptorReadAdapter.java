package dev.patra.catalog.infra.mesh.persistence;

import dev.patra.catalog.domain.kernel.model.vo.SuggestQuery;
import dev.patra.catalog.domain.mesh.model.read.MeshDescriptorSuggestion;
import dev.patra.catalog.domain.mesh.model.read.MeshDescriptorSummary;
import dev.patra.catalog.domain.mesh.port.read.MeshDescriptorReadPort;
import dev.patra.catalog.infra.kernel.support.LikePatterns;
import dev.patra.catalog.infra.kernel.support.ProjectionLists;
import dev.patra.catalog.infra.mesh.persistence.dao.MeshDescriptorDao;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/// MeSH 主题词 CQRS 读适配器：拼 LIKE 模式、把投影转成读模型。
///
/// MeSH 全是英文，只做前缀与词首匹配，忽略查询的匹配模式；排序与过滤都在 SQL 里
/// （`MeshDescriptorSuggestSql`）。
///
/// @author linqibin
/// @since 0.1.0
@Repository
@RequiredArgsConstructor
public class MeshDescriptorReadAdapter implements MeshDescriptorReadPort {

  private final MeshDescriptorDao meshDescriptorDao;

  @Override
  public List<MeshDescriptorSuggestion> suggest(SuggestQuery query) {
    String text = query.text();
    return meshDescriptorDao
        .suggest(text, LikePatterns.prefix(text), LikePatterns.wordStart(text), query.limit())
        .stream()
        .map(row -> MeshDescriptorSuggestion.of(toSummary(row), row.getMatchedTerm()))
        .toList();
  }

  @Override
  public Optional<MeshDescriptorSummary> findByUi(String ui) {
    return meshDescriptorDao.findSummaryByUi(ui).map(this::toSummary);
  }

  /// 投影 → 读模型。
  ///
  /// @param row 摘要投影
  /// @return 读模型
  private MeshDescriptorSummary toSummary(MeshDescriptorSummaryRow row) {
    return MeshDescriptorSummary.of(
        row.getUi(), row.getName(), ProjectionLists.splitBySpace(row.getTreeNumbers()));
  }
}
