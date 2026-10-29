package dev.patra.interaction.app.post.service;

import dev.patra.interaction.domain.post.model.vo.PostId;
import dev.patra.interaction.domain.subject.model.enums.SubjectType;
import dev.patra.interaction.domain.subject.model.read.SubjectView;
import dev.patra.interaction.domain.subject.model.vo.SubjectId;
import dev.patra.interaction.domain.subject.model.vo.SubjectSnapshot;
import dev.patra.interaction.domain.subject.port.read.SubjectReadPort;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/// 经验在底座登记表里的那一行：编辑、删除时要原样或换月份传回它的快照（`updateLocal` 不接受 null）。
@Component
@RequiredArgsConstructor
public class PostRegistry {

  private final SubjectReadPort subjects;

  /// 经验当前的登记快照。
  ///
  /// @param id 经验 ID
  /// @return 快照
  /// @throws IllegalStateException 经验没有登记（写入与登记在同一事务里，出现即程序错误）
  public SubjectSnapshot snapshotOf(PostId id) {
    SubjectId subjectId = subjectIdOf(id);
    SubjectView view = subjects.findByIds(List.of(subjectId)).get(subjectId);
    if (view == null) {
      throw new IllegalStateException("经验没有登记成 POST 对象：" + id);
    }
    return view.snapshot();
  }

  /// 经验自己在登记表里的对象 ID（类型 POST、`source_id` 是帖子 ID），不是经验所属期刊的对象。
  ///
  /// @param id 经验 ID
  /// @return 对象 ID
  /// @throws IllegalStateException 经验没有登记（写入与登记在同一事务里，出现即程序错误）
  public SubjectId subjectIdOf(PostId id) {
    String key = Long.toString(id.value());
    SubjectId subjectId = subjects.findIdsByKeys(SubjectType.POST, List.of(key)).get(key);
    if (subjectId == null) {
      throw new IllegalStateException("经验没有登记成 POST 对象：" + id);
    }
    return subjectId;
  }
}
