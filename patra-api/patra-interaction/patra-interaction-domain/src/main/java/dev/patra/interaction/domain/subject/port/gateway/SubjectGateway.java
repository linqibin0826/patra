package dev.patra.interaction.domain.subject.port.gateway;

import dev.patra.interaction.domain.subject.model.enums.SubjectCapability;
import dev.patra.interaction.domain.subject.model.enums.SubjectState;
import dev.patra.interaction.domain.subject.model.enums.SubjectType;
import dev.patra.interaction.domain.subject.model.vo.SubjectId;
import dev.patra.interaction.domain.subject.model.vo.SubjectOwner;
import dev.patra.interaction.domain.subject.model.vo.SubjectSnapshot;

/// 对象登记（实现在 app）：外部对象首次互动前的解析与登记，本地对象由所属子域登记与更新。
///
/// `resolveForInteraction` 只用于**新增**互动；对已有互动（改备注、取消收藏、移入收藏夹）直接用自己表里的
/// `subjectId`，对象失效后用户仍能管理已有条目。
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

  /// 登记一个本地对象（如投稿经验）：身份锚 `{前缀}:{localId}`，ACTIVE，带所有者与「是否匿名」，之后都不变。
  ///
  /// **须在调用方的事务里调用**，与对象本身的写入同一事务：调用方没开事务时抛
  /// `IllegalTransactionStateException`。重复登记违反唯一约束，是程序错误。
  ///
  /// @param type 本地对象种类
  /// @param localId 所属子域里的 ID（如帖子 ID）
  /// @param owner 所有者
  /// @param snapshot 展示快照，由所属子域决定内容
  /// @return 本地 ID
  /// @throws IllegalArgumentException `type` 不是本地对象
  SubjectId registerLocal(
      SubjectType type, long localId, SubjectOwner owner, SubjectSnapshot snapshot);

  /// 把本地对象的状态与快照改成给定值：删除时 UNAVAILABLE，编辑时 ACTIVE 加新快照。所有者不变。
  ///
  /// **须在调用方的事务里调用**，同 {@link #registerLocal}。
  ///
  /// @param type 本地对象种类
  /// @param localId 所属子域里的 ID
  /// @param state 新状态
  /// @param snapshot 新快照（删除时传当前快照）
  /// @throws IllegalArgumentException `type` 不是本地对象
  /// @throws IllegalStateException 这个本地对象没有登记
  void updateLocal(SubjectType type, long localId, SubjectState state, SubjectSnapshot snapshot);
}
