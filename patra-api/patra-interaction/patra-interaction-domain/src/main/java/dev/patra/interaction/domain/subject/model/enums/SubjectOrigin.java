package dev.patra.interaction.domain.subject.model.enums;

/// 对象从哪来。
public enum SubjectOrigin {
  /// 外部对象（文献、期刊）：有来源策略，首次互动时向来源解析，每日对账。
  EXTERNAL,

  /// 本地对象（投稿经验）：由所属子域登记、改状态，不经外部解析，不对账。
  LOCAL
}
