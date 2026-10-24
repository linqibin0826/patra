package dev.patra.interaction.app.collection.usecase.query;

import dev.patra.interaction.domain.subject.model.enums.SubjectState;
import dev.patra.interaction.domain.subject.model.read.SubjectView;

/// 条目视图里来自登记对象的字段。
final class SubjectFields {

  /// 只有静态方法。
  private SubjectFields() {}

  /// 已失效时的快照标题（「收藏时记下的标题」），正常时为 `null`。
  ///
  /// @param view 视图
  /// @return 标题
  static String unavailableTitle(SubjectView view) {
    return view.state() == SubjectState.UNAVAILABLE && view.snapshot() != null
        ? view.snapshot().title()
        : null;
  }
}
