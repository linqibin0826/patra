package dev.patra.catalog.domain.organization.port.read;

import dev.patra.catalog.domain.kernel.model.vo.SuggestQuery;
import dev.patra.catalog.domain.organization.model.read.OrganizationSuggestion;
import dev.patra.catalog.domain.organization.model.read.OrganizationSummary;
import java.util.List;
import java.util.Optional;

/// 机构 CQRS 读端口：边打边搜的建议与按 ROR ID 的查询。
///
/// 由 Infra 层 `OrganizationReadAdapter` 实现。两个方法都只认 `active` 且未软删的机构。
///
/// @author linqibin
/// @since 0.1.0
public interface OrganizationReadPort {

  /// 按名称、中文名、别名、缩写搜机构，按命中方式、中国优先、医疗优先等规则排好序。
  ///
  /// @param query 已归一化的查询
  /// @return 最多 `query.limit()` 条候选，没有命中为空列表
  List<OrganizationSuggestion> suggest(SuggestQuery query);

  /// 按 9 位裸 ROR ID 查机构摘要。
  ///
  /// @param rorId 裸 ROR ID
  /// @return 机构摘要；不存在或非 active 为空
  Optional<OrganizationSummary> findByRorId(String rorId);
}
