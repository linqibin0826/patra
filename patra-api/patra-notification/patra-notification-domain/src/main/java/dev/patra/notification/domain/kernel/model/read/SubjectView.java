package dev.patra.notification.domain.kernel.model.read;

import dev.patra.notification.domain.kernel.model.enums.SubjectType;
import java.util.Objects;
import lombok.Builder;

/// interaction 登记对象的展示信息，读的那一刻现查。
///
/// @param subjectId 登记表 ID
/// @param type 类型
/// @param key 门户链接里的 ID（文献 `/papers/{key}`，经验 `/posts/{key}`，即帖子 ID）；来源里整行消失时为空
/// @param available 对象是否还在（文献下线时为假）
/// @param title 文献标题；经验为空
/// @param journal 文献的期刊全称、经验所在期刊的全称
/// @param abbreviation 经验所在期刊的缩写，可空
/// @param publishedDate 文献的出版日期、经验的投稿月份 `yyyy-MM`，可空
/// @param ownerId 经验的作者；文献为空
/// @param ownerAnonymous 经验是否匿名发布
/// @param experience 经验的结果；文献为空
@Builder
public record SubjectView(
    long subjectId,
    SubjectType type,
    String key,
    boolean available,
    String title,
    String journal,
    String abbreviation,
    String publishedDate,
    Long ownerId,
    boolean ownerAnonymous,
    ExperienceOutline experience) {

  /// 校验非空。
  public SubjectView {
    Objects.requireNonNull(type, "type 不能为 null");
  }
}
