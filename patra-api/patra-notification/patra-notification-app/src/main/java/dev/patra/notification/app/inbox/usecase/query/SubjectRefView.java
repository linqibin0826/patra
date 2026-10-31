package dev.patra.notification.app.inbox.usecase.query;

import dev.patra.notification.domain.kernel.model.enums.SubjectType;
import dev.patra.notification.domain.kernel.model.read.ExperienceOutline;
import lombok.Builder;

/// 提醒行的对象上下文（「在哪」）与点开去哪要的 key。
///
/// @param type 对象类型
/// @param key 门户链接里的 ID，可空（来源里整行消失时）
/// @param available 对象是否还在
/// @param title 文献标题；经验为空
/// @param journal 经验所在期刊的全称；文献为空
/// @param abbreviation 经验所在期刊的缩写；文献为空
/// @param submittedMonth 经验的投稿月份 `yyyy-MM`；文献为空
/// @param mine 经验是不是收件人自己的
/// @param experience 经验的结果；文献为空
@Builder
public record SubjectRefView(
    SubjectType type,
    String key,
    boolean available,
    String title,
    String journal,
    String abbreviation,
    String submittedMonth,
    boolean mine,
    ExperienceOutline experience) {}
