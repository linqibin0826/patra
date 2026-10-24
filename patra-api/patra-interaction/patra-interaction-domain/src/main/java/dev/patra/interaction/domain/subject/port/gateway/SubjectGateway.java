package dev.patra.interaction.domain.subject.port.gateway;

import dev.patra.interaction.domain.subject.model.enums.SubjectCapability;
import dev.patra.interaction.domain.subject.model.enums.SubjectType;
import dev.patra.interaction.domain.subject.model.vo.SubjectId;

/// 首次互动前的对象登记（实现在 app）。
///
/// 只用于**新增**互动；对已有互动（改备注、取消收藏、移入收藏夹）直接用自己表里的 `subjectId`，
/// 对象失效后用户仍能管理已有条目。
public interface SubjectGateway {

  /// 已登记且正常就直接返回（不调来源）；未登记就向来源解析并登记。
  ///
  /// 失败以领域异常直接传播：能力不支持、key 不合法（422）；对象不存在、已软删、已失效（404）；
  /// 来源不可用（503，不写库）。
  ///
  /// @param type 对象种类
  /// @param key 对外 key（文献是门户 URL 里的 catalog 内部 ID）
  /// @param capability 要做的互动
  /// @return 本地 ID
  SubjectId resolveForInteraction(SubjectType type, String key, SubjectCapability capability);
}
