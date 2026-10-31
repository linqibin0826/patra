package dev.patra.notification.adapter.inbox.rest.response;

import dev.patra.notification.app.inbox.usecase.query.SubjectRefView;
import java.util.Locale;
import lombok.Builder;

/// 提醒行的对象上下文与点开去哪要的 key（文献 `/papers/{key}`，经验 `/posts/{key}`）。
///
/// @param type `publication` / `post`
/// @param key 门户链接里的 ID，可空
/// @param available 对象是否还在
/// @param title 文献标题
/// @param journal 经验所在期刊的全称
/// @param abbreviation 经验所在期刊的缩写
/// @param submittedMonth 经验的投稿月份 `yyyy-MM`
/// @param mine 经验是不是收件人自己的
/// @param experience 经验的结果；文献为空
@Builder
public record SubjectRefResponse(
    String type,
    String key,
    boolean available,
    String title,
    String journal,
    String abbreviation,
    String submittedMonth,
    boolean mine,
    ExperienceResponse experience) {

  /// 转换。
  ///
  /// @param view 视图，可空
  /// @return 响应，可空
  public static SubjectRefResponse from(SubjectRefView view) {
    if (view == null) {
      return null;
    }
    return SubjectRefResponse.builder()
        .type(view.type().name().toLowerCase(Locale.ROOT))
        .key(view.key())
        .available(view.available())
        .title(view.title())
        .journal(view.journal())
        .abbreviation(view.abbreviation())
        .submittedMonth(view.submittedMonth())
        .mine(view.mine())
        .experience(ExperienceResponse.from(view.experience()))
        .build();
  }
}
