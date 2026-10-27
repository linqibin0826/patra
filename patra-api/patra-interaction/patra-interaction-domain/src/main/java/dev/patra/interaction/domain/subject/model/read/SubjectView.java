package dev.patra.interaction.domain.subject.model.read;

import dev.patra.interaction.domain.subject.model.enums.SubjectState;
import dev.patra.interaction.domain.subject.model.enums.SubjectType;
import dev.patra.interaction.domain.subject.model.vo.SubjectId;
import dev.patra.interaction.domain.subject.model.vo.SubjectOwner;
import dev.patra.interaction.domain.subject.model.vo.SubjectSnapshot;
import lombok.Builder;

/// 给其他子域的登记对象视图。
///
/// @param subjectId 本地 ID
/// @param type 对象种类
/// @param key 对外 key（来源内部 ID 的十进制；本地对象是所属子域里的 ID）；来源里整行消失时为 null，只能用快照展示
/// @param state 状态
/// @param snapshot 展示快照，也是按出版日期 / 期刊排序的数据来源（外部对象每日对账刷新）
/// @param owner 所有者与「是否匿名」：本地对象才有，外部对象为 null
@Builder
public record SubjectView(
    SubjectId subjectId,
    SubjectType type,
    String key,
    SubjectState state,
    SubjectSnapshot snapshot,
    SubjectOwner owner) {}
