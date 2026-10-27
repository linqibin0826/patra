package dev.patra.interaction.app.notification.usecase.query;

import dev.patra.interaction.domain.subject.model.enums.SubjectType;
import lombok.Builder;

/// 提醒行的对象上下文（第三行）与永久链接要的 key。
///
/// @param type 对象类型（文献 / 投稿经验）
/// @param key 门户 URL 里的 ID，可空（外部来源行已消失）
/// @param available 对象是否还在（经验已删除、文献已下线时为 `false`）
/// @param title 文献标题；经验为空
/// @param journal 经验所在期刊的全称；文献为空
/// @param abbreviation 经验所在期刊的缩写，可空
/// @param submittedMonth 经验的投稿月份 `yyyy-MM`；文献为空
/// @param mine 经验是不是收件人自己的
@Builder
public record SubjectRefView(
    SubjectType type,
    String key,
    boolean available,
    String title,
    String journal,
    String abbreviation,
    String submittedMonth,
    boolean mine) {}
