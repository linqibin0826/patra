package dev.patra.interaction.app.collection.usecase.query;

import dev.patra.interaction.domain.collection.model.read.ItemRef;
import dev.patra.interaction.domain.subject.model.read.SubjectView;
import dev.patra.interaction.domain.subject.model.vo.SubjectId;
import java.util.List;
import java.util.Map;

/// 排好、切好的一页引用，连同本页对象的视图。
///
/// @param refs 本页引用
/// @param total 总条数
/// @param views 本页对象 → 视图（可能多于本页）
record OrderedPage(List<ItemRef> refs, long total, Map<SubjectId, SubjectView> views) {

  /// 本页对象 ID。
  ///
  /// @return ID
  List<SubjectId> ids() {
    return refs.stream().map(ItemRef::subjectId).toList();
  }
}
