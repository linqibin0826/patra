package dev.patra.catalog.domain.mesh.port.read;

import dev.patra.catalog.domain.kernel.model.vo.SuggestQuery;
import dev.patra.catalog.domain.mesh.model.read.MeshDescriptorSuggestion;
import dev.patra.catalog.domain.mesh.model.read.MeshDescriptorSummary;
import java.util.List;
import java.util.Optional;

/// MeSH 主题词 CQRS 读端口：边打边搜的建议与按 UI 的查询。
///
/// 由 Infra 层 `MeshDescriptorReadAdapter` 实现。建议只给主题类（class 1）且 active 的主题词，
/// 查询不限 class。
///
/// @author linqibin
/// @since 0.1.0
public interface MeshDescriptorReadPort {

  /// 按主题词名与入口词搜主题词；经入口词命中的返回主题词本身。
  ///
  /// @param query 已归一化的查询（MeSH 只做前缀与词首，忽略匹配模式）
  /// @return 最多 `query.limit()` 条候选，没有命中为空列表
  List<MeshDescriptorSuggestion> suggest(SuggestQuery query);

  /// 按 UI 查主题词摘要。
  ///
  /// @param ui 主题词 UI
  /// @return 主题词摘要；不存在为空
  Optional<MeshDescriptorSummary> findByUi(String ui);
}
