package dev.patra.interaction.app.collection.usecase.query;

import dev.patra.interaction.domain.subject.model.enums.SubjectState;
import dev.patra.interaction.domain.subject.model.enums.SubjectType;
import dev.patra.interaction.domain.subject.model.vo.SubjectId;
import java.time.Instant;
import lombok.Builder;

/// 公开收藏夹里的一条：不带所有者的备注与其他归属。
///
/// @param itemId 条目（= 对象 ID）
/// @param type 对象种类
/// @param key 对外 key，可空
/// @param state 正常 / 已失效
/// @param title 已失效时的快照标题
/// @param addedAt 加入该夹的时间
@Builder
public record PublicItemView(
    SubjectId itemId,
    SubjectType type,
    String key,
    SubjectState state,
    String title,
    Instant addedAt) {}
