package dev.patra.interaction.app.collection.service;

import dev.patra.interaction.domain.subject.model.read.SubjectView;
import dev.patra.interaction.domain.subject.model.vo.SubjectId;
import dev.patra.interaction.domain.subject.port.read.SubjectReadPort;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/// 按 subject 读端口的上限（{@value SubjectReadPort#MAX_IDS}）分块取登记对象视图。
@Component
@RequiredArgsConstructor
public class SubjectViews {

  private final SubjectReadPort subjects;

  /// 取视图。
  ///
  /// @param ids 本地 ID
  /// @return 本地 ID → 视图；查不到的不在结果里
  public Map<SubjectId, SubjectView> fetch(List<SubjectId> ids) {
    if (ids.isEmpty()) {
      return Map.of();
    }
    Map<SubjectId, SubjectView> views = new HashMap<>();
    for (int from = 0; from < ids.size(); from += SubjectReadPort.MAX_IDS) {
      views.putAll(
          subjects.findByIds(
              ids.subList(from, Math.min(ids.size(), from + SubjectReadPort.MAX_IDS))));
    }
    return views;
  }
}
